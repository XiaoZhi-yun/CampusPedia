import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

/** 拦截 axios.create 注册的两个拦截器，直接对拦截器函数做单元验证。 */
const h = vi.hoisted(() => ({
  onRequest: undefined as undefined | ((config: any) => any),
  onFulfilled: undefined as undefined | ((resp: any) => any),
  onRejected: undefined as undefined | ((err: any) => any),
  error: vi.fn(),
}))

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

vi.mock('element-plus', () => ({ ElMessage: { error: h.error } }))

import './request'

const fulfilled = (data: unknown) => h.onFulfilled!({ data })
const rejected = (err: unknown) => h.onRejected!(err)

describe('request 拦截器', () => {
  let consoleError: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    sessionStorage.clear()
    h.error.mockClear()
    // jsdom 对 window.location.href 赋值会输出 Not implemented 警告，静默掉
    consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleError.mockRestore()
    sessionStorage.clear()
  })

  it('已登录时注入 Bearer token', () => {
    sessionStorage.setItem('token', 'abc')
    const config: any = { headers: {} }
    expect(h.onRequest!(config).headers.Authorization).toBe('Bearer abc')
  })

  it('未登录时不注入 Authorization', () => {
    const config: any = { headers: {} }
    expect(h.onRequest!(config).headers.Authorization).toBeUndefined()
  })

  it('HTTP 200 但业务 code≠200 时拒绝并提示', async () => {
    await expect(fulfilled({ code: 500, msg: '出错啦' })).rejects.toThrow('出错啦')
    expect(h.error).toHaveBeenCalledWith('出错啦')
  })

  it('业务 code=200 时原样返回响应体', () => {
    const body = { code: 200, msg: '成功', data: { id: 1 } }
    expect(fulfilled(body)).toEqual(body)
    expect(h.error).not.toHaveBeenCalled()
  })

  it('已登录请求遇到 401 时清理登录态', async () => {
    sessionStorage.setItem('token', 'abc')
    const err = { response: { status: 401, data: { msg: '登录已过期' } }, config: { url: '/sessions' }, message: 'x' }

    await expect(rejected(err)).rejects.toBe(err)
    expect(sessionStorage.getItem('token')).toBeNull()
    expect(h.error).toHaveBeenCalledWith('登录已过期')
  })

  it('登录/注册接口报 401 不触发登出', async () => {
    sessionStorage.setItem('token', 'old')
    const err = { response: { status: 401, data: { msg: '账号或密码错误' } }, config: { url: '/auth/login' }, message: 'x' }

    await expect(rejected(err)).rejects.toBe(err)
    expect(sessionStorage.getItem('token')).toBe('old')
    expect(h.error).toHaveBeenCalledWith('账号或密码错误')
  })

  it('普通错误提示后端返回的 msg', async () => {
    const err = { response: { status: 403, data: { msg: '无权限' } }, config: { url: '/admin/users' }, message: 'x' }

    await expect(rejected(err)).rejects.toBe(err)
    expect(h.error).toHaveBeenCalledWith('无权限')
  })
})
