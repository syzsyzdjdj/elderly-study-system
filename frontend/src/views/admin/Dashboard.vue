<template>
  <div class="admin-page dashboard-page">
    <h3 class="page-title">📊 数据看板</h3>

    <!-- 操作区：一键运行 Spark 全部分析 + 模拟数据管理 -->
    <div class="analysis-actions">
      <van-button type="primary" size="small" round :loading="running" @click="runAll">
        ▶ 运行全部分析
      </van-button>
      <van-button size="small" round plain @click="genData">生成模拟数据</van-button>
      <van-button size="small" round plain type="danger" @click="clearData">清理模拟数据</van-button>
    </div>

    <!-- 分析进度条弹窗 -->
    <van-overlay :show="running" z-index="2000">
      <div class="progress-overlay">
        <div class="progress-card">
          <div class="progress-title">正在执行分析任务…</div>
          <van-progress
            :percentage="progressPercent"
            :stroke-width="10"
            color="#1989fa"
            track-color="#e8e8e8"
            :show-pivot="false"
          />
          <div class="progress-task">{{ progressTaskName }}</div>
          <div class="progress-msg">{{ progressMessage }}</div>
          <div class="progress-hint">{{ progressPercent }}% · 请勿关闭页面</div>
        </div>
      </div>
    </van-overlay>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-num">{{ stats.userCount }}</div>
        <div class="stat-label">注册用户</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.courseCount }}</div>
        <div class="stat-label">课程总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.learnerCount }}</div>
        <div class="stat-label">已分析学员</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.finishRate }}%</div>
        <div class="stat-label">用户课程完成率</div>
      </div>
    </div>

    <!-- 分地区学情：省级柱状图，点击柱子下钻市级 -->
    <div class="chart-title">
      分地区学员分布
      <span v-if="regionProvince" class="drill-back" @click="backToProvince">← 返回省级</span>
    </div>
    <div class="region-sub" v-if="regionRows.length">
      {{ regionProvince ? `当前：${regionProvince}（市级分布，点击柱条无下钻）` : '点击下方柱条可查看该省市级分布' }}
    </div>
    <van-empty v-if="regionLoaded && !regionRows.length" description="暂无地区数据（生成带地区的模拟数据后可见）" image-size="70" />
    <div v-if="regionRows.length" ref="regionRef" class="chart"></div>

    <!-- K-Means 学习人群聚类 -->
    <div class="chart-title">学习型人群分布（K-Means 聚类）</div>
    <van-empty v-if="!kmeansRows.length" description="暂无聚类结果，请先运行分析" image-size="70" />
    <template v-else>
      <div ref="pieRef" class="chart"></div>
      <div class="chart-title">各人群日均累计学习时长（分钟）</div>
      <div ref="barRef" class="chart"></div>
    </template>

    <!-- 学习趋势 -->
    <div class="chart-title">学习趋势（最近30个自然日）</div>
    <p class="region-sub trend-meta">
      来源：{{ trendMetadata?.dataScope === 'mock' ? '模拟数据' : trendMetadata?.dataScope === 'real' ? '真实数据' : '未知（历史结果）' }}
      · 分析批次：{{ trendMetadata?.runId || '未记录' }}
      · 分析时间：{{ trendMetadata?.createTime || '未记录' }}
    </p>
    <p class="region-sub">截至 {{ trendToday || '今天' }} 的30个自然日（含当天）；批次覆盖日期内无记录按0展示，未分析日期留空。图表显示最近成功保存的结果。</p>
    <p v-if="trendMetadata?.fromDate && trendMetadata?.toDate" class="region-sub">批次覆盖日期：{{ trendMetadata.fromDate }} 至 {{ trendMetadata.toDate }}</p>
    <van-empty v-if="!trendRows.length && !trendMetadata" description="暂无趋势数据，请先运行分析" image-size="70" />
    <div v-else ref="lineRef" class="chart"></div>

    <!-- 课程热度排行 -->
    <div class="chart-title">课程热度 Top10</div>
    <van-empty v-if="!hotRows.length" description="暂无热度数据，请先运行分析" image-size="70" />
    <van-cell
      v-for="h in hotRows.slice(0, 10)"
      :key="h.courseId"
      :title="`${h.rank}、${h.title}`"
      is-link
      @click="router.push(`/course/${h.courseId}`)"
    >
      <template #value>
        <van-tag type="primary" plain>{{ h.studyCount }} 名学习用户</van-tag>
      </template>
    </van-cell>

    <!-- 课程分类学习占比 -->
    <div class="chart-title">课程分类学习占比</div>
    <van-empty v-if="!categoryRows.length" description="暂无分类占比数据，请先运行分析" image-size="70" />
    <div v-else ref="categoryRef" class="chart"></div>

    <!-- 问答热点词 -->
    <div class="chart-title">AI 问答热点词 Top15</div>
    <van-empty v-if="!wordRows.length" description="暂无词频数据，请先运行分析" image-size="70" />
    <div v-else ref="wordRef" class="chart"></div>

    <!-- 问答文本分类：地区 × 业务双维度（朴素贝叶斯） -->
    <div class="chart-title">AI 问答业务主题分类（地区 × 业务双维度 · 朴素贝叶斯）</div>
    <p class="region-sub">模型置信度表示分类倾向，未经校准，不代表准确率。低置信度及证据不足的提问归为“未确定业务”。</p>
    <van-button size="small" plain type="primary" to="/admin/classification-review">人工复核与独立测试</van-button>
    <van-empty v-if="!classifyRows.length" description="暂无分类结果，请先运行分析" image-size="70" />
    <div v-else ref="classifyRef" class="chart chart-tall"></div>
    <div v-if="classifyRows.length" class="confidence-table-wrap">
      <table class="confidence-table">
        <thead><tr><th>地区 / 业务</th><th>提问数</th><th>平均置信度</th><th>最低置信度</th><th>高置信度提问数（≥80%）</th></tr></thead>
        <tbody><tr v-for="r in classifyRows" :key="`${r.province}|${r.category}`">
          <td>{{ r.province }} / {{ r.category }}</td><td>{{ r.questionCount }}</td>
          <td>{{ formatConfidence(r.avgConfidence) }}</td><td>{{ formatConfidence(r.minConfidence) }}</td>
          <td>{{ r.highConfidenceCount ?? '—' }}</td>
        </tr></tbody>
      </table>
      <p class="region-sub">“—”表示未确定业务或旧批次没有置信度记录。高置信度仍可能误判；实际准确率请查看 Spark 任务页的人工测试指标。</p>
    </div>

    <!-- FP-Growth 课程共学关联规则 -->
    <div class="chart-title">课程共学关联规则（FP-Growth · Top10）</div>
    <van-empty v-if="!associationRows.length" description="暂无关联规则，请先运行分析" image-size="70" />
    <div v-else class="rule-list">
      <div class="rule-item" v-for="(r, i) in associationRows.slice(0, 10)" :key="i">
        <div class="rule-flow">
          <span class="rule-course">{{ r.antecedentTitles }}</span>
          <span class="rule-arrow">→</span>
          <span class="rule-course">{{ r.consequentTitles }}</span>
        </div>
        <div class="rule-metrics">
          <van-tag plain type="primary">置信度 {{ formatPct(r.confidence) }}</van-tag>
          <van-tag v-if="r.lift != null" plain type="success">提升度 {{ Number(r.lift).toFixed(3) }}</van-tag>
          <van-tag plain type="success" style="margin-left:6px">支持度 {{ formatPct(r.support) }}</van-tag>
        </div>
        <div class="rule-desc">学了「{{ r.antecedentTitles }}」的老人中 {{ formatPct(r.confidence) }} 也学了「{{ r.consequentTitles }}」</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { recentStudyTrend } from '../../utils/studyTrend.mjs'
