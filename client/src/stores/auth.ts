/**
 * 登录态状态层：集中维护当前用户资料与凭证，并负责与 sessionStorage 的持久化同步。
 * 所属层：client/src/stores（Pinia store）。
 * 上下游：上游为各页面/组件经 useAuthStore() 读写；下游依赖 pinia；
 *   写入 sessionStorage 的 token/role 被 api/request.ts（请求头注入、401 清理）与
 *   router 守卫读取，构成全站共享的登录态来源。
 */
import { defineStore } from 'pinia'

/**
 * 定义并导出 auth store：state 从 sessionStorage 恢复，actions 负责登录写入与登出清理。
 * @returns Pinia store 定义，供组件通过 useAuthStore() 获取响应式登录态。
 * 契约：所有字段均以 sessionStorage 为准，刷新页面后由 state 初始化函数自动回填。
 */
export const useAuthStore = defineStore('auth', {
// 从 sessionStorage 逐项回填，保证刷新后登录态不丢；缺省值避免 undefined 进入模板
  state: () => ({
    token: sessionStorage.getItem('token') || '',
    studentNo: sessionStorage.getItem('studentNo') || '',
    username: sessionStorage.getItem('username') || '',
    major: sessionStorage.getItem('major') || '',
    className: sessionStorage.getItem('className') || '',
    avatar: sessionStorage.getItem('avatar') || 'p1',
    role: sessionStorage.getItem('role') || '',
  }),
  actions: {
/**
 * 登录/资料更新 action：把后端返回的用户资料同时写入响应式 state 与 sessionStorage。
 * @param data 含 token、studentNo、username、major、className、avatar、role 的登录数据；
 *   可选字段缺省时回退为空串或默认头像 'p1'。
 * @returns 无返回值。
 * 副作用：写入 7 项 sessionStorage 键值，使 router 守卫与 request 拦截器立即读到新登录态。
 */
    setLogin(data: any) {
      this.token = data.token
      this.studentNo = data.studentNo || ''
      this.username = data.username || ''
      this.major = data.major || ''
      this.className = data.className || ''
      this.avatar = data.avatar || 'p1'
      this.role = data.role
// state 只服务于本次会话的响应式渲染，持久化必须同步写入 sessionStorage 供跨层读取
      sessionStorage.setItem('token', data.token)
      sessionStorage.setItem('studentNo', data.studentNo || '')
      sessionStorage.setItem('username', data.username || '')
      sessionStorage.setItem('major', data.major || '')
      sessionStorage.setItem('className', data.className || '')
      sessionStorage.setItem('avatar', data.avatar || 'p1')
      sessionStorage.setItem('role', data.role)
    },
/**
 * 登出 action：清空内存中的用户资料并重置为默认头像，随后清除持久化登录态。
 * @returns 无返回值。
 * 副作用：sessionStorage.clear() 移除全部键；router 守卫据此跳回登录页，
 *   request 拦截器也不再注入 Authorization。
 */
    logout() {
      this.token = ''; this.studentNo = ''; this.username = ''; this.major = ''; this.className = ''
      this.avatar = 'p1'; this.role = ''
// clear 而非逐项 remove：登录态各键由本 store 管理，一次性清空可避免遗漏
      sessionStorage.clear()
    },
  },
})
