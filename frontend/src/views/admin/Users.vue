<template>
  <div class="admin-page">
    <h3 class="page-title">👥 用户管理</h3>

    <van-search v-model="keyword" placeholder="搜索账号或昵称" @search="refresh" @clear="refresh" />

    <div class="add-btn">
      <van-button type="primary" block size="large" icon="plus" @click="openAdd">新增用户</van-button>
    </div>

    <!-- 用户卡片列表（分页） -->
    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多用户了" @load="onLoad">
      <div class="user-card" v-for="u in list" :key="u.id">
        <div class="user-line">
          <span class="u-name">{{ u.nickname || u.username }}</span>
          <van-tag :type="u.status === 1 ? 'success' : 'warning'">{{ u.status === 1 ? '正常' : '停用' }}</van-tag>
        </div>
        <div class="u-line">账号：{{ u.username }}　电话：{{ u.phone || '-' }}</div>
        <div class="u-line">
          角色：
          <van-tag :type="roleTagType(u)" size="medium">{{ u.roleName || '未分配' }}</van-tag>
          　积分：{{ u.points ?? 0 }}
        </div>
        <div class="user-actions">
          <van-button size="small" type="success" plain icon="records" @click="openRecords(u)">学习记录</van-button>
          <van-button size="small" type="primary" plain @click="openEdit(u)">编辑</van-button>
          <van-button size="small" type="warning" plain @click="resetPwd(u)">重置密码</van-button>
          <van-button size="small" type="danger" plain @click="removeUser(u)">删除</van-button>
          <van-switch :model-value="u.status === 1" size="22" @change="toggleStatus(u)" />
        </div>
      </div>
    </van-list>

    <!-- 新增/编辑弹窗 -->
    <van-popup v-model:show="showPopup" position="bottom" round @closed="form.password = ''">
      <div class="popup-body">
        <h3 class="page-title">{{ isEdit ? '编辑用户' : '新增用户' }}</h3>
        <van-form @submit="saveUser">
          <van-field
            v-model="form.username"
            label="账号"
            :readonly="isEdit"
            placeholder="请输入账号"
            :rules="[{ required: true, message: '请输入账号' }]"
          />
          <van-field
            v-model="form.password"
            type="password"
            label="密码"
            :placeholder="isEdit ? '不修改请留空' : '请设置至少12字符的密码'"
          />
          <van-field v-model="form.nickname" label="昵称" placeholder="请输入昵称" />
          <van-field v-model="form.phone" label="手机号" type="tel" placeholder="请输入手机号" />
          <van-field label="角色">
            <template #input>
              <van-picker
                :model-value="[form.roleId]"
                @update:model-value="onRoleChange"
                :columns="roleColumns"
                class="role-picker"
              />
            </template>
          </van-field>
          <div class="form-actions">
            <van-button block type="primary" native-type="submit" size="large">保存</van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <van-popup v-model:show="showReset" position="bottom" round :close-on-click-overlay="!resetting" @closed="clearReset">
      <div class="popup-body">
        <h3 class="page-title">重置「{{ resetTarget?.username }}」的密码</h3>
        <p>请单独告知本人新密码，并提醒其登录后修改。</p>
        <van-form @submit="submitReset">
          <van-field v-model="resetPassword" type="password" label="新密码" autocomplete="new-password" :disabled="resetting" placeholder="至少12字符" />
          <van-field v-model="resetConfirm" type="password" label="确认密码" autocomplete="new-password" :disabled="resetting" placeholder="再次输入新密码" />
          <div class="form-actions">
            <van-button block type="primary" native-type="submit" :loading="resetting">确认重置</van-button>
            <van-button block native-type="button" :disabled="resetting" @click="showReset = false">取消</van-button>
          </div>
        </van-form>
      </div>
    </van-popup>

    <!-- 学习记录弹窗：该用户学过哪些课程 + 学习明细 -->
    <van-popup v-model:show="showRecords" position="bottom" round :style="{ maxHeight: '80%' }">
      <div class="popup-body">
        <h3 class="page-title">📖 {{ recordsUser ? (recordsUser.nickname || recordsUser.username) : '' }} 的学习记录</h3>
        <van-tabs v-model:active="recordsTab">
          <!-- 学过的课程（汇总） -->
          <van-tab title="学过的课程">
            <div v-if="recordsLoading" class="tab-loading"><van-loading size="24" />加载中...</div>
            <van-empty v-else-if="!records.length" description="该用户还没有学习记录" image-size="80" />
            <van-cell v-for="r in records" :key="r.id || r.courseId" :title="r.courseTitle || `课程 ${r.courseId}`">
              <template #label>
                累计 {{ formatMinutes(r.studyTime) }} · 进度 {{ r.progress || 0 }}% ·
                最近 {{ formatDate(r.lastStudyTime) }}
              </template>
              <template #value>
                <van-tag :type="r.isFinish === 1 ? 'success' : 'warning'">
                  {{ r.isFinish === 1 ? '已完成' : '学习中' }}
                </van-tag>
              </template>
            </van-cell>
          </van-tab>

          <!-- 学习行为明细（每次会话） -->
          <van-tab :title="`学习明细${logs.length ? '(' + logs.length + ')' : ''}`">
            <div v-if="recordsLoading" class="tab-loading"><van-loading size="24" />加载中...</div>
            <van-empty v-else-if="!logs.length" description="暂无学习明细" image-size="80" />
            <van-cell v-for="l in logs" :key="l.id" :title="l.courseTitle || `课程 ${l.courseId}`">
              <template #label>{{ l.studyDate }} · 学习 {{ formatMinutes(l.studyTime) }} · 进度 {{ l.progress || 0 }}%</template>
              <template #value>
                <van-tag plain :type="l.isFinish === 1 ? 'success' : 'default'">
                  {{ l.isFinish === 1 ? '完成' : '学习' }}
                </van-tag>
              </template>
            </van-cell>
          </van-tab>
        </van-tabs>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { addUser, deleteUser, getUserList, resetUserPwd, updateUser } from '../../api/user'
