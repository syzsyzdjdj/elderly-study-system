<template>
  <div class="admin-page review-page">
    <h3 class="page-title">分类人工复核</h3>
    <p class="page-tip">请根据提问原文选择业务类别；跨业务、含义不清或不属于现有类别时，选择“未确定业务”。标签仅用于独立测试，不参与训练。</p>
    <p class="page-tip">评估至少需要30条不同提问、2个业务类别、每个已标注类别至少5条。重复、冲突或与课程训练标题重合的提问不计入测试。</p>
    <div class="review-actions" role="group" aria-label="Spark分类结果分组">
      <van-button :type="predictionGroup === 'classified' ? 'primary' : 'default'" :disabled="saving !== null" @click="changeGroup('classified')">Spark 已分类（{{ groupCounts.classified }}）</van-button>
      <van-button :type="predictionGroup === 'undetermined' ? 'primary' : 'default'" :disabled="saving !== null" @click="changeGroup('undetermined')">未确定业务（{{ groupCounts.undetermined }}）</van-button>
    </div>
    <p class="page-tip">分组依据最近保存的 Spark 逐条预测，与人工标签分开。模型分类和置信度仅供核查，请独立判断人工标签。</p>
    <div v-if="groupCounts.unprocessed" class="unprocessed-note">
      {{ groupCounts.unprocessed }}条提问尚无有效 Spark 预测（新提问、原文变更或旧批次无明细），需重新运行文本分类。
      <van-button size="small" plain :disabled="saving !== null" @click="changeGroup('unprocessed')">查看这些提问</van-button>
    </div>
    <p v-if="predictionGroup === 'unprocessed'" class="page-tip">当前查看：尚未分类的提问</p>
    <div class="review-actions">
      <van-checkbox v-model="onlyPending" :disabled="saving !== null" @change="reload">只看待标注</van-checkbox>
      <van-button size="small" :disabled="saving !== null || loading" @click="load">刷新</van-button>
      <van-button size="small" plain type="primary" to="/admin/spark-tasks">查看评估 / 运行文本分类</van-button>
    </div>
    <p v-if="loaded" class="page-tip">当前来源：{{ scope === 'mock' ? '模拟数据（不能作为真实效果结论）' : '真实数据' }} · 最近{{ lookbackDays }}天 · {{ total }}条{{ onlyPending ? '待标注' : '提问' }}。保存后需重新运行文本分类，才会更新评估。</p>
    <van-loading v-if="loading" class="review-loading">加载提问中</van-loading>
    <div v-if="loadError && !loading" class="review-error" role="alert">
      <p>提问加载失败，请重试。加载成功后才会显示待复核内容。</p>
      <van-button size="small" type="primary" @click="load">重新加载</van-button>
    </div>
    <div v-for="q in records" :key="q.id" class="review-card">
      <p class="review-question">{{ q.question }}</p>
      <p class="page-tip">{{ q.province || '未知地区' }} · 编号 {{ q.id }}</p>
      <p class="model-result">Spark：{{ q.sparkCategory || '尚未分类' }}<span v-if="q.sparkConfidence != null"> · 模型置信度 {{ (Number(q.sparkConfidence) * 100).toFixed(1) }}%</span></p>
      <p v-if="q.sparkRunId" class="page-tip batch-id">预测批次：{{ q.sparkRunId }}</p>
      <p class="page-tip">人工标签：{{ q.category || '待标注' }}</p>
      <label class="review-label" :for="`category-${q.id}`">人工确认类别</label>
      <select :id="`category-${q.id}`" v-model="selections[q.id]" :disabled="saving !== null || loading" @change="drafts.remember(q, $event.target.value)">
        <option value="" disabled>请选择业务类别</option>
        <option v-for="category in categories" :key="category" :value="category">{{ category }}</option>
      </select>
      <div class="review-actions">
        <van-button size="small" type="primary" :loading="saving === q.id" :disabled="!selections[q.id] || saving !== null || loading" @click="save(q)">保存人工标签</van-button>
        <van-button v-if="q.category" size="small" plain type="danger" :disabled="saving !== null || loading" @click="remove(q)">撤销标签</van-button>
      </div>
    </div>
    <van-empty v-if="loaded && !loading && !loadError && !records.length" :description="`当前分组${onlyPending ? '没有待人工标注的提问' : '没有提问'}`" />
    <van-pagination v-if="total > size" v-model="page" :total-items="total" :items-per-page="size" @change="load" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { getClassificationReviews, saveClassificationLabel, deleteClassificationLabel } from '../../api/analysis'
