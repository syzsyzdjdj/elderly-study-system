<template>
  <div class="admin-layout">
    <van-nav-bar fixed placeholder>
      <template #left>
        <button type="button" class="admin-menu-toggle" aria-label="打开管理菜单" @click="showMenu = true"><van-icon name="wap-nav" /></button>
      </template>
      <template #title>
        <span class="admin-nav-title">🎓 系统管理后台</span>
      </template>
      <template #right>
        <span class="admin-nav-user">{{ store.nickname || '管理员' }}</span>
        <span class="logout-btn" @click="logout" title="退出登录">
          <van-icon name="cross" size="16" />
          <span style="font-size:13px;margin-left:2px;">退出</span>
        </span>
      </template>
    </van-nav-bar>

    <van-popup v-model:show="showMenu" position="left" class="admin-mobile-menu" teleport="body">
      <div class="admin-menu-heading">管理工作台<van-button icon="cross" size="small" aria-label="关闭菜单" @click="showMenu = false" /></div>
      <nav aria-label="管理功能">
        <button v-for="item in ALL_MENUS" :key="item.path" type="button" :class="{ selected: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined" @click="router.push(item.path); showMenu = false">{{ item.title }}</button>
      </nav>
    </van-popup>

    <div class="admin-body">
      <!-- 侧边菜单 -->
      <van-sidebar v-model="active" class="admin-sidebar" @change="onSidebarChange">
        <van-sidebar-item
          v-for="item in ALL_MENUS"
          :key="item.path"
          :title="item.title"
        />
      </van-sidebar>

      <!-- 内容区 -->
      <div class="admin-content">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/user'
import { logoutApi } from '../../api/auth'
import { showConfirmDialog } from 'vant'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const ALL_MENUS = computed(() => store.managementMenus || [])

const active = ref(0)
const showMenu = ref(false)

// 路由与侧边栏联动
watch(
  () => route.path,
  (p) => {
    const i = ALL_MENUS.value.findIndex((m) => m.path === p)
    if (i >= 0) active.value = i
  },
  { immediate: true }
)

const onSidebarChange = (i) => {
  router.push(ALL_MENUS.value[i].path)
}

// 退出登录
const logout = async () => {
  try {
    await showConfirmDialog({ message: '确定退出登录吗？' })
  } catch {
    return // 取消
  }
  try {
    await logoutApi()
  } catch {
    // 后端退出失败不影响前端退出
  }
  store.clearUser()
  router.replace('/login')
}
</script>

<style scoped>
.logout-btn {
  display: inline-flex;
  align-items: center;
  margin-left: 10px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  color: #666;
  transition: all 0.2s;
}
.logout-btn:hover {
  color: #ee0a24;
  background: #fef0f0;
}
</style>
