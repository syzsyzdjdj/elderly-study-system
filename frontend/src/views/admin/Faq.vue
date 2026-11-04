<template>
  <div class="admin-page">
    <h3 class="page-title">✅ 审核问答库</h3>
    <p class="page-tip">
      老人提问时优先匹配本库并直接返回人工审核的准确回答，未命中再交由 AI 大模型回答。
      停用或删除的条目不参与匹配。
    </p>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <van-dropdown-menu>
        <van-dropdown-item v-model="filterCategory" :options="categoryOptions" @change="reload" />
        <van-dropdown-item v-model="filterStatus" :options="statusOptions" @change="reload" />
      </van-dropdown-menu>
      <van-field
        v-model="keyword"
        placeholder="搜索问题/关键词/答案"
        clearable
        size="small"
        class="search-box"
        @keyup.enter="reload"
        @clear="reload"
      >
        <template #button>
          <van-button size="small" type="primary" @click="reload">搜索</van-button>
        </template>
      </van-field>
      <van-button type="primary" size="small" icon="plus" @click="openAdd">新增问答</van-button>
    </div>

    <!-- 问答卡片列表 -->
    <div v-for="f in list" :key="f.id" class="admin-course-card">
      <div class="course-line">
        <span class="c-title">问：{{ f.question }}</span>
      </div>
      <div class="c-line faq-answer">答：{{ f.answer }}</div>
      <div class="c-line faq-meta">
        <van-tag plain type="primary">{{ f.category }}</van-tag>
        <van-tag :type="f.status === 1 ? 'success' : 'default'" plain>
          {{ f.status === 1 ? '已审核启用' : '已停用' }}
        </van-tag>
        <span class="hit-text">命中 {{ f.hitCount || 0 }} 次</span>
      </div>
      <div v-if="f.keywords" class="c-line faq-keywords">关键词：{{ f.keywords }}</div>
      <div class="user-actions">
        <van-button size="small" type="primary" plain @click="toggleStatus(f)">
          {{ f.status === 1 ? '停用' : '启用' }}
        </van-button>
        <van-button size="small" type="primary" plain @click="openEdit(f)">编辑</van-button>
        <van-button size="small" type="danger" plain @click="remove(f)">删除</van-button>
      </div>
    </div>
    <van-empty v-if="loaded && !list.length" description="暂无审核问答" image-size="90" />

    <van-pagination
      v-model="page"
      :total-items="total"
      :items-per-page="size"
      @change="load"
    />

    <!-- 新增/编辑弹层 -->
    <van-popup v-model:show="showPopup" position="bottom" round :style="{ maxHeight: '90%' }">
      <div class="popup-body">
        <h3 class="page-title">{{ form.id ? '编辑审核问答' : '新增审核问答' }}</h3>
        <van-field v-model="form.question" label="标准问法" placeholder="老人完整这样问时直接命中" rows="1" type="textarea" autosize />
        <van-field
          v-model="form.keywords"
          label="匹配关键词"
          type="textarea"
          rows="2"
          autosize
          placeholder="逗号分隔，如：医保,报销,异地就医"
        />
        <van-field v-model="form.answer" label="权威解答" type="textarea" rows="6" placeholder="人工审核的标准回答内容" />
        <van-field label="业务分类">
          <template #input>
            <van-radio-group v-model="form.category" direction="horizontal">
              <van-radio v-for="c in CATEGORIES.slice(1)" :key="c.value" :name="c.value">{{ c.label }}</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field label="审核启用">
          <template #input>
            <van-switch v-model="enabled" :active-value="1" :inactive-value="0" />
          </template>
        </van-field>
        <div class="form-actions">
          <van-button block type="primary" size="large" :loading="saving" @click="save">保存</van-button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { deleteFaq, getFaqPage, saveFaq } from '../../api/faq'

const CATEGORIES = [
  { text: '全部分类', value: '' },
  { text: '政务办事', value: '政务办事', label: '政务' },
  { text: '手机操作', value: '手机操作', label: '手机' },
  { text: '反诈安全', value: '反诈安全', label: '反诈' },
  { text: '便民生活', value: '便民生活', label: '便民' },
  { text: '其他', value: '其他', label: '其他' }
]
const categoryOptions = CATEGORIES.map(({ text, value }) => ({ text, value }))
const statusOptions = [
  { text: '全部状态', value: '' },
  { text: '已启用', value: 1 },
  { text: '已停用', value: 0 }
]

const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loaded = ref(false)
const saving = ref(false)
const keyword = ref('')
const filterCategory = ref('')
const filterStatus = ref('')

const showPopup = ref(false)
const enabled = ref(1)
const form = reactive({ id: null, question: '', keywords: '', answer: '', category: '政务办事' })

const reload = () => {
  page.value = 1
  load()
}

const load = async () => {
  try {
    const res = await getFaqPage({
      page: page.value,
      size,
      category: filterCategory.value || undefined,
      status: filterStatus.value === '' ? undefined : filterStatus.value,
      keyword: keyword.value || undefined
    })
    list.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (e) { /* 拦截器已提示 */ }
  loaded.value = true
}

const openAdd = () => {
  Object.assign(form, {
    id: null,
    question: '',
    keywords: '',
    answer: '',
    category: filterCategory.value || '政务办事'
  })
  enabled.value = 1
  showPopup.value = true
}

const openEdit = (f) => {
  Object.assign(form, {
    id: f.id,
    question: f.question,
    keywords: f.keywords || '',
    answer: f.answer,
    category: f.category || '其他'
  })
  enabled.value = f.status
  showPopup.value = true
}

const toggleStatus = async (f) => {
  try {
    await saveFaq({
      id: f.id,
      question: f.question,
      keywords: f.keywords,
      answer: f.answer,
      category: f.category,
      status: f.status === 1 ? 0 : 1
    })
    showToast(f.status === 1 ? '已停用' : '已启用')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

const save = async () => {
  if (!form.question.trim() || !form.answer.trim()) {
    showToast('请填写标准问法和权威解答')
    return
  }
  saving.value = true
  try {
    await saveFaq({ ...form, status: enabled.value })
    showToast('保存成功')
    showPopup.value = false
    load()
  } catch (e) { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

const remove = async (f) => {
  try {
    await showConfirmDialog({ title: '删除问答', message: `确定删除「${f.question}」吗？` })
  } catch { return }
  try {
    await deleteFaq(f.id)
    showToast('删除成功')
    load()
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.search-box {
  flex: 1;
  min-width: 160px;
  padding: 0;
}
.faq-answer {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #444;
}
.faq-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}
.hit-text {
  font-size: 12px;
  color: #969799;
  margin-left: 2px;
}
.faq-keywords {
  font-size: 12px;
  color: #969799;
}
</style>
