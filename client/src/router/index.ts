/**
 * 前端路由层：集中定义路由表，并通过全局前置守卫做页面级访问控制。
 * 所属层：client/src/router。
 * 上下游：上游由 main.ts 经 app.use(router) 挂载、各 .vue 视图作为懒加载目标；
 *   下游依赖 vue-router；守卫读取的 token/role 由 stores/auth 写入 sessionStorage，
 *   与 api/request.ts 的 401 登出逻辑共享同一份登录态。
 */
import { createRouter, createWebHistory } from 'vue-router'

/**
 * 创建路由实例：HTML5 history 模式，视图均按需懒加载。
 * 路由表契约：/login 登录；/ 聊天；/profile 个人；/admin 及 /admin/knowledge、
 *   /admin/config、/admin/users、/admin/notice 为管理页。
 * @returns 路由实例，供 main.ts 挂载与本模块默认导出。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
    { path: '/', name: 'chat', component: () => import('../views/Chat.vue') },
    { path: '/profile', name: 'profile', component: () => import('../views/Profile.vue') },
    { path: '/admin', name: 'admin', component: () => import('../views/admin/Home.vue') },
    { path: '/admin/knowledge', name: 'knowledge', component: () => import('../views/admin/Knowledge.vue') },
    { path: '/admin/config', name: 'config', component: () => import('../views/admin/Config.vue') },
    { path: '/admin/users', name: 'users', component: () => import('../views/admin/Users.vue') },
    { path: '/admin/notice', name: 'notice', component: () => import('../views/admin/Notice.vue') },
  ],
})

/**
 * 全局前置守卫：按登录态与角色做前端门禁，决定放行或重定向。
 * 注意：仅为体验层拦截，不是安全边界——真实鉴权必须由后端接口把关。
 * @param to 目标路由，据此判断是否需要 token / admin 角色。
 * @returns true 放行；返回路径字符串即重定向（'/login' 或 '/'）。
 * 副作用：只读 sessionStorage，不改写登录态（清理由 request.ts 与 auth store 负责）。
 */
router.beforeEach((to) => {
// token/role 取自 sessionStorage，与 auth store、request.ts 同源，刷新页面后仍有效
  const token = sessionStorage.getItem('token')
  const role = sessionStorage.getItem('role')
// 登录页必须无条件放行，否则未登录访问登录页会被再次重定向形成死循环
  if (to.path === '/login') return true
// 无 token 一律回登录页
  if (!token) return '/login'
// /admin* 需 admin 角色；此处只是前端门禁，越权接口仍由后端拒绝
  if (to.path.startsWith('/admin')) {
// 非管理员访问管理页回首页，避免暴露管理入口
    if (role !== 'admin') return '/'
  }
  return true
})

/** 默认导出路由实例，供 main.ts 通过 app.use 挂载。 */
export default router