import { getRoleList } from '../../api/role'
import { getUserStudyRecords, getUserStudyLogs } from '../../api/study'
import { managedPasswordError } from '../../utils/managedPassword.mjs'

const list = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
const keyword = ref('')

const showPopup = ref(false)
const isEdit = ref(false)
const form = reactive({ id: null, username: '', password: '', nickname: '', phone: '', roleId: null })

const roleColumns = ref([])

// ---------- 学习记录（管理员查看该用户学过哪些课程） ----------
const showRecords = ref(false)
const recordsTab = ref(0)
const recordsLoading = ref(false)
const recordsUser = ref(null)
const records = ref([])
const logs = ref([])

const openRecords = async (u) => {
  recordsUser.value = u
  recordsTab.value = 0
  records.value = []
  logs.value = []
  showRecords.value = true
  recordsLoading.value = true
  try {
    const [recRes, logRes] = await Promise.all([
      getUserStudyRecords(u.id),
      getUserStudyLogs(u.id)
    ])
    records.value = recRes.data || []
    logs.value = logRes.data || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    recordsLoading.value = false
  }
}

// 分钟 → "X小时Y分钟"
const formatMinutes = (min) => {
  const m = Number(min || 0)
  if (m < 60) return `${m}分钟`
  const h = Math.floor(m / 60)
  return m % 60 ? `${h}小时${m % 60}分钟` : `${h}小时`
}

