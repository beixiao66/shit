<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()

/** 菜单按 JWT type 区分（type: 1 平台管理员 / 2 商家），骨架先列占位，路由按里程碑补 */
const menus = computed(() => {
  const admin = [
    { to: '/admin', label: '数据看板' },
    { to: '/admin/categories', label: '类目管理' },
    { to: '/admin/adverts', label: '广告管理' },
    { to: '/admin/merchants', label: '商家审核' },
    { to: '/admin/orders', label: '订单管理' },
    { to: '/admin/refunds', label: '退款处理' },
    { to: '/admin/reports', label: '统计报表' },
    { to: '/admin/employees', label: '员工管理' },
  ]
  const merchant = [
    { to: '/admin', label: '店铺概览' },
    { to: '/admin/shop', label: '店铺设置' },
    { to: '/admin/products', label: '商品管理' },
    { to: '/admin/orders', label: '店铺订单' },
    { to: '/admin/withdrawal', label: '提现记录' },
  ]
  return userStore.type === 2 ? merchant : admin
})

function logout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin">
    <aside class="sider">
      <div class="sider-brand">MX<span class="sider-brand-sub">管理后台</span></div>
      <nav class="sider-nav">
        <router-link v-for="m in menus" :key="m.to" :to="m.to" class="sider-link">
          {{ m.label }}
        </router-link>
      </nav>
    </aside>
    <main class="admin-main">
      <header class="topbar">
        <span class="md-num topbar-type">TYPE:{{ userStore.type === 2 ? 'MERCHANT' : 'ADMIN' }}</span>
        <el-dropdown @command="logout">
          <span class="topbar-user">{{ userStore.nickname || userStore.username }} ▾</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>
      <div class="content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<style scoped>
.admin {
  display: flex;
  min-height: 100vh;
  background: var(--md-color-bg-tint);
}
.sider {
  width: 216px;
  padding: 22px 14px;
  background: var(--md-color-primary);
  color: #fff;
}
.sider-brand {
  font-family: var(--md-font-display);
  font-weight: 700;
  font-size: 19px;
  padding: 0 10px 20px;
  letter-spacing: 0.04em;
}
.sider-brand-sub {
  font-size: 12px;
  font-weight: 400;
  color: #aab3c4;
  margin-left: 8px;
}
.sider-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.sider-link {
  padding: 10px 12px;
  border-radius: var(--md-radius-sm);
  font-size: 14px;
  color: #b8c2d4;
}
.sider-link:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.06);
}
.sider-link.router-link-exact-active {
  color: var(--md-color-primary);
  background: var(--md-color-accent);
  font-weight: 600;
}
.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.topbar {
  height: 56px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid var(--md-color-line);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.topbar-type {
  font-size: 12px;
  letter-spacing: 0.14em;
  color: var(--md-color-ink-sub);
}
.topbar-user {
  font-size: 14px;
  cursor: pointer;
  outline: none;
}
.content {
  padding: 24px;
  flex: 1;
}
</style>
