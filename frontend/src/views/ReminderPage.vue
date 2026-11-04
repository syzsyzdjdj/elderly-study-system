<template>
  <div class="reminder-page">
    <van-nav-bar title="我的提醒" left-text="返回" left-arrow @click-left="$router.back()" />

    <div class="tip">
      <van-icon name="info-o" />
      <span>设置定时提醒，到点会自动弹窗并语音播报</span>
    </div>

    <!-- 提醒列表 -->
    <div v-if="list.length" class="reminder-list">
      <van-cell-group inset>
        <van-cell v-for="r in list" :key="r.id" :title="r.title" :value="formatTime(r)" :label="r.content">
          <template #right-icon>
            <div class="row-actions">
              <van-switch v-model="r._enabled" size="20px" @change="toggle(r)" />
              <van-icon name="edit" class="action-icon" @click="openEdit(r)" />
              <van-icon name="delete-o" class="action-icon del" @click="remove(r)" />
            </div>
          </template>
        </van-cell>
      </van-cell-group>
    </div>
    <van-empty v-else description="暂无提醒，点下方按钮添加第一条" />

    <!-- 新增按钮 -->
    <div class="fab-wrap">
      <van-button type="warning" round icon="label-o" @click="openTemplateSheet">从模板添加</van-button>
      <van-button type="primary" round icon="plus" @click="openAdd">自定义添加</van-button>
    </div>

    <!-- 管理员配置的提醒模板 -->
    <van-action-sheet
      v-model:show="showTemplateSheet"
      class="reminder-sheet template-sheet"
      title="选择提醒模板"
      teleport="body"
      safe-area-inset-bottom
    >
      <p class="sheet-description">选择一项，即可为您添加定时提醒</p>
      <div class="template-options" :aria-busy="addingTemplate">
        <button v-for="action in templateActions" :key="action.template.id" class="template-option" type="button" :disabled="addingTemplate" @click="onTemplateSelect(action)">
          <span class="template-icon"><van-icon name="bell" /></span>
          <span class="template-copy">
            <span class="template-name">{{ action.template.templateName }}</span>
            <span class="template-subtitle">{{ action.subname }}</span>
            <span class="template-schedule"><van-icon name="clock-o" />{{ action.schedule }}</span>
          </span>
          <van-icon name="arrow" class="template-arrow" />
        </button>
      </div>
      <van-empty v-if="!templateActions.length" description="暂无可用模板" image-size="70" />
      <div class="sheet-footer"><van-button block round :disabled="addingTemplate" @click="showTemplateSheet = false">取消</van-button></div>
    </van-action-sheet>

    <!-- 编辑弹窗 -->
    <van-dialog
      v-model:show="showEdit"
      :title="editingId ? '编辑提醒' : '添加提醒'"
      show-cancel-button
      @confirm="save"
    >
      <div class="form-box">
        <van-field v-model="form.title" label="标题" placeholder="如：学习打卡" />
        <van-field v-model="form.content" label="提醒语" placeholder="如：老王您好，今天还没打卡哦" rows="2" autosize type="textarea" />
        <van-field :model-value="typeOptions.find(option => option.value === form.type)?.text" label="重复" is-link readonly @click="showTypePicker = true" />
        <van-field v-model="form.time" label="时间" type="time" />
        <van-field v-if="form.type === 'WEEKLY'" v-model="form.weekLabel" label="星期" is-link readonly @click="showWeekPicker = true" />
        <van-field v-if="form.type === 'MONTHLY'" v-model="form.dayOfMonth" label="几号" type="number" placeholder="1-31" />
        <van-field v-if="form.type === 'ONCE'" v-model="form.date" label="日期" type="date" />
      </div>
    </van-dialog>

    <!-- 类型选择 -->
    <van-popup v-model:show="showTypePicker" position="bottom" round class="reminder-sheet" teleport="body" safe-area-inset-bottom>
      <van-picker
        :model-value="[form.type]"
        :columns="typeOptions"
        @confirm="onTypeConfirm"
        @cancel="showTypePicker = false"
        title="选择重复类型"
      />
    </van-popup>

    <!-- 星期选择 -->
    <van-popup v-model:show="showWeekPicker" position="bottom" round class="reminder-sheet" teleport="body" safe-area-inset-bottom>
      <van-picker
        :model-value="[weekOptions.find(option => option.text === form.weekLabel)?.value || 1]"
        :columns="weekOptions"
        @confirm="onWeekConfirm"
        @cancel="showWeekPicker = false"
        title="选择星期"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { createReminder, deleteReminder, listReminders, toggleReminder, updateReminder, listEnabledTemplates, useReminderTemplate } from '../api/reminder'

