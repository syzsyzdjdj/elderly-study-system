<template>
  <div class="admin-page">
    <h3 class="page-title">⚙️ 系统参数配置</h3>

    <van-loading v-if="loading" class="page-loading">加载参数中…</van-loading>

    <template v-else>
      <!-- AI 大模型参数 -->
      <div class="config-section">
        <h4 class="config-title">🤖 智能问答 / AI 陪伴</h4>
        <div class="config-row" v-for="c in aiConfigs" :key="c.configKey">
          <div class="config-label">
            <div class="cfg-name">{{ c.configName || c.configKey }}</div>
            <div class="cfg-key">{{ c.configKey }}</div>
          </div>
          <div class="config-input">
            <van-switch
              v-if="c.configKey === 'ai_enabled'"
              v-model="c._bool"
              :active-value="true"
              :inactive-value="false"
              @change="(v) => (c.configValue = v ? 'true' : 'false')"
            />
            <van-field
              v-else
              v-model="c.configValue"
              :type="c.configKey === 'ai_api_key' ? 'password' : 'text'"
              :placeholder="c.remark || ''"
            />
          </div>
        </div>
      </div>

      <!-- TTS 参数 -->
      <div class="config-section">
        <h4 class="config-title">🔊 TTS 语音合成</h4>
        <div class="config-row" v-for="c in ttsConfigs" :key="c.configKey">
          <div class="config-label">
            <div class="cfg-name">{{ c.configName || c.configKey }}</div>
            <div class="cfg-key">{{ c.configKey }}</div>
          </div>
          <div class="config-input">
            <van-field
              v-model="c.configValue"
              :type="['tts_speed', 'tts_volume'].includes(c.configKey) ? 'number' : 'text'"
              :step="['tts_speed', 'tts_volume'].includes(c.configKey) ? '0.1' : undefined"
              :placeholder="c.remark || ''"
            />
          </div>
        </div>
        <div class="config-tip">
          语速建议 0.8 ~ 1.3（老人可稍慢）；音量 0.1 ~ 1.0；音色编码如 default、female、male。<br/>
          rate / volume / pitch 仅对 edge-tts 方言音色生效：rate 如 -15%（慢），volume 如 +50%，pitch 如 +10Hz，留空用默认。
        </div>
      </div>

      <!-- 其他参数 -->
      <div class="config-section" v-if="otherConfigs.length">
        <h4 class="config-title">🧩 其他参数</h4>
        <div class="config-row" v-for="c in otherConfigs" :key="c.configKey">
          <div class="config-label">
            <div class="cfg-name">{{ c.configName || c.configKey }}</div>
            <div class="cfg-key">{{ c.configKey }}</div>
          </div>
          <div class="config-input">
            <van-field v-model="c.configValue" :placeholder="c.remark || ''" />
          </div>
        </div>
      </div>

      <van-button type="primary" block size="large" :loading="saving" @click="saveAll">
        保存全部参数
      </van-button>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { getConfigList, saveConfigs } from '../../api/config'

const loading = ref(true)
const saving = ref(false)
const configs = ref([])

const aiConfigs = computed(() =>
  configs.value.filter((c) => c.configKey.startsWith('ai_')).map(normalize)
)
const ttsConfigs = computed(() =>
  configs.value.filter((c) => c.configKey.startsWith('tts_')).map(normalize)
)
const otherConfigs = computed(() =>
  configs.value.filter((c) => !c.configKey.startsWith('ai_') && !c.configKey.startsWith('tts_'))
)

// 布尔型参数补一个内部开关状态
const normalize = (c) => {
  if (c.configKey === 'ai_enabled') {
    c._bool = c.configValue === 'true'
  }
  return c
}

const saveAll = async () => {
  saving.value = true
  try {
    const payload = configs.value.map((c) => ({
      id: c.id,
      configKey: c.configKey,
      configValue: c.configValue,
      configName: c.configName,
      remark: c.remark
    }))
    await saveConfigs(payload)
    showToast('参数保存成功，已即时生效')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getConfigList()
    configs.value = res.data || []
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-loading {
  display: block;
  text-align: center;
  padding-top: 60px;
}
.config-section {
  background: #fff;
  border-radius: 10px;
  padding: 4px 14px 14px;
  margin-bottom: 16px;
}
.config-title {
  margin: 10px 0;
  font-size: 16px;
  color: #1d4ed8;
}
.config-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f2f3f5;
}
.config-label {
  width: 200px;
  flex-shrink: 0;
}
.cfg-name {
  font-weight: 600;
  font-size: 14px;
}
.cfg-key {
  font-size: 12px;
  color: #969799;
}
.config-input {
  flex: 1;
  min-width: 0;
}
.config-tip {
  font-size: 12px;
  color: #969799;
  padding-top: 8px;
}
</style>