import { showToast, showLoadingToast, closeToast } from 'vant'
import { useRouter } from 'vue-router'
import {
  getAnalysisResult,
  getStudyTrendSnapshot,
  getCourseHot,
  getCategoryShare,
  getAssociation,
  getWordFreq,
  getTextClassify,
  runAllAnalyses,
  getAnalysisProgress,
  genMockData,
  clearMockData,
  getRegionStats
} from '../../api/analysis'
import { getUserList } from '../../api/user'
import { getCourseList } from '../../api/course'

const router = useRouter()

const pieRef = ref(null)
const barRef = ref(null)
const lineRef = ref(null)
const wordRef = ref(null)
const regionRef = ref(null)
const classifyRef = ref(null)
const categoryRef = ref(null)

// 分地区统计：provinceRows 省级、cityRows 下钻市级
const regionRows = ref([])
const regionLoaded = ref(false)
const regionProvince = ref('')
let regionChart = null

const stats = reactive({ userCount: '-', courseCount: '-', learnerCount: '-', finishRate: '-' })
const kmeansRows = ref([])
const trendRows = ref([])
const trendMetadata = ref(null)
const trendToday = ref('')
const hotRows = ref([])
const wordRows = ref([])
const classifyRows = ref([])
const categoryRows = ref([])
const associationRows = ref([])
const running = ref(false)
const progressPercent = ref(0)
const progressTaskName = ref('')
const progressMessage = ref('')
let progressTimer = null