const list = ref([])
const templates = ref([])
const showEdit = ref(false)
const showTypePicker = ref(false)
const showWeekPicker = ref(false)
const showTemplateSheet = ref(false)
const addingTemplate = ref(false)
const editingId = ref(null)

const TYPE_LABEL = { DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月' }
const templateActions = computed(() => templates.value.map(t => {
  let when = TYPE_LABEL[t.remindType] || '每天'
  const time = t.remindTime ? t.remindTime.substring(0, 5) : '09:00'
  if (t.remindType === 'WEEKLY') when += ['一', '二', '三', '四', '五', '六', '日'][((t.dayOfWeek || 1) - 1)]
  if (t.remindType === 'MONTHLY') when += (t.dayOfMonth || '?') + '号'
  return { schedule: `${when} ${time}`, subname: t.title, template: t }
}))

async function loadTemplates() {
  try {
    const res = await listEnabledTemplates()
    templates.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
}

function openTemplateSheet() {
  if (!templates.value.length) {
    showToast('暂无可用的官方模板')
    return
  }
  showTemplateSheet.value = true
}

async function onTemplateSelect(action) {
  if (addingTemplate.value) return
  addingTemplate.value = true
  try {
    await useReminderTemplate(action.template.id)
    showTemplateSheet.value = false
    showToast('已按模板添加提醒')
    load()
  } catch (e) { /* 拦截器已提示 */ }
  finally { addingTemplate.value = false }
}

const form = reactive({
  title: '',
  content: '',
  type: 'DAILY',
  time: '09:00',
  weekLabel: '周一',
  dayOfMonth: '1',
  date: ''
})

const typeOptions = [
  { text: '每天', value: 'DAILY' },
  { text: '每周', value: 'WEEKLY' },
  { text: '每月', value: 'MONTHLY' },
  { text: '仅一次', value: 'ONCE' }
]

const weekOptions = [
  { text: '周一', value: 1 },
  { text: '周二', value: 2 },
  { text: '周三', value: 3 },
  { text: '周四', value: 4 },
  { text: '周五', value: 5 },
  { text: '周六', value: 6 },
  { text: '周日', value: 7 }
]

function formatTime(r) {
  const t = r.remindTime ? r.remindTime.substring(0, 5) : '09:00'
  switch (r.remindType) {
    case 'DAILY': return `每天 ${t}`
    case 'WEEKLY': return `每周${r.dayOfWeek ? weekOptions.find(w => w.value === r.dayOfWeek)?.text : ''} ${t}`
    case 'MONTHLY': return `每月${r.dayOfMonth || '?'}号 ${t}`
    case 'ONCE': return `一次 ${r.nextTriggerTime ? r.nextTriggerTime.substring(0, 16).replace('T', ' ') : t}`
    default: return t
  }
}

async function load() {
  try {
    const res = await listReminders()
    list.value = (res.data || []).map(r => ({ ...r, _enabled: r.enabled === 1 }))
  } catch (e) { /* 拦截器已提示 */ }
}

function openAdd() {
  editingId.value = null
  form.title = ''
  form.content = ''
  form.type = 'DAILY'
  form.time = '09:00'
  form.weekLabel = '周一'
  form.dayOfMonth = '1'
  form.date = ''
  showEdit.value = true
}

function openEdit(r) {
  editingId.value = r.id
  form.title = r.title
  form.content = r.content
  form.type = r.remindType
  form.time = r.remindTime ? r.remindTime.substring(0, 5) : '09:00'
  form.weekLabel = weekOptions.find(w => w.value === r.dayOfWeek)?.text || '周一'
  form.dayOfMonth = String(r.dayOfMonth || 1)
  form.date = r.nextTriggerTime ? r.nextTriggerTime.substring(0, 10) : ''
  showEdit.value = true
}

async function save() {
  if (!form.title.trim()) { showToast('请填标题'); return }
  if (!form.content.trim()) { showToast('请填提醒语'); return }

  const body = {
    title: form.title.trim(),
    content: form.content.trim(),
    remindType: form.type,
    remindTime: form.time + ':00',
    dayOfWeek: form.type === 'WEEKLY' ? (weekOptions.find(w => w.text === form.weekLabel)?.value || 1) : null,
    dayOfMonth: form.type === 'MONTHLY' ? parseInt(form.dayOfMonth) : null,
    enabled: 1
  }
  try {
    if (editingId.value) {
      body.id = editingId.value
      await updateReminder(body)
      showToast('已更新')
    } else {
      await createReminder(body)
      showToast('已添加')
    }
    showEdit.value = false
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

async function remove(r) {
  try {
    await showConfirmDialog({ title: '删除', message: `确定删除"${r.title}"吗？` })
    await deleteReminder(r.id)
    showToast('已删除')
    load()
  } catch (e) { /* 取消或拦截器 */ }
}

async function toggle(r) {
  try {
    await toggleReminder(r.id)
    showToast(r._enabled ? '已开启' : '已关闭')
  } catch (e) {
    r._enabled = !r._enabled  // 回滚 UI
  }
}

function onTypeConfirm({ selectedOptions }) {
  form.type = selectedOptions[0].value
  showTypePicker.value = false
}

function onWeekConfirm({ selectedOptions }) {
  form.weekLabel = selectedOptions[0].text
  showWeekPicker.value = false
}

onMounted(() => { load(); loadTemplates() })
</script>

<style scoped>
.reminder-page {
  min-height: 100vh;
  background: var(--page-bg, #faf4ec);
  padding-bottom: 80px;
}
.tip {
  margin: 12px 16px;
  padding: 10px 14px;
  background: #fff7ed;
  border-radius: 10px;
  color: #9a3412;
  font-size: 13px;
  display: flex;
  gap: 6px;
  align-items: center;
}
.row-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.action-icon {
  font-size: 20px;
  color: #16a34a;
}
.action-icon.del {
  color: #ef4444;
}
.fab-wrap {
  position: fixed;
  bottom: 24px;
  left: 16px;
  right: 16px;
  display: flex;
  gap: 10px;
}
.fab-wrap .van-button {
  flex: 1;
}
.form-box {
  padding: 8px 0 16px;
}
.reminder-sheet {
  width: 100%;
  max-width: 520px;
  left: 50%;
  margin-left: max(-260px, -50vw);
  max-height: 85vh;
  max-height: 85dvh;
  border-radius: 24px 24px 0 0;
  box-sizing: border-box;
}
.template-sheet { background: var(--page-bg, #faf4ec); }
.template-sheet :deep(.van-action-sheet__header) {
  padding: 12px 48px 0;
  font-size: 21px;
  font-weight: 700;
  background: var(--van-background-2, #fff);
}
.template-sheet :deep(.van-action-sheet__close) { top: 12px; }
.sheet-description {
  margin: 0;
  padding: 4px 20px 20px;
  text-align: center;
  color: var(--van-text-color-2, #646566);
  font-size: 15px;
  line-height: 1.6;
  background: var(--van-background-2, #fff);
}
.template-options { display: grid; gap: 12px; padding: 18px 16px; }
.template-option {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  min-height: 100px;
  padding: 18px 16px;
  border: 1px solid var(--van-border-color, #ebedf0);
  border-radius: 18px;
  background: var(--van-background-2, #fff);
  color: var(--van-text-color, #323233);
  text-align: left;
  font: inherit;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.template-option:active { background: var(--van-active-color, #f2f3f5); }
.template-option:focus-visible { outline: 3px solid var(--van-primary-color, #16a34a); outline-offset: 2px; }
.template-option:disabled { opacity: .6; cursor: wait; }
.template-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: #edf8ef;
  color: var(--van-primary-color, #16a34a);
  font-size: 25px;
}
.template-copy { display: flex; flex: 1; min-width: 0; flex-direction: column; align-items: flex-start; gap: 7px; }
.template-name { font-size: 18px; font-weight: 600; line-height: 1.5; overflow-wrap: anywhere; }
.template-subtitle { color: var(--van-text-color-2, #646566); font-size: 15px; line-height: 1.5; }
.template-schedule { display: inline-flex; align-items: center; gap: 6px; color: var(--van-primary-color, #16a34a); font-size: 15px; line-height: 1.5; }
.template-arrow { flex-shrink: 0; color: var(--van-text-color-3, #969799); font-size: 18px; }
.sheet-footer { padding: 0 16px 16px; }
.sheet-footer .van-button { min-height: 50px; font-size: 18px; }
.reminder-sheet :deep(.van-picker__toolbar) { height: 60px; }
.reminder-sheet :deep(.van-picker__confirm),
.reminder-sheet :deep(.van-picker__cancel) { min-width: 64px; height: 48px; font-size: 17px; }
</style>
