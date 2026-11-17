/**
 * 前端网络层：基于 axios 的全局单例请求实例，统一收口鉴权注入与错误处理。
 * 所属层：client/src/api（接口/网络层）。
 * 上下游：上游为各页面与组件（通过本模块默认导出发请求）；下游依赖 axios 与
 *   element-plus 的 ElMessage；登录态 token 存于 sessionStorage，与 router 全局守卫、
 *   stores/auth 共享同一份数据。统一错误提示由拦截器承担，页面不再重复 ElMessage.error。
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 创建全局唯一的 axios 实例，作为本模块默认导出供所有页面复用。
 * 预置的 baseURL 与超时见下方行内注释；拦截器在紧随其后的语句中注册。
 * @returns axios 实例。
 */
// baseURL 固定 '/api' 与后端网关/代理前缀对齐；30s 超时兼顾 AI/检索类慢请求与悬挂兜底
const request = axios.create({ baseURL: '/api', timeout: 30000 })

/**
 * 请求拦截器：统一注入登录凭证，调用方无需逐请求设置 Authorization。
 * @param config axios 请求配置；命中登录态时原地写入 config.headers.Authorization（副作用）。
 * @returns 处理后的 config——axios 要求必须返回，否则请求不会发出。
 */
// token 取自 sessionStorage（与 router 守卫、auth store 同源，刷新后仍有效），后端约定 Bearer 前缀
request.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

/**
 * 响应拦截器：把「HTTP 2xx 但业务失败」与「HTTP 非 2xx」两类错误统一收口。
 * @param resp 成功回调收到的响应；约定 code!==200 即业务失败，统一 ElMessage.error
 *   并 Promise.reject，页面因此无需再判 code 或重复弹错；code=200 则原样返回响应体 data。
 * @param err 失败回调收到的错误；统一取后端 msg 提示并 reject(err)，保持错误传播契约。
 * @returns 成功回调返回 data；失败回调 reject(err)。
 * 副作用：仅当「已登录且请求 URL 非 /auth/ 前缀」的 401 会清空 sessionStorage 并跳 /login；
 *   排除 /auth/ 是因为登录/注册接口本身用 401 表达账号或密码错误，不应触发登出。
 */
request.interceptors.response.use(
  (resp) => {
    const data = resp.data
    // 少数旧端点仍以 HTTP 200 + code≠200 返回业务错误，统一转成拒绝并提示
    if (data && typeof data === 'object' && 'code' in data && (data as any).code !== 200) {
      const msg = (data as any).msg || '请求失败'
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return data
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.msg || err.message || '网络异常，请稍后重试'
    const url = err.config?.url || ''
    // 登录/注册失败不触发登出；只有已登录请求遇到 401 才清理登录态
    if (status === 401 && !url.startsWith('/auth/') && sessionStorage.getItem('token')) {
      sessionStorage.clear()
      window.location.href = '/login'
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

/**
 * 默认导出：页面统一通过该实例发起后端请求。
 * 契约：code!==200、401 登出、网络异常等错误提示已由拦截器集中处理，
 *   调用方只需处理成功数据或 catch 业务异常，不要重复 ElMessage.error。
 */
export default request
