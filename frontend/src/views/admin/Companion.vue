<template>
  <div class="admin-page">
    <h3 class="page-title">💬 AI 陪伴话术与提醒</h3>

    <van-tabs v-model:active="activeTab" sticky>
      <!-- ============ Tab1：陪伴话术 ============ -->
      <van-tab title="陪伴话术">
        <div class="filter-bar">
          <van-field v-model="sceneFilter" label="场景" placeholder="GREETING/FESTIVAL/ENCOURAGE/RECOMMEND" style="flex:1" clearable />
          <van-button size="small" type="primary" @click="load">查询</van-button>
          <van-button size="small" type="primary" icon="plus" @click="openAdd">新增</van-button>
        </div>

        <div v-for="s in list" :key="s.id" class="admin-course-card">
          <div class="course-line">
            <span class="c-title">{{ s.title }}</span>
            <van-tag type="primary">{{ SCENE[s.scene] || s.scene }}</van-tag>
            <van-tag :type="s.enabled === 1 ? 'success' : 'default'">{{ s.enabled === 1 ? '启用' : '停用' }}</van-tag>
          </div>
          <div class="c-line">关键词：{{ s.keyword || '-' }}　排序：{{ s.sort ?? 0 }}</div>
          <div class="c-line ellipsis2">{{ s.content }}</div>
          <div class="user-actions">
            <van-button size="small" type="primary" plain @click="openEdit(s)">编辑</van-button>
            <van-button size="small" type="danger" plain @click="remove(s)">删除</van-button>
          </div>
        </div>
        <van-empty v-if="loaded && !list.length" description="暂无话术" image-size="90" />

        <van-popup v-model:show="showPopup" position="bottom" round>
          <div class="popup-body">
            <h3 class="page-title">{{ form.id ? '编辑话术' : '新增话术' }}</h3>
            <van-field label="场景">
              <template #input>
                <van-radio-group v-model="form.scene" direction="horizontal">
                  <van-radio name="GREETING">日常问候</van-radio>
                  <van-radio name="FESTIVAL">节日祝福</van-radio>
                  <van-radio name="ENCOURAGE">学习鼓励</van-radio>
                  <van-radio name="RECOMMEND">课程推荐</van-radio>
                </van-radio-group>
              </template>
            </van-field>
            <van-field v-model="form.title" label="标题" placeholder="如：早上好问候" />
            <van-field v-model="form.keyword" label="关键词" placeholder="触发词，多个用逗号分隔" />
            <van-field v-model="form.content" label="话术内容" type="textarea" rows="5" placeholder="AI 陪伴对老人说的暖心话（支持 TTS 朗读）" />
            <van-field v-model="form.sort" label="排序" type="number" placeholder="数字越小越靠前" />
            <van-field label="启用">
              <template #input>
                <van-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
              </template>
            </van-field>
            <div class="form-actions">
              <van-button block type="primary" size="large" :loading="saving" @click="save">保存</van-button>
            </div>
          </div>
        </van-popup>
      </van-tab>

      <!-- ============ Tab2：提醒模板 ============ -->
      <van-tab title="定时提醒模板">
        <div class="filter-bar">
          <span class="tpl-tip">老人端可在"我的提醒"中一键选用下列模板</span>
          <van-button size="small" type="primary" icon="plus" @click="openTplAdd">新增模板</van-button>
        </div>

        <div v-for="t in tplList" :key="t.id" class="admin-course-card">
          <div class="course-line">
            <span class="c-title">{{ t.templateName }}</span>
            <van-tag plain type="warning">{{ TYPE_TEXT[t.remindType] || t.remindType }}</van-tag>
            <van-tag :type="t.enabled === 1 ? 'success' : 'default'">{{ t.enabled === 1 ? '启用' : '停用' }}</van-tag>
          </div>
          <div class="c-line">提醒标题：{{ t.title }}　时间：{{ freqText(t) }}　排序：{{ t.sort ?? 0 }}</div>
          <div class="c-line ellipsis2">{{ t.content }}</div>
          <div class="user-actions">
            <van-button size="small" type="primary" plain @click="openTplEdit(t)">编辑</van-button>
            <van-button size="small" type="danger" plain @click="removeTpl(t)">删除</van-button>
          </div>
        </div>
        <van-empty v-if="tplLoaded && !tplList.length" description="暂无提醒模板" image-size="90" />

        <van-popup v-model:show="showTplPopup" position="bottom" round>
          <div class="popup-body">
            <h3 class="page-title">{{ tplForm.id ? '编辑提醒模板' : '新增提醒模板' }}</h3>
            <van-field v-model="tplForm.templateName" label="模板名称" placeholder="如：养老待遇资格认证提醒" />
            <van-field v-model="tplForm.title" label="提醒标题" placeholder="老人提醒列表中显示，如：学习打卡" />
            <van-field v-model="tplForm.content" label="播报文本" type="textarea" rows="4" placeholder="到点语音播报的内容" />
            <van-field label="重复类型">
              <template #input>
                <van-radio-group v-model="tplForm.remindType" direction="horizontal">
                  <van-radio name="DAILY">每天</van-radio>
                  <van-radio name="WEEKLY">每周</van-radio>
                  <van-radio name="MONTHLY">每月</van-radio>
                </van-radio-group>
              </template>
            </van-field>
            <van-field v-model="tplForm.time" label="提醒时间" type="time" />
            <van-field v-if="tplForm.remindType === 'WEEKLY'" v-model="tplForm.weekLabel" label="星期" is-link readonly placeholder="选择星期" @click="showWeekPicker = true" />
            <van-field v-if="tplForm.remindType === 'MONTHLY'" v-model="tplForm.dayOfMonth" label="几号" type="number" placeholder="1-31" />
            <van-field v-model="tplForm.sort" label="排序" type="number" placeholder="数字越小越靠前" />
            <van-field label="启用">
              <template #input>
                <van-switch v-model="tplForm.enabled" :active-value="1" :inactive-value="0" />
              </template>
            </van-field>
            <div class="form-actions">
              <van-button block type="primary" size="large" :loading="tplSaving" @click="saveTpl">保存</van-button>
            </div>
          </div>
        </van-popup>

        <van-popup v-model:show="showWeekPicker" position="bottom">
          <van-picker
            :columns="weekOptions"
            title="选择星期"
            @confirm="onWeekConfirm"
            @cancel="showWeekPicker = false"
          />
        </van-popup>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { getAdminCompanionList, saveCompanion, deleteCompanion } from '../../api/companion'
