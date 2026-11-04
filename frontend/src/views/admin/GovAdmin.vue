<template>
  <div class="admin-page">
    <h3 class="page-title">🏛 政务服务管理</h3>

    <van-tabs v-model:active="tab" sticky @change="onTabChange">
      <van-tab title="办事直达入口" name="service" />
      <van-tab title="政策资讯" name="policy" />
    </van-tabs>

    <!-- ========== 办事入口 ========== -->
    <template v-if="tab === 'service'">
      <van-button type="primary" block size="large" icon="plus" @click="openServiceAdd">新增办事入口</van-button>

      <div v-for="s in services" :key="s.id" class="admin-course-card">
        <div class="course-line">
          <span class="c-title">{{ s.icon }} {{ s.name }}</span>
          <van-tag :type="s.scope === 'NATIONAL' ? 'primary' : 'warning'">
            {{ s.scope === 'NATIONAL' ? '全国' : (s.province || '地方') }}
          </van-tag>
          <van-tag :type="s.status === 1 ? 'success' : 'default'">{{ s.status === 1 ? '启用' : '停用' }}</van-tag>
        </div>
        <div class="c-line ellipsis2">🔗 {{ s.serviceUrl }}</div>
        <div class="user-actions">
          <van-button size="small" type="primary" plain @click="openServiceEdit(s)">编辑</van-button>
          <van-button size="small" type="danger" plain @click="removeService(s)">删除</van-button>
        </div>
      </div>
      <van-empty v-if="loadedSvc && !services.length" description="暂无办事入口" image-size="90" />
    </template>

    <!-- ========== 政策 ========== -->
    <template v-else>
      <div class="filter-bar">
        <van-field v-model="policyLevel" label="层级" placeholder="NATIONAL/PROVINCE/CITY" style="flex:1" clearable />
        <van-field v-model="policyProvince" label="省份" placeholder="如：广东省" style="flex:1" clearable />
        <van-button size="small" type="primary" @click="reloadPolicies">查询</van-button>
      </div>
      <van-button type="primary" block size="large" icon="plus" @click="openPolicyAdd">新增政策</van-button>

      <div v-for="p in policies" :key="p.id" class="admin-course-card">
        <div class="course-line">
          <span class="c-title">{{ p.title }}</span>
          <van-tag :type="levelTag(p.policyLevel)[1]">{{ levelTag(p.policyLevel)[0] }}</van-tag>
        </div>
        <div class="c-line ellipsis2">{{ p.content }}</div>
        <div class="c-line">
          来源：{{ p.source || '-' }}　地区：{{ [p.province, p.city].filter(Boolean).join(' / ') || '全国' }}　日期：{{ p.publishTime || '-' }}
        </div>
        <div class="user-actions">
          <van-button size="small" type="primary" plain @click="openPolicyEdit(p)">编辑</van-button>
          <van-button size="small" type="danger" plain @click="removePolicy(p)">删除</van-button>
        </div>
      </div>
      <van-empty v-if="loadedPol && !policies.length" description="暂无政策" image-size="90" />
    </template>

    <!-- 办事入口弹窗 -->
    <van-popup v-model:show="showService" position="bottom" round>
      <div class="popup-body">
        <h3 class="page-title">{{ svcForm.id ? '编辑办事入口' : '新增办事入口' }}</h3>
        <van-field v-model="svcForm.name" label="名称" placeholder="如：医保电子凭证" />
        <van-field v-model="svcForm.icon" label="图标" placeholder="一个 emoji，如 🏥" />
        <van-field v-model="svcForm.serviceUrl" label="跳转链接" placeholder="https://…" />
        <van-field label="范围">
          <template #input>
            <van-radio-group v-model="svcForm.scope" direction="horizontal">
              <van-radio name="NATIONAL">全国通用</van-radio>
              <van-radio name="LOCAL">地方专属</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-model="svcForm.province" label="省份" placeholder="地方项填写，如：广东省" />
        <van-field v-model="svcForm.city" label="城市" placeholder="可留空" />
        <van-field v-model="svcForm.sort" label="排序" type="number" placeholder="数字越小越靠前" />
        <van-field label="启用">
          <template #input>
            <van-switch v-model="svcForm.status" :active-value="1" :inactive-value="0" />
          </template>
        </van-field>
        <div class="form-actions">
          <van-button block type="primary" size="large" :loading="saving" @click="saveService">保存</van-button>
        </div>
      </div>
    </van-popup>

    <!-- 政策弹窗 -->
    <van-popup v-model:show="showPolicy" position="bottom" round :style="{ maxHeight: '88%' }">
      <div class="popup-body">
        <h3 class="page-title">{{ polForm.id ? '编辑政策' : '新增政策' }}</h3>
        <van-field v-model="polForm.title" label="标题" placeholder="政策标题" />
        <van-field v-model="polForm.content" label="正文" type="textarea" rows="5" placeholder="政策摘要/正文" />
        <van-field v-model="polForm.source" label="来源" placeholder="如：国务院办公厅" />
        <van-field label="层级">
          <template #input>
            <van-radio-group v-model="polForm.policyLevel" direction="horizontal">
              <van-radio name="NATIONAL">全国</van-radio>
              <van-radio name="PROVINCE">省级</van-radio>
              <van-radio name="CITY">市级</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field v-model="polForm.province" label="省份" placeholder="地方政策填写" />
        <van-field v-model="polForm.city" label="城市" placeholder="市级政策填写" />
        <van-field v-model="polForm.govUrl" label="原文链接" placeholder="https://…" />
        <van-field v-model="polForm.publishTime" label="发布日期" type="date" placeholder="YYYY-MM-DD" />
        <van-field label="启用">
          <template #input>
            <van-switch v-model="polForm.status" :active-value="1" :inactive-value="0" />
          </template>
        </van-field>
        <div class="form-actions">
          <van-button block type="primary" size="large" :loading="saving" @click="savePolicy">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import {
  getAdminGovServices, saveGovService, deleteGovService,
  getAdminGovPolicies, saveGovPolicy, deleteGovPolicy
} from '../../api/gov'