let charts = []
let disposed = false

// 兼容后端多种返回结构（聚类结果）
const normalize = (raw) => {
  const arr = Array.isArray(raw) ? raw : raw && (raw.list || raw.clusters || raw.data)
  const list = Array.isArray(arr) ? arr : arr && (arr.list || arr.clusters)
  return (list || []).map((item) => ({
    name: item.name || item.label || item.clusterName || '人群',
    count: Number(item.count ?? item.userCount ?? item.value ?? 0),
    avg: Number(item.avgDuration ?? item.avgTime ?? item.averageDuration ?? item.duration ?? item.avgStudyTime ?? 0),
    finish: Number(item.finishRate ?? item.avgFinishRate ?? 0)
  }))
}

const toList = (raw) => {
  if (Array.isArray(raw)) return raw
  if (raw && Array.isArray(raw.list)) return raw.list
  if (raw && Array.isArray(raw.records)) return raw.records
  if (raw && Array.isArray(raw.data)) return raw.data
  return []
}

// 关联规则的置信度/支持度转百分比展示
const formatPct = (v) => `${Math.round(Number(v ?? 0) * 100)}%`
const formatConfidence = (v) => v == null || v === '' || !Number.isFinite(Number(v)) ? '—' : `${(Number(v) * 100).toFixed(1)}%`

// 统计卡片数据：并行拉取用户数、课程数
const loadStats = async () => {
  try {
    const res = await getUserList({ page: 1, pageSize: 1 })
    const total = res.data && (res.data.total ?? res.data.list?.length)
    if (total !== undefined) stats.userCount = Number(total)
  } catch (e) { /* 拦截器已提示 */ }
  try {
    const res = await getCourseList({ page: 1, size: 1 })
    const total = res.data && (res.data.total ?? res.data.records?.length)
    if (total !== undefined) stats.courseCount = Number(total)
  } catch (e) { /* 拦截器已提示 */ }
}

// 读取全部已落库的分析结果（只读，不触发分析）
const loadAll = async () => {
  const [k, t, h, w, c, cs, ar] = await Promise.allSettled([
    getAnalysisResult(),
    getStudyTrendSnapshot(),
    getCourseHot(),
    getWordFreq(),
    getTextClassify(),
    getCategoryShare(),
    getAssociation()
  ])
  if (k.status === 'fulfilled') {
    kmeansRows.value = normalize(k.value.data)
    if (kmeansRows.value.length) {
      stats.learnerCount = kmeansRows.value.reduce((s, r) => s + r.count, 0)
      const finishSum = kmeansRows.value.reduce((s, r) => s + r.finish * r.count, 0)
      stats.finishRate = stats.learnerCount > 0 ? Math.round((finishSum / stats.learnerCount) * 100) : 0
    }
  }
  if (t.status === 'fulfilled') {
    trendRows.value = toList(t.value.data?.rows)
    trendMetadata.value = t.value.data?.metadata || null
    trendToday.value = t.value.data?.today || ''
  }
  if (h.status === 'fulfilled') hotRows.value = toList(h.value.data)
  if (w.status === 'fulfilled') wordRows.value = toList(w.value.data)
  if (c.status === 'fulfilled') classifyRows.value = toList(c.value.data)
  if (cs.status === 'fulfilled') categoryRows.value = toList(cs.value.data)
  if (ar.status === 'fulfilled') associationRows.value = toList(ar.value.data)
  await nextTick()
  await renderCharts()
}