// 时间 → yyyy-MM-dd HH:mm
const formatDate = (t) => {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

// Vant4 picker 的 modelValue 是数组，需与角色ID做转换
const onRoleChange = (v) => {
  form.roleId = Array.isArray(v) ? v[0] : v
}

const onLoad = async () => {
  try {
    const res = await getUserList({ page: page.value, pageSize, keyword: keyword.value || undefined })
    const data = res.data || {}
    const rows = data.list || data.records || (Array.isArray(data) ? data : [])
    const total = Number(data.total ?? rows.length)
    if (page.value === 1) list.value = rows
    else list.value = list.value.concat(rows)
    page.value += 1
    loading.value = false
    if (list.value.length >= total) finished.value = true
  } catch (e) {
    loading.value = false
    finished.value = true
  }
}

const refresh = () => {
  page.value = 1
  list.value = []
  finished.value = false
  loading.value = true
  onLoad()
}

// 角色标签颜色：admin 红色、user 绿色
const roleTagType = (u) => {
  if (u.roleKey === 'admin') return 'danger'
  return 'success'
}

// ---------- 新增 / 编辑 ----------
const openAdd = () => {
  isEdit.value = false
  Object.assign(form, { id: null, username: '', password: '', nickname: '', phone: '', roleId: roleColumns.value[0]?.value ?? null })
  showPopup.value = true
}

const openEdit = (u) => {
  isEdit.value = true
  Object.assign(form, {
    id: u.id,
    username: u.username,
    password: '',
    nickname: u.nickname || '',
    phone: u.phone || '',
    roleId: u.roleId ?? roleColumns.value[0]?.value ?? null
  })
  showPopup.value = true
}

const saveUser = async () => {
  if (!isEdit.value || form.password) {
    const error = managedPasswordError(form.password)
    if (error) { showToast(error); return }
  }
  if (!form.username) {
    showToast('请输入账号')
    return
  }
  try {
    if (isEdit.value) {
      const payload = { id: form.id, username: form.username }
      if (form.nickname) payload.nickname = form.nickname
      if (form.phone) payload.phone = form.phone
      if (form.password) payload.password = form.password
      if (form.roleId) payload.roleId = form.roleId
      await updateUser(payload)
    } else {
      await addUser({
        username: form.username,
        password: form.password,
        nickname: form.nickname || form.username,
        phone: form.phone,
        roleId: form.roleId
      })
    }
    showToast('保存成功')
    showPopup.value = false
    refresh()
  } catch (e) {
    // 拦截器已提示
  }
}

// ---------- 启用/停用 ----------
const toggleStatus = async (u) => {
  const next = u.status === 1 ? 0 : 1
  try {
    await updateUser({ id: u.id, status: next })
    u.status = next
    showToast(next === 1 ? '已启用' : '已停用')
  } catch (e) {
    // 拦截器已提示，状态未改变
  }
}

// ---------- 重置密码 ----------
const showReset = ref(false)
const resetting = ref(false)
const resetTarget = ref(null)
const resetPassword = ref('')
const resetConfirm = ref('')
const clearReset = () => {
  resetTarget.value = null
  resetPassword.value = ''
  resetConfirm.value = ''
}
const resetPwd = (user) => {
  clearReset()
  resetTarget.value = user
  showReset.value = true
}
const submitReset = async () => {
  if (resetting.value || !resetTarget.value) return
  const error = managedPasswordError(resetPassword.value)
  if (error) { showToast(error); return }
  if (resetPassword.value !== resetConfirm.value) { showToast('两次输入的密码不一致'); return }
  resetting.value = true
  try {
    await resetUserPwd({ id: resetTarget.value.id, newPassword: resetPassword.value })
    showToast('密码已重置，请告知本人并提醒修改')
    showReset.value = false
    clearReset()
  } catch (e) {
    // 拦截器已提示
  } finally { resetting.value = false }
}

// ---------- 删除 ----------
const removeUser = async (u) => {
  try {
    await showConfirmDialog({
      title: '删除用户',
      message: `确定删除用户「${u.username}」吗？删除后不可恢复！`
    })
  } catch (e) {
    return
  }
  try {
    await deleteUser(u.id)
    showToast('删除成功')
    refresh()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(async () => {
  // 角色选项
  try {
    const res = await getRoleList()
    const data = res.data || {}
    const roles = data.list || (Array.isArray(data) ? data : [])
    roleColumns.value = roles.map((r) => ({ text: r.roleName || r.name, value: r.id || r.roleId }))
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.tab-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px 0;
  color: #999;
}
</style>
