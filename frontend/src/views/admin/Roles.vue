<template>
  <div class="admin-page">
    <h3 class="page-title">🛡 角色权限</h3>

    <van-empty v-if="loaded && !roles.length" description="暂无角色" image-size="90" />

    <!-- 角色列表：点击弹出菜单分配 -->
    <van-cell
      v-for="r in roles"
      :key="r.id"
      :title="r.roleName"
      :label="r.roleKey === 'admin' ? '管理员拥有全部管理菜单' : r.roleKey === 'course_manager' ? '分配课程与分类菜单；接口权限由角色决定' : '学员使用学习端'"
      :is-link="r.roleKey === 'course_manager'"
      @click="openAssign(r)"
    />

    <!-- 菜单分配弹窗 -->
    <van-popup v-model:show="showAssign" position="bottom" round>
      <div class="popup-body">
        <h3 class="page-title">「{{ currentRole.roleName || currentRole.name }}」菜单权限</h3>
        <van-empty v-if="!menuTree.length" description="暂无菜单数据" image-size="80" />

        <van-checkbox-group v-model="checkedMenus">
          <div class="menu-item" v-for="m in menuTree" :key="m.id">
            <van-checkbox :name="m.id">{{ m.title || m.name || m.menuName }}</van-checkbox>
            <div v-if="m.children && m.children.length">
              <div class="menu-item sub" v-for="c in m.children" :key="c.id">
                <van-checkbox :name="c.id">{{ c.title || c.name || c.menuName }}</van-checkbox>
              </div>
            </div>
          </div>
        </van-checkbox-group>

        <div class="form-actions">
          <van-button block type="primary" size="large" @click="saveAssign">保存权限</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { assignMenu, getMenuList, getRoleList } from '../../api/role'
import { assignableMenus } from '../../utils/managementNavigation.mjs'

const roles = ref([])
const loaded = ref(false)

const showAssign = ref(false)
const currentRole = ref({})
const menuTree = ref([])
const checkedMenus = ref([])

const loadRoles = async () => {
  try {
    const res = await getRoleList()
    const data = res.data || {}
    roles.value = data.list || (Array.isArray(data) ? data : [])
  } catch (e) {
    // 拦截器已提示
  }
  loaded.value = true
}

// 菜单扁平列表 → 树
const buildMenuTree = (list) => {
  const rows = list.map((m) => ({ ...m, children: [] }))
  const tops = rows.filter((m) => !m.parentId)
  rows.forEach((m) => {
    if (m.parentId) {
      const p = rows.find((x) => x.id === m.parentId)
      if (p) p.children.push(m)
      else tops.push(m)
    }
  })
  menuTree.value = tops
}

const openAssign = async (role) => {
  if (role.roleKey !== 'course_manager') return
  currentRole.value = role
  checkedMenus.value = []
  menuTree.value = []
  showAssign.value = true
  // 加载菜单树
  try {
    const res = await getMenuList()
    const data = res.data || {}
    const list = data.list || (Array.isArray(data) ? data : [])
    const allowed = new Set(assignableMenus(role.roleKey).map(menu => menu.path))
    const flatten = rows => rows.flatMap(menu => [menu, ...flatten(menu.children || [])])
    buildMenuTree(flatten(list).filter(menu => allowed.has(menu.path)))
  } catch (e) {
    // 拦截器已提示
  }
  // 回显角色已选菜单
  const ids = role.menuIds || (role.menus || []).map((x) => (typeof x === 'object' ? x.id : x))
  if (Array.isArray(ids)) checkedMenus.value = ids
}

const saveAssign = async () => {
  try {
    await assignMenu({
      roleId: currentRole.value.id || currentRole.value.roleId,
      menuIds: checkedMenus.value
    })
    showToast('保存成功')
    showAssign.value = false
    loadRoles()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(() => loadRoles())
</script>