const tab = ref('service')
const saving = ref(false)

// ---- 办事入口 ----
const services = ref([])
const loadedSvc = ref(false)
const showService = ref(false)
const svcForm = reactive({ id: null, name: '', icon: '🏛', serviceUrl: '', scope: 'NATIONAL', province: '', city: '', sort: 0, status: 1 })

const loadServices = async () => {
  try {
    const res = await getAdminGovServices()
    services.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  loadedSvc.value = true
}

const openServiceAdd = () => {
  Object.assign(svcForm, { id: null, name: '', icon: '🏛', serviceUrl: '', scope: 'NATIONAL', province: '', city: '', sort: 0, status: 1 })
  showService.value = true
}
const openServiceEdit = (s) => {
  Object.assign(svcForm, { id: s.id, name: s.name, icon: s.icon || '', serviceUrl: s.serviceUrl, scope: s.scope, province: s.province || '', city: s.city || '', sort: s.sort ?? 0, status: s.status })
  showService.value = true
}
const saveService = async () => {
  if (!svcForm.name || !svcForm.serviceUrl) { showToast('请填写名称和链接'); return }
  saving.value = true
  try {
    await saveGovService({ ...svcForm, sort: Number(svcForm.sort) || 0 })
    showToast('保存成功')
    showService.value = false
    loadServices()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}
const removeService = async (s) => {
  try { await showConfirmDialog({ title: '删除入口', message: `确定删除「${s.name}」吗？` }) } catch { return }
  try {
    await deleteGovService(s.id)
    showToast('删除成功')
    loadServices()
  } catch (e) { /* 拦截器已提示 */ }
}

// ---- 政策 ----
const policies = ref([])
const loadedPol = ref(false)
const showPolicy = ref(false)
const policyLevel = ref('')
const policyProvince = ref('')
const polForm = reactive({
  id: null, title: '', content: '', source: '', policyLevel: 'NATIONAL',
  province: '', city: '', govUrl: '', publishTime: '', status: 1
})

const LEVEL_MAP = { NATIONAL: ['全国', 'primary'], PROVINCE: ['省级', 'warning'], CITY: ['市级', 'success'] }
const levelTag = (l) => LEVEL_MAP[l] || [l, 'default']

const loadPolicies = async () => {
  try {
    const res = await getAdminGovPolicies({
      level: policyLevel.value || undefined,
      province: policyProvince.value || undefined
    })
    policies.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  loadedPol.value = true
}
const reloadPolicies = () => loadPolicies()

const openPolicyAdd = () => {
  Object.assign(polForm, { id: null, title: '', content: '', source: '', policyLevel: 'NATIONAL', province: '', city: '', govUrl: '', publishTime: '', status: 1 })
  showPolicy.value = true
}
const openPolicyEdit = (p) => {
  Object.assign(polForm, {
    id: p.id, title: p.title, content: p.content || '', source: p.source || '',
    policyLevel: p.policyLevel, province: p.province || '', city: p.city || '',
    govUrl: p.govUrl || '', publishTime: p.publishTime || '', status: p.status
  })
  showPolicy.value = true
}
const savePolicy = async () => {
  if (!polForm.title) { showToast('请填写标题'); return }
  saving.value = true
  try {
    await saveGovPolicy({ ...polForm })
    showToast('保存成功')
    showPolicy.value = false
    loadPolicies()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}
const removePolicy = async (p) => {
  try { await showConfirmDialog({ title: '删除政策', message: `确定删除「${p.title}」吗？` }) } catch { return }
  try {
    await deleteGovPolicy(p.id)
    showToast('删除成功')
    loadPolicies()
  } catch (e) { /* 拦截器已提示 */ }
}

const onTabChange = (name) => {
  if (name === 'policy' && !loadedPol.value) loadPolicies()
}

onMounted(loadServices)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 10px 0;
}
.ellipsis2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
