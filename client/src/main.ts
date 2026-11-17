/**
 * 应用入口：组装 Vue 实例、全局插件与组件，并挂载到 #app。
 * 所属层：client 启动层。
 * 上下游：依赖 vue / pinia / vue-router / element-plus 及其暗色 css-vars；
 *   引入 router（路由与守卫）、style.css（设计令牌）与 App.vue、NavBar.vue；
 *   pinia 须先于依赖 store 的组件安装。
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// Element Plus 暗色变量表：配合 style.css 的 html.dark 重定义，组件无需另写暗色分支
import 'element-plus/theme-chalk/dark/css-vars.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import NavBar from './components/NavBar.vue'
import router from './router'
import './style.css'

/**
 * 创建根 Vue 应用实例，作为后续插件与全局组件的注册载体。
 * @returns 应用实例；副作用：无（mount 在最后一步才触发视图渲染）。
 */
const app = createApp(App)
// 图标组件逐个全局注册，使各 .vue 模板无需重复 import 即可按名称使用
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
// Pinia 需先安装，否则组件内 useAuthStore() 无法取得实例
app.use(createPinia())
// 挂载路由后全局前置守卫生效，首次导航即执行登录态门禁
app.use(router)
app.component('NavBar', NavBar)
app.use(ElementPlus)
app.mount('#app')
