<template>
  <div class="admin-page">
    <h3 class="page-title">👤 个人中心</h3>
    <p class="page-tip">查看管理员账号信息，可修改登录密码。</p>

    <div class="admin-course-card">
      <div class="course-line"><span class="c-title">账号信息</span></div>
      <div class="c-line">登录账号：{{ info.username || '-' }}</div>
      <div class="c-line">昵称：{{ info.nickname || '-' }}</div>
      <div class="c-line">角色：{{ roleText }}</div>
      <div class="c-line">手机号：{{ info.phone || '-' }}</div>
    </div>

    <div class="admin-course-card">
      <div class="course-line"><span class="c-title">修改密码</span></div>
      <van-field v-model="oldPwd" label="原密码" type="password" placeholder="请输入当前登录密码" />
      <van-field v-model="newPwd" label="新密码" type="password" placeholder="至少6位，建议字母+数字组合" />
      <van-field v-model="confirmPwd" label="确认新密码" type="password" placeholder="再次输入新密码" />
      <div class="user-actions" style="margin-top:12px;">
        <van-button type="primary" :loading="saving" @click="submit">保存新密码</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getProfile, changePassword } from '../../api/profile'
import { logoutApi } from '../../api/auth'
import { useUserStore } from '../../store/user'

const info = ref({})
const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')
const saving = ref(false)
const router = useRouter()
const store = useUserStore()

const ROLE_TEXT = { admin: '系统管理员', course_manager: '课程管理员', user: '普通用户' }
const roleText = computed(() => ROLE_TEXT[info.value.roleKey] || info.value.roleKey || '-')

const load = async () => {
  try {
    const res = await getProfile()
    info.value = res.data || {}
  } catch (e) { /* 拦截器已提示 */ }
}

const submit = async () => {
  if (!oldPwd.value || !newPwd.value) {
    showToast('请填写原密码和新密码')
    return
  }
  if (newPwd.value.length < 6) {
    showToast('新密码至少 6 位')
    return
  }
  if (newPwd.value !== confirmPwd.value) {
    showToast('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await changePassword({ oldPassword: oldPwd.value, newPassword: newPwd.value })
    showToast('密码修改成功，请重新登录')
    oldPwd.value = newPwd.value = confirmPwd.value = ''
    setTimeout(async () => {
      try { await logoutApi() } catch { /* 忽略 */ }
      store.clearUser()
      router.replace('/login')
    }, 1200)
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-tip {
  color: #999;
  font-size: 14px;
  margin: 0 0 12px;
}
.c-line {
  color: #666;
  font-size: 14px;
  line-height: 1.9;
}
</style>