import { ClassificationReviewDrafts } from '../../utils/classificationReviewDrafts.mjs'

const records = ref([]), categories = ref([]), selections = ref({})
const page = ref(1), total = ref(0), scope = ref(''), lookbackDays = ref(90)
const onlyPending = ref(true), loading = ref(false), loaded = ref(false), saving = ref(null)
const loadError = ref(false)
const predictionGroup = ref('classified')
const groupCounts = ref({ classified: 0, undetermined: 0, unprocessed: 0 })
const size = 20
let requestVersion = 0
let disposed = false
const drafts = new ClassificationReviewDrafts()
const load = async () => {
  const version = ++requestVersion
  loading.value = true
  loadError.value = false
  try {
    const res = await getClassificationReviews({ page: page.value, size, onlyPending: onlyPending.value, predictionGroup: predictionGroup.value })
    if (disposed || version !== requestVersion) return
    const data = res.data
    records.value = data.records || []
    categories.value = data.categories || []
    total.value = Number(data.total || 0)
    scope.value = data.dataScope
    lookbackDays.value = data.lookbackDays
    groupCounts.value = data.groupCounts || { classified: 0, undetermined: 0, unprocessed: 0 }
    const restored = drafts.restore(records.value, categories.value)
    selections.value = restored.selections
    if (restored.discarded) showToast('部分题目或标签已变化，请重新确认这些题目的类别')
    loaded.value = true
    if (!records.value.length && page.value > 1) { page.value = Math.max(1, Math.ceil(total.value / size)); await load() }
  } catch {
    if (!disposed && version === requestVersion) loadError.value = true
  }
  finally { if (version === requestVersion) loading.value = false }
}
const reload = () => { page.value = 1; load() }
const changeGroup = (group) => { predictionGroup.value = group; reload() }
const save = async (q) => {
  if (disposed || saving.value !== null || loading.value) return
  saving.value = q.id
  try {
    await saveClassificationLabel(q.id, selections.value[q.id], q.question, q.reviewToken ?? null)
    drafts.discard(q.id)
    if (!disposed) { showToast('人工标签已保存'); await load() }
  }
  catch { /* 请求拦截器显示失败信息 */ }
  finally { saving.value = null }
}
const remove = async (q) => {
  if (disposed || saving.value !== null || loading.value) return
  saving.value = q.id
  try {
    await deleteClassificationLabel(q.id, q.question, q.reviewToken ?? null)
    drafts.discard(q.id)
    if (!disposed) { showToast('标签已撤销'); await load() }
  }
  catch { /* 请求拦截器显示失败信息 */ }
  finally { saving.value = null }
}
onMounted(load)
onBeforeUnmount(() => { disposed = true; requestVersion++ })
</script>

<style scoped>
.review-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin: 12px 0; }
.review-card { padding: 16px; margin: 14px 0; border: 1px solid #dbe4ee; border-radius: 10px; background: white; }
.review-question { margin: 0 0 10px; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 17px; line-height: 1.7; }
.review-label { display: block; margin: 12px 0 6px; }
select { width: 100%; min-height: 44px; padding: 8px; border: 1px solid #94a3b8; border-radius: 6px; background: white; font-size: 16px; }
.review-loading { margin: 20px 0; }
.model-result { color: #1d4ed8; font-weight: 600; }
.batch-id { overflow-wrap: anywhere; }
.unprocessed-note { padding: 12px; background: #fff7ed; border-radius: 8px; line-height: 1.7; }
.review-error { margin: 20px 0; padding: 16px; border: 1px solid #fca5a5; border-radius: 10px; background: #fff7ed; color: #9a3412; }
</style>
