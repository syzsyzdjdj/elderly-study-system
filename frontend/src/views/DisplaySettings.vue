<template>
  <div class="display-page">
    <van-nav-bar title="显示与账号设置" left-arrow @click-left="router.back()" />

    <!-- 字号 -->
    <div class="section">
      <h3 class="section-title">🔠 字体大小</h3>
      <van-radio-group v-model="fontScale" direction="horizontal" class="scale-group">
        <van-radio name="100" checked-color="#16a34a">标准</van-radio>
        <van-radio name="115" checked-color="#16a34a">大号</van-radio>
        <van-radio name="130" checked-color="#16a34a">特大号</van-radio>
      </van-radio-group>

      <h3 class="section-title">🅱️ 字体粗细</h3>
      <van-cell center title="字体加粗" label="字迹更浓，看得更清楚">
        <template #right-icon>
          <van-switch v-model="fontBold" :active-value="1" :inactive-value="0" />
        </template>
      </van-cell>

      <h3 class="section-title">📏 行距</h3>
      <van-radio-group v-model="lineHeight" direction="horizontal" class="scale-group">
        <van-radio name="140" checked-color="#16a34a">紧凑</van-radio>
        <van-radio name="160" checked-color="#16a34a">标准</van-radio>
        <van-radio name="190" checked-color="#16a34a">宽松</van-radio>
      </van-radio-group>

      <p
        class="preview-text"
        :style="{ fontSize: previewPx + 'px', fontWeight: fontBold ? 600 : 400, lineHeight: Number(lineHeight) / 100 }"
      >
        预览：欢迎使用老年数字素养学习系统，祝您学习愉快、身体健康
      </p>
      <van-button block type="primary" size="large" :loading="saving" @click="saveDisplay">
        保存显示设置
      </van-button>
    </div>

    <!-- 高对比度 -->
    <div class="section">
      <h3 class="section-title">🌓 高对比度模式</h3>
      <van-cell center title="开启高对比度" label="文字更深、边框更明显，视力不好也能看清">
        <template #right-icon>
          <van-switch v-model="highContrast" :active-value="1" :inactive-value="0" />
        </template>
      </van-cell>
    </div>

    <!-- 常住地区 -->
    <div class="section">
      <h3 class="section-title">📍 常住地区</h3>
      <van-field
        v-model="regionText"
        is-link
        readonly
        label="省 / 市 / 区"
        placeholder="点击修改常住地区"
        @click="showArea = true"
      />
      <p class="section-tip" style="padding: 6px 16px 0;">
        地区用于本地政务政策、办事入口与本地化课程展示
      </p>
      <div class="save-region">
        <van-button block type="primary" size="large" :loading="saving" @click="saveRegion">
          保存地区
        </van-button>
      </div>
    </div>

    <!-- 修改密码 -->
    <div class="section">
      <h3 class="section-title">🔑 修改密码</h3>
      <van-cell-group inset>
        <van-field v-model="pwd.oldPassword" type="password" label="原密码" placeholder="请输入原密码" />
        <van-field v-model="pwd.newPassword" type="password" label="新密码" placeholder="不少于 6 位" />
        <van-field v-model="pwd.confirm" type="password" label="确认新密码" placeholder="请再次输入新密码" />
      </van-cell-group>
      <div class="form-actions">
        <van-button block type="primary" size="large" :loading="saving" @click="savePassword">
          修改密码
        </van-button>
      </div>
    </div>

    <div class="tabbar-holder"></div>

    <van-popup v-model:show="showArea" position="bottom" round teleport="body">
      <van-area
        :area-list="areaList"
        title="选择常住地区"
        @confirm="onAreaConfirm"
        @cancel="showArea = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { areaList } from '@vant/area-data'
import { getProfile, updateProfile, changePassword } from '../api/profile'
import { useUserStore } from '../store/user'
import { applyDisplay } from '../utils/display'

const router = useRouter()
const store = useUserStore()

const fontScale = ref('100')
const highContrast = ref(0)
const fontBold = ref(0)
const lineHeight = ref('160')
const saving = ref(false)
const region = reactive({ province: '', city: '', district: '' })
const showArea = ref(false)
const pwd = reactive({ oldPassword: '', newPassword: '', confirm: '' })

const regionText = computed(() =>
  [region.province, region.city, region.district].filter(Boolean).join(' / ')
)

// 三档字号对应预览像素
const previewPx = computed(() => ({ 100: 17, 115: 20, 130: 23 }[Number(fontScale.value)] || 17))

const onAreaConfirm = ({ selectedOptions }) => {
  const names = (selectedOptions || []).map((o) => o?.text).filter(Boolean)
  region.province = names[0] || ''
  region.city = names[1] || ''
  region.district = names[2] || ''
  showArea.value = false
}

const refreshStore = (data) => {
  store.setUserInfo({ ...(store.userInfo || {}), ...data })
}

const saveDisplay = async () => {
  saving.value = true
  try {
    const res = await updateProfile({
      fontScale: Number(fontScale.value),
      highContrast: highContrast.value,
      fontBold: fontBold.value,
      lineHeight: Number(lineHeight.value)
    })
    if (res.data) refreshStore(res.data)
    applyDisplay(store.userInfo)
    showToast('显示设置已保存')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

const saveRegion = async () => {
  if (!region.province) {
    showToast('请先选择常住地区')
    return
  }
  saving.value = true
  try {
    const res = await updateProfile({ ...region })
    if (res.data) refreshStore(res.data)
    showToast('常住地区已更新')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

const savePassword = async () => {
  if (!pwd.oldPassword || !pwd.newPassword) {
    showToast('请填写原密码和新密码')
    return
  }
  if (pwd.newPassword.length < 6) {
    showToast('新密码不少于 6 位')
    return
  }
  if (pwd.newPassword !== pwd.confirm) {
    showToast('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await changePassword({ oldPassword: pwd.oldPassword, newPassword: pwd.newPassword })
    showToast('密码修改成功，请牢记新密码')
    pwd.oldPassword = ''
    pwd.newPassword = ''
    pwd.confirm = ''
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getProfile()
    const info = res.data || {}
    fontScale.value = String(info.fontScale || 100)
    highContrast.value = Number(info.highContrast) || 0
    fontBold.value = Number(info.fontBold) || 0
    lineHeight.value = String(info.lineHeight || 160)
    region.province = info.province || ''
    region.city = info.city || ''
    region.district = info.district || ''
    refreshStore(info)
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.scale-group {
  display: flex;
  gap: 28px;
  padding: 8px 4px 14px;
}
.scale-group .van-radio {
  font-size: 18px;
}
.preview-text {
  color: #444;
  background: #fff8f2;
  border-radius: 10px;
  padding: 14px;
  margin: 0 0 14px;
}
.save-region {
  padding: 12px 16px 0;
}
</style>
