import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({ baseURL: '/api', timeout: 30000 })

request.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

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

export default request