import { adminListTemplates, adminSaveTemplate, adminDeleteTemplate } from '../../api/reminder'

const SCENE = { GREETING: '日常问候', FESTIVAL: '节日祝福', ENCOURAGE: '学习鼓励', RECOMMEND: '课程推荐' }
const TYPE_TEXT = { DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月', ONCE: '仅一次' }
const weekOptions = [
  { text: '周一', value: 1 }, { text: '周二', value: 2 }, { text: '周三', value: 3 },
  { text: '周四', value: 4 }, { text: '周五', value: 5 }, { text: '周六', value: 6 }, { text: '周日', value: 7 }
]

const activeTab = ref(0)

// ---------------- 陪伴话术 ----------------
const list = ref([])
const loaded = ref(false)
const saving = ref(false)
const sceneFilter = ref('')
const showPopup = ref(false)
const form = reactive({ id: null, scene: 'GREETING', title: '', keyword: '', content: '', sort: 0, enabled: 1 })

const load = async () => {
  try {
    const res = await getAdminCompanionList(sceneFilter.value || undefined)
    list.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  loaded.value = true
}

const openAdd = () => {
  Object.assign(form, { id: null, scene: sceneFilter.value || 'GREETING', title: '', keyword: '', content: '', sort: 0, enabled: 1 })
  showPopup.value = true
}
const openEdit = (s) => {
  Object.assign(form, { id: s.id, scene: s.scene, title: s.title || '', keyword: s.keyword || '', content: s.content, sort: s.sort ?? 0, enabled: s.enabled })
  showPopup.value = true
}
const save = async () => {
  if (!form.title || !form.content) { showToast('请填写标题和内容'); return }
  saving.value = true
  try {
    await saveCompanion({ ...form, sort: Number(form.sort) || 0 })
    showToast('保存成功')
    showPopup.value = false
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}
const remove = async (s) => {
  try { await showConfirmDialog({ title: '删除话术', message: `确定删除「${s.title}」吗？` }) } catch { return }
  try {
    await deleteCompanion(s.id)
    showToast('删除成功')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

// ---------------- 提醒模板 ----------------
const tplList = ref([])
const tplLoaded = ref(false)
const tplSaving = ref(false)
const showTplPopup = ref(false)
const showWeekPicker = ref(false)
const tplForm = reactive({ id: null, templateName: '', title: '', content: '', remindType: 'DAILY', time: '09:00', weekLabel: '周一', dayOfWeek: 1, dayOfMonth: '1', sort: 0, enabled: 1 })

function freqText(t) {
  const time = t.remindTime ? t.remindTime.substring(0, 5) : '09:00'
  if (t.remindType === 'WEEKLY') return `每周${weekOptions.find(w => w.value === t.dayOfWeek)?.text || ''} ${time}`
  if (t.remindType === 'MONTHLY') return `每月${t.dayOfMonth || '?'}号 ${time}`
  return `每天 ${time}`
}

const loadTpl = async () => {
  try {
    const res = await adminListTemplates()
    tplList.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  tplLoaded.value = true
}

const openTplAdd = () => {
  Object.assign(tplForm, { id: null, templateName: '', title: '', content: '', remindType: 'DAILY', time: '09:00', weekLabel: '周一', dayOfWeek: 1, dayOfMonth: '1', sort: 0, enabled: 1 })
  showTplPopup.value = true
}
const openTplEdit = (t) => {
  Object.assign(tplForm, {
    id: t.id,
    templateName: t.templateName || '',
    title: t.title || '',
    content: t.content || '',
    remindType: t.remindType || 'DAILY',
    time: t.remindTime ? t.remindTime.substring(0, 5) : '09:00',
    weekLabel: weekOptions.find(w => w.value === t.dayOfWeek)?.text || '周一',
    dayOfWeek: t.dayOfWeek || 1,
    dayOfMonth: String(t.dayOfMonth || 1),
    sort: t.sort ?? 0,
    enabled: t.enabled
  })
  showTplPopup.value = true
}
const onWeekConfirm = ({ selectedOptions }) => {
  tplForm.weekLabel = selectedOptions[0].text
  tplForm.dayOfWeek = selectedOptions[0].value
  showWeekPicker.value = false
}
const saveTpl = async () => {
  if (!tplForm.templateName.trim()) { showToast('请填写模板名称'); return }
  if (!tplForm.title.trim()) { showToast('请填写提醒标题'); return }
  if (!tplForm.content.trim()) { showToast('请填写播报文本'); return }
  const body = {
    id: tplForm.id,
    templateName: tplForm.templateName.trim(),
    title: tplForm.title.trim(),
    content: tplForm.content.trim(),
    remindType: tplForm.remindType,
    remindTime: (tplForm.time || '09:00') + ':00',
    dayOfWeek: tplForm.remindType === 'WEEKLY' ? tplForm.dayOfWeek : null,
    dayOfMonth: tplForm.remindType === 'MONTHLY' ? (parseInt(tplForm.dayOfMonth) || 1) : null,
    sort: Number(tplForm.sort) || 0,
    enabled: tplForm.enabled
  }
  tplSaving.value = true
  try {
    await adminSaveTemplate(body)
    showToast('保存成功')
    showTplPopup.value = false
    loadTpl()
  } catch (e) { /* 拦截器已提示 */ } finally {
    tplSaving.value = false
  }
}
const removeTpl = async (t) => {
  try { await showConfirmDialog({ title: '删除模板', message: `确定删除「${t.templateName}」吗？` }) } catch { return }
  try {
    await adminDeleteTemplate(t.id)
    showToast('删除成功')
    loadTpl()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(() => { load(); loadTpl() })
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
}
.tpl-tip {
  flex: 1;
  font-size: 12px;
  color: #9a3412;
}
.ellipsis2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