// ---------- 分地区统计（省级 + 市级下钻） ----------
const loadRegion = async () => {
  try {
    const res = await getRegionStats(regionProvince.value ? { province: regionProvince.value } : {})
    const key = regionProvince.value ? 'city' : 'province'
    regionRows.value = res.data?.[key] || []
    await renderRegionChart()
  } catch (e) { /* 拦截器已提示 */ }
  regionLoaded.value = true
}

const renderRegionChart = async () => {
  if (disposed || !regionRef.value || !regionRows.value.length) return
  const echarts = await import('echarts')
  if (regionChart) regionChart.dispose()
  regionChart = echarts.init(regionRef.value)
  const nameKey = regionProvince.value ? 'city' : 'province'
  regionChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        const r = regionRows.value[p.dataIndex]
        if (!r) return ''
        return `${r[nameKey]}<br/>学员 ${r.userCount} 人｜学习 ${r.learnerCount} 人<br/>行为 ${r.logCount} 次｜时长 ${r.studyTime} 分钟｜完成 ${r.finishCount} 次`
      }
    },
    grid: { left: 45, right: 20, top: 25, bottom: 70 },
    xAxis: {
      type: 'category',
      data: regionRows.value.map((r) => r[nameKey]),
      axisLabel: { fontSize: 12, rotate: 35, interval: 0 }
    },
    yAxis: { type: 'value', name: '学员数', axisLabel: { fontSize: 12 } },
    series: [
      {
        type: 'bar',
        barMaxWidth: 40,
        itemStyle: { color: regionProvince.value ? '#14b8a6' : '#2563eb' },
        data: regionRows.value.map((r) => Number(r.userCount ?? 0))
      }
    ]
  })
  // 省级柱条点击 → 市级下钻
  regionChart.off('click')
  regionChart.on('click', (p) => {
    if (regionProvince.value) return
    const r = regionRows.value[p.dataIndex]
    if (r && r.province && r.province !== '未知') {
      regionProvince.value = r.province
      loadRegion()
    }
  })
  charts.push(regionChart)
}

const backToProvince = () => {
  regionProvince.value = ''
  loadRegion()
}

