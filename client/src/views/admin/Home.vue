<template>
  <div class="admin-page">
    <header class="topbar">
      <div class="brand">管理后台</div>
      <div><el-button text @click="$router.push('/')">返回问答</el-button></div>
    </header>
    <div class="content">
      <el-card>
        <h3>管理入口</h3>
        <el-space>
          <el-button type="primary" @click="$router.push('/admin/knowledge')">语料管理</el-button>
          <el-button @click="$router.push('/admin/config')">参数配置</el-button>
        </el-space>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100vh; background: #f5f6f7; }
.topbar { display: flex; justify-content: space-between; padding: 12px 20px; background: #fff; border-bottom: 1px solid #eee; }
.brand { font-weight: 600; }
.content { padding: 20px; }
</style>
