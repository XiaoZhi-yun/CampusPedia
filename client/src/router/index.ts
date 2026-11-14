import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/Login.vue') },
    { path: '/profile', name: 'profile', component: () => import('../views/Profile.vue') },
    { path: '/', name: 'chat', component: () => import('../views/Chat.vue') },
    { path: '/admin', name: 'admin', component: () => import('../views/admin/Home.vue') },
    { path: '/admin/knowledge', name: 'knowledge', component: () => import('../views/admin/Knowledge.vue') },
    { path: '/admin/config', name: 'config', component: () => import('../views/admin/Config.vue') },
    { path: '/admin/users', name: 'users', component: () => import('../views/admin/Users.vue') },
    { path: '/admin/feedback', name: 'feedback', component: () => import('../views/admin/Feedback.vue') },
    { path: '/admin/faq', name: 'faq', component: () => import('../views/admin/Faq.vue') },
    { path: '/admin/notice', name: 'notice', component: () => import('../views/admin/Notice.vue') },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const role = localStorage.getItem('role')
  if (to.path.startsWith('/admin')) {
    if (!token) return '/login'
    if (role !== 'admin') return '/'
  }
  return true
})

export default router
