<!--
  文件头：应用根组件（壳层），仅提供路由出口，不持有业务状态。
  上游：main.ts 通过 createApp(App).use(router).use(pinia) 挂载。
  下游：router/index.ts 的 login / chat / profile / admin 系列视图；
        导航栏、页面布局与登录态由各视图自行组织，避免根组件与业务耦合。
-->
<template>
  <!-- 路由出口：当前匹配视图的挂载点 -->
  <router-view />
</template>