const renderCharts = async () => {
  if (disposed) return
  charts.forEach((c) => c && c.dispose())
  charts = []
  regionChart = null
  const echarts = await import('echarts')

  // 饼图：三类学习型人群分布
  if (kmeansRows.value.length && pieRef.value) {
    const pie = echarts.init(pieRef.value)
    pie.setOption({
      tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）' },
      legend: { bottom: 0, textStyle: { fontSize: 15 } },
      series: [
        {
          type: 'pie',
          radius: ['38%', '68%'],
          center: ['50%', '45%'],
          label: { fontSize: 15 },
          data: kmeansRows.value.map((r) => ({ name: r.name, value: r.count }))
        }
      ]
    })
    charts.push(pie)
  }

  // 柱状图：各人群平均学习时长
  if (kmeansRows.value.length && barRef.value) {
    const bar = echarts.init(barRef.value)
    bar.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 45, right: 15, top: 25, bottom: 35 },
      xAxis: { type: 'category', data: kmeansRows.value.map((r) => r.name), axisLabel: { fontSize: 14 } },
      yAxis: { type: 'value', axisLabel: { fontSize: 13 } },
      series: [{ type: 'bar', barWidth: 32, itemStyle: { color: '#2563eb' }, data: kmeansRows.value.map((r) => r.avg) }]
    })
    charts.push(bar)
  }

  // 折线图：近30天学习人次与时长
  if ((trendRows.value.length || trendMetadata.value) && lineRef.value) {
    const recent = recentStudyTrend(trendRows.value, trendToday.value, trendMetadata.value?.fromDate, trendMetadata.value?.toDate)
    const line = echarts.init(lineRef.value)
    line.setOption({
      tooltip: { trigger: 'axis' },
      legend: { top: 8, textStyle: { fontSize: 14 } },
      grid: { left: 15, right: 15, top: 65, bottom: 25, containLabel: true },
      xAxis: {
        type: 'category',
        data: recent.map((r) => r.studyDate),
        axisLabel: { fontSize: 12, rotate: 35, formatter: (value) => value.slice(5), hideOverlap: true }
      },
      yAxis: [
        { type: 'value', name: '活跃人数（人）', min: 0, minInterval: 1, axisLabel: { fontSize: 12 } },
        { type: 'value', name: '学习时长（分钟）', min: 0, axisLabel: { fontSize: 12 }, splitLine: { show: false } }
      ],
      series: [
        {
          name: '每日活跃人数',
          type: 'line',
          smooth: false,
          itemStyle: { color: '#2563eb' },
          data: recent.map((r) => r.studyCount)
        },
        {
          name: '学习时长',
          type: 'line',
          smooth: false,
          yAxisIndex: 1,
          itemStyle: { color: '#00A870' },
          data: recent.map((r) => r.studyDurationMin)
        }
      ]
    })
    charts.push(line)
  }

  // 环形饼图：课程分类学习占比
  if (categoryRows.value.length && categoryRef.value) {
    const palette = ['#2563eb', '#f27a1a', '#ef4444', '#10b981', '#8b5cf6', '#0ea5e9', '#eab308']
    const cat = echarts.init(categoryRef.value)
    cat.setOption({
      tooltip: {
        trigger: 'item',
        formatter: (p) => `${p.name}：${p.data.count} 名用户（参与分布 ${p.value}%）；同一用户可参与多个分类`
      },
      legend: { type: 'scroll', bottom: 0, textStyle: { fontSize: 13 } },
      series: [
        {
          type: 'pie',
          radius: ['38%', '68%'],
          center: ['50%', '45%'],
          label: { fontSize: 14, formatter: '{b}\n{d}%' },
          data: categoryRows.value.map((r, i) => ({
            name: r.categoryName || '未分类',
            value: Number(Number(r.shareRate ?? 0).toFixed(1)),
            count: Number(r.studyCount ?? 0),
            itemStyle: { color: palette[i % palette.length] }
          }))
        }
      ]
    })
    charts.push(cat)
  }

  // 横向条形图：问答热点词
  if (wordRows.value.length && wordRef.value) {
    const rows = wordRows.value.slice(0, 15).reverse()
    const word = echarts.init(wordRef.value)
    word.setOption({
      tooltip: { trigger: 'axis', formatter: '{b}：{c} 次' },
      grid: { left: 90, right: 30, top: 15, bottom: 25 },
      xAxis: { type: 'value', axisLabel: { fontSize: 12 } },
      yAxis: { type: 'category', data: rows.map((r) => r.word), axisLabel: { fontSize: 14 } },
      series: [{ type: 'bar', barWidth: 16, itemStyle: { color: '#FF8C42' }, data: rows.map((r) => Number(r.freq ?? 0)) }]
    })
    charts.push(word)
  }

  // 堆叠柱状图：AI 问答业务主题分类（省份 × 业务类别）
  if (classifyRows.value.length && classifyRef.value) {
    const provinces = [...new Set(classifyRows.value.map((r) => r.province))]
    const categories = [...new Set(classifyRows.value.map((r) => r.category))]
    const palette = ['#2563eb', '#f27a1a', '#ef4444', '#10b981', '#8b5cf6', '#0ea5e9', '#eab308']
    const cell = {}
    classifyRows.value.forEach((r) => {
      cell[`${r.province}|${r.category}`] = Number(r.questionCount ?? 0)
    })
    const classify = echarts.init(classifyRef.value)
    classify.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { bottom: 0, textStyle: { fontSize: 13 } },
      grid: { left: 50, right: 20, top: 25, bottom: 60 },
      xAxis: {
        type: 'category',
        data: provinces,
        axisLabel: { fontSize: 12, rotate: 30, interval: 0 }
      },
      yAxis: { type: 'value', name: '问答量', axisLabel: { fontSize: 12 } },
      series: categories.map((cat, i) => ({
        name: cat,
        type: 'bar',
        stack: 'total',
        barMaxWidth: 46,
        itemStyle: { color: palette[i % palette.length] },
        emphasis: { focus: 'series' },
        data: provinces.map((p) => cell[`${p}|${cat}`] || 0)
      }))
    })
    charts.push(classify)
  }

  // 地区图已有数据时随其他图表一起重建
  if (regionRows.value.length) {
    await renderRegionChart()
  }

  window.addEventListener('resize', onResize)
}

