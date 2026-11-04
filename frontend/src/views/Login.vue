<template>
  <div class="login-page">
    <div class="login-banner">
      <div class="login-logo"><van-icon name="like" /></div>
      <h1>老年数字素养学习系统</h1>
      <p>学手机 · 防诈骗 · 乐享智慧生活</p>
    </div>

    <van-form class="login-form-wrap" @submit="onSubmit">
      <div class="login-welcome">
        <h2>欢迎回来</h2>
        <p>请输入账号和密码，继续今天的学习。</p>
      </div>
      <van-cell-group inset>
        <van-field
          v-model="form.username"
          name="username"
          autocomplete="username"
          label="账号"
          placeholder="请输入您的账号"
          :rules="[{ required: true, message: '请输入账号' }]"
        />
        <van-field
          v-model="form.password"
          :type="showPassword ? 'text' : 'password'"
          name="password"
          autocomplete="current-password"
          label="密码"
          placeholder="请输入您的密码"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <template #right-icon>
            <button type="button" class="password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword">
              <van-icon :name="showPassword ? 'eye-o' : 'closed-eye'" size="23" />
            </button>
          </template>
        </van-field>
      </van-cell-group>
      <div class="remember-login"><van-checkbox v-model="form.rememberMe">这是我的手机，保持登录30天</van-checkbox><p>公用手机请取消勾选；不保存您的密码。</p></div>

      <div class="login-actions">
        <van-button round block type="primary" size="large" native-type="submit" :loading="submitting">
          登录
        </van-button>
        <van-button round block plain type="primary" size="large" native-type="button" @click="router.push('/register')">
          注册新账号
        </van-button>
      </div>

      <div class="login-help">首次使用？注册账号后即可开始学习。</div>
      <div class="login-help"><a href="/download.html">下载 Android App</a></div>
    </van-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { loginApi } from '../api/auth'
import { useUserStore } from '../store/user'

const router = useRouter()
const form = reactive({ username: '', password: '', rememberMe: true })
const showPassword = ref(false)
const submitting = ref(false)

// 规格书指定：登录提交逻辑
const onSubmit = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await loginApi({ ...form, username: form.username.trim() })
    const store = useUserStore()
    store.setToken(res.data.token)
    store.setUserInfo(res.data.userInfo)
    store.setRoleKey(res.data.roleKey)
    store.setRemembered(form.rememberMe && res.data.roleKey === 'user')
    form.password = ''
    showToast('登录成功')
    router.push(['admin', 'course_manager'].includes(store.roleKey) ? '/admin' : '/home')
  } catch (err) {
    showToast(err.msg || '登录失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.remember-login { padding:16px 20px 0; }
.remember-login p { font-size:14px; color:#607266; margin:10px 0 0; }
</style>
