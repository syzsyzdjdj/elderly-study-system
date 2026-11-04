<template>
  <div class="login-page">
    <div class="login-banner"><div class="login-logo"><van-icon name="friends-o" /></div><h1>注册新账号</h1><p>只填两项，就能开始学习</p></div>
    <van-form class="login-form-wrap" @submit="onSubmit">
      <div class="login-welcome"><h2>给自己建一个学习账号</h2><p>填写账号和密码即可。称呼、手机号和地区，都可以以后在“我的”里补充。</p></div>
      <van-cell-group inset>
        <van-field v-model="form.username" name="username" autocomplete="username" label="账号" maxlength="30" placeholder="取一个好记的名字" :rules="[{ required: true, message: '请填写账号' }]" />
        <van-field v-model="form.password" name="password" autocomplete="new-password" :type="showPassword ? 'text' : 'password'" label="密码" maxlength="72" placeholder="设置至少6位密码" :rules="[{ required: true, message: '请填写密码' }, { pattern: /^.{6,72}$/, message: '密码请设置6至72个字符' }]">
          <template #right-icon><button type="button" class="password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><van-icon :name="showPassword ? 'eye-o' : 'closed-eye'" size="23" /></button></template>
        </van-field>
      </van-cell-group>
      <p class="register-tip">点密码旁的眼睛可以检查输入，不用重复填写密码。</p>
      <div class="register-remember"><van-checkbox v-model="rememberMe">这是我的手机，保持登录30天</van-checkbox><p>公用手机请取消勾选。</p></div>
      <div class="login-actions">
        <van-button round block type="primary" size="large" native-type="submit" :loading="submitting" loading-text="正在创建账号…">注册并开始学习</van-button>
        <van-button round block plain type="primary" size="large" native-type="button" @click="router.replace('/login')">已有账号，去登录</van-button>
      </div>
    </van-form>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { registerApi, loginApi } from '../api/auth'
import { useUserStore } from '../store/user'
const router = useRouter()
const store = useUserStore()
const form = reactive({ username: '', password: '' })
const rememberMe = ref(true)
const showPassword = ref(false)
const submitting = ref(false)
const onSubmit = async () => {
  if (submitting.value) return
  const username = form.username.trim()
  if (!username || form.password.length < 6 || form.password.length > 72) { showToast('请填写账号和至少6位密码'); return }
  submitting.value = true
  let created = false
  try {
    await registerApi({ username, password: form.password })
    created = true
    const res = await loginApi({ username, password: form.password, rememberMe: rememberMe.value })
    store.setToken(res.data.token); store.setUserInfo(res.data.userInfo); store.setRoleKey(res.data.roleKey)
    store.setRemembered(rememberMe.value && res.data.roleKey === 'user')
    form.password = ''
    showToast('注册成功，开始学习吧')
    router.replace('/home')
  } catch (error) {
    if (created) { form.password = ''; showToast('账号已创建，请用刚才的账号密码登录'); router.replace('/login') }
    else showToast(error.msg || '注册未成功，请稍后重试')
  } finally { submitting.value = false }
}
</script>
<style scoped>
.register-tip,.register-remember { padding:14px 20px 0; line-height:1.7; }
.register-tip,.register-remember p { color:#607266; font-size:14px; }
.register-remember p { margin:8px 0 0; }
</style>