// 一键运行全部 7 个 Spark 分析任务（local 内嵌 / YARN 集群）
const runAll = async () => {
  if (running.value) return
  running.value = true
  progressPercent.value = 0
  progressTaskName.value = '准备中…'
  progressMessage.value = ''

  // 启动轮询进度（仅更新进度条，不判断完成）
  const poll = async () => {
    try {
      const res = await getAnalysisProgress()
      const p = res.data || {}
      if (p.current !== undefined && p.total) {
        progressPercent.value = Math.round((p.current / p.total) * 100)
      }
      if (p.taskName) progressTaskName.value = p.taskName
      if (p.message) progressMessage.value = p.message
    } catch (e) { /* 静默失败，下一轮继续 */ }
  }

  progressTimer = setInterval(poll, 2000)
  poll()

  try {
    const res = await runAllAnalyses()
    const results = res.data || []
    const ok = results.filter((r) => r.success).length
    const fail = results.filter((r) => !r.success)
    progressPercent.value = 100
    progressTaskName.value = '全部完成'
    progressMessage.value = ''
    showToast(`分析完成：成功 ${ok} / ${results.length}${fail.length ? '，失败见消息' : ''}`)
    fail.forEach((f) => console.warn(`[analysis] ${f.task} 失败:`, f.message))
    await loadAll()
    loadStats()
  } catch (e) {
    showToast('分析任务执行失败，请查看后端日志')
  } finally {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
    running.value = false
  }
}

// 生成模拟数据（10 万条学习日志 + 500 虚拟学员 + 2000 条提问）
const genData = async () => {
  showLoadingToast({ message: '正在生成模拟数据…', forbidClick: true, duration: 0 })
  try {
    const res = await genMockData({ users: 500, logs: 100000, aiQuestions: 2000 })
    closeToast()
    showToast(`模拟数据生成完成（${res.data?.studyLogs ?? ''} 条日志）`)
    loadStats()
  } catch (e) {
    closeToast()
  }
}

const clearData = async () => {
  try {
    await clearMockData()
    showToast('模拟数据已清理')
    await loadAll()
    loadStats()
  } catch (e) {
    // 拦截器已提示
  }
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  loadStats()
  loadRegion()
  await loadAll()
})

const onResize = () => {
  charts.forEach((c) => c && c.resize())
}

onBeforeUnmount(() => {
  disposed = true
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c && c.dispose())
  charts = []
})
</script>

<style scoped>
.trend-meta { overflow-wrap: anywhere; }
.confidence-table-wrap { overflow-x: auto; margin: 12px 0 20px; }
.confidence-table { border-collapse: collapse; width: 100%; min-width: 640px; font-size: 14px; }
.confidence-table th, .confidence-table td { padding: 10px 12px; border-bottom: 1px solid #e2e8f0; text-align: left; }
.confidence-table th { background: #f1f5f9; }
.analysis-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.chart-tall {
  height: 340px;
}
.rule-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
.rule-item {
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.rule-flow {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #333;
}
.rule-course {
  background: #f2f7ff;
  color: #2563eb;
  border-radius: 6px;
  padding: 3px 8px;
}
.rule-arrow {
  color: #f27a1a;
  font-weight: 700;
}
.rule-metrics {
  margin: 8px 0 4px;
}
.rule-desc {
  font-size: 12px;
  color: #969799;
  line-height: 1.5;
}
.drill-back {
  float: right;
  font-size: 13px;
  color: #2563eb;
  cursor: pointer;
  font-weight: 400;
}
.region-sub {
  font-size: 12px;
  color: #969799;
  margin: -6px 0 6px;
}
.progress-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.progress-card {
  width: 280px;
  background: #fff;
  border-radius: 12px;
  padding: 28px 24px 20px;
  text-align: center;
  box-shadow: 0 4px 20px rgba(0,0,0,0.15);
}
.progress-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 20px;
  color: #333;
}
.progress-task {
  font-size: 14px;
  font-weight: 500;
  color: #1989fa;
  margin-top: 14px;
}
.progress-msg {
  font-size: 12px;
  color: #999;
  margin-top: 6px;
  word-break: break-all;
}
.progress-hint {
  font-size: 11px;
  color: #bbb;
  margin-top: 14px;
}
</style>
