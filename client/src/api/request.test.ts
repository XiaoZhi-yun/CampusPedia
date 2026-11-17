/**
 * request.ts 拦截器的单元测试（vitest）。
 * 所属层：测试层，与被测模块同目录。
 * 上下游：被测模块为 './request'；因 vi.mock 工厂会被提升执行，拦截容器须经 vi.hoisted
 *   提前声明；axios 与 element-plus 被替换为可记录调用的替身，无需真实网络或 DOM。
 * 共 7 个用例：token 注入/未注入、code≠200 拒绝、code=200 透传、401 登出、
 *   /auth/ 401 不登出、普通错误取后端 msg。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

/** 拦截 axios.create 注册的两个拦截器，直接对拦截器函数做单元验证。 */
const h = vi.hoisted(() => ({
  onRequest: undefined as undefined | ((config: any) => any),
  onFulfilled: undefined as undefined | ((resp: any) => any),
  onRejected: undefined as undefined | ((err: any) => any),
  error: vi.fn(),
}))

// 把 create 返回实例的 interceptors.use 换成「捕获函数」，使拦截器逻辑可被用例直接调用
vi.mock('axios', () => ({
  default: {
    create: () => ({
      interceptors: {
        request: { use: (fn: any) => { h.onRequest = fn } },
        response: {
          use: (ok: any, fail: any) => {
            h.onFulfilled = ok
            h.onRejected = fail
          },
        },
      },
    }),
  },
}))

// 用可记录调用的 spy 替换 ElMessage.error：既避免真实 DOM 弹层，又能断言提示文案
vi.mock('element-plus', () => ({ ElMessage: { error: h.error } }))

// 导入即执行被测模块顶层代码，两个拦截器函数由此落到 hoisted 的 h 上供后续断言
import './request'

/**
 * 调用被捕获的响应成功回调，模拟一次 { data } 响应。
 * @param data 构造的响应体。
 * @returns 拦截器返回值（原样 data，或失败时 Promise.reject）。
 */
const fulfilled = (data: unknown) => h.onFulfilled!({ data })
/**
 * 调用被捕获的响应失败回调，模拟一次请求异常。
 * @param err 构造的错误对象。
 * @returns 拦截器返回值（Promise.reject(err)）。
 */
const rejected = (err: unknown) => h.onRejected!(err)

/** 聚合请求拦截器与响应拦截器的行为用例，共享登录态与 spy 的清理钩子。 */
describe('request 拦截器', () => {
  let consoleError: ReturnType<typeof vi.spyOn>

/** 每个用例前清空登录态、重置错误 spy，并静默控制台，保证用例相互隔离。 */
  beforeEach(() => {
    sessionStorage.clear()
    h.error.mockClear()
    // jsdom 对 window.location.href 赋值会输出 Not implemented 警告，静默掉
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

/** 每个用例后还原 console.error 并清空登录态，避免污染后续用例。 */
  afterEach(() => {
    consoleError.mockRestore()
    sessionStorage.clear()
  })

/** 测什么：请求拦截器在 sessionStorage 存在 token 时注入 Authorization: Bearer abc。 */
  it('已登录时注入 Bearer token', () => {
    sessionStorage.setItem('token', 'abc')
    const config: any = { headers: {} }
    expect(h.onRequest!(config).headers.Authorization).toBe('Bearer abc')
  })

/** 测什么：无 token 时请求拦截器不写 Authorization 头，保持 config 原样。 */
  it('未登录时不注入 Authorization', () => {
    const config: any = { headers: {} }
    expect(h.onRequest!(config).headers.Authorization).toBeUndefined()
  })

/** 测什么：响应体 code≠200 时转为 reject，并以 msg 触发统一错误提示。 */
  it('HTTP 200 但业务 code≠200 时拒绝并提示', async () => {
    await expect(fulfilled({ code: 500, msg: '出错啦' })).rejects.toThrow('出错啦')
    expect(h.error).toHaveBeenCalledWith('出错啦')
  })

/** 测什么：code=200 时原样透传响应体，且不触发错误提示。 */
  it('业务 code=200 时原样返回响应体', () => {
    const body = { code: 200, msg: '成功', data: { id: 1 } }
    expect(fulfilled(body)).toEqual(body)
    expect(h.error).not.toHaveBeenCalled()
  })

/** 测什么：已登录的普通请求收到 401 时清空 sessionStorage 并提示过期文案。 */
  it('已登录请求遇到 401 时清理登录态', async () => {
    sessionStorage.setItem('token', 'abc')
// /sessions 属普通已登录接口，配合上方 token 命中「401 登出」分支
    const err = { response: { status: 401, data: { msg: '登录已过期' } }, config: { url: '/sessions' }, message: 'x' }

    await expect(rejected(err)).rejects.toBe(err)
    expect(sessionStorage.getItem('token')).toBeNull()
    expect(h.error).toHaveBeenCalledWith('登录已过期')
  })

/** 测什么：/auth/ 前缀的 401 属账号密码错误，保留原有登录态、不触发登出。 */
  it('登录/注册接口报 401 不触发登出', async () => {
    sessionStorage.setItem('token', 'old')
// url 取 /auth/login 前缀，用于验证「登录 401 不登出」的排除逻辑
    const err = { response: { status: 401, data: { msg: '账号或密码错误' } }, config: { url: '/auth/login' }, message: 'x' }

    await expect(rejected(err)).rejects.toBe(err)
    expect(sessionStorage.getItem('token')).toBe('old')
    expect(h.error).toHaveBeenCalledWith('账号或密码错误')
  })

/** 测什么：非 401 的普通错误仍按后端 data.msg 统一提示，并原样 reject。 */
  it('普通错误提示后端返回的 msg', async () => {
// 403 非 401，用于验证普通错误只取 msg、不触碰登录态
    const err = { response: { status: 403, data: { msg: '无权限' } }, config: { url: '/admin/users' }, message: 'x' }

    await expect(rejected(err)).rejects.toBe(err)
    expect(h.error).toHaveBeenCalledWith('无权限')
  })
})
