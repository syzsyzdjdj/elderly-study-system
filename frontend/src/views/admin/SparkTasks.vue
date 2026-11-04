<template>
  <div class="admin-page spark-page">
    <h3 class="page-title">⚡ Spark 分析任务管控</h3>

    <div class="analysis-actions">
      <van-button type="primary" size="small" round :loading="runningAll" @click="runAll">
        ▶ 一键运行全部 8 个任务
      </van-button>
      <van-button size="small" round plain icon="replay" @click="loadTasks">刷新状态</van-button>
      <span class="mode-hint">按服务器配置执行 · 全批次共用数据快照 · 默认最近 90 天真实数据，模拟数据单独分析</span>
    </div>

    <div class="task-table-wrap">
      <table class="task-table">
        <thead>
          <tr>
            <th style="width:42px">#</th>
            <th style="width:150px">任务</th>
            <th style="width:130px">任务标识</th>
            <th>算法说明与评估</th>
            <th style="width:80px">结果条数</th>
            <th style="width:150px">最近运行时间</th>
            <th style="width:80px">状态</th>
            <th style="width:90px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(t, i) in tasks" :key="t.task">
            <td>{{ i + 1 }}</td>
            <td class="t-name">{{ t.taskName }}</td>
            <td><van-tag plain type="primary">{{ t.task }}</van-tag></td>
            <td class="t-desc">
              <div>{{ t.description }}</div>
              <div v-for="m in (t.metrics || [])" :key="m.metric" class="metric-line">
                <span>{{ metricName(m.metric) }}：{{ m.value == null ? '—' : Number(m.value).toFixed(4) }}</span>
                <van-tag plain :type="m.status === 'evaluated' ? 'success' : 'warning'">{{ metricStatus(m.status) }}</van-tag>
                <div>{{ m.detail }}</div>
              </div>
              <div v-if="t.metrics?.length" class="metric-line">
                来源：{{ t.metrics[0].dataScope === 'mock' ? '模拟数据（不可作为真实结论）' : '真实数据' }}
                · 批次 {{ t.metrics[0].runId }}
              </div>
            </td>
            <td>{{ Number(t.resultCount) || 0 }}</td>
            <td>{{ formatTime(t.lastRunTime) }}</td>
            <td>
              <van-tag :type="Number(t.resultCount) > 0 ? 'success' : 'default'">
                {{ t.metrics?.length ? '已执行' : Number(t.resultCount) > 0 ? '旧版结果' : '未运行' }}
              </van-tag>
            </td>
            <td>
              <van-button
                size="mini"
                type="primary"
                plain
                :loading="runningTask === t.task"
                @click="runOne(t)"
              >运行</van-button>
            </td>
          </tr>
        </tbody>
      </table>
      <van-empty v-if="loaded && !tasks.length" description="任务目录加载失败，点击上方刷新重试" image-size="90" />
    </div>

    <!-- 全部任务执行进度 -->
    <van-overlay :show="runningAll" z-index="2000">
      <div class="progress-overlay">
        <div class="progress-card">
          <div class="progress-title">正在执行 Spark 分析任务…</div>
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
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { getTaskList, runAnalysisTask, runAllAnalyses, getAnalysisProgress } from '../../api/analysis'

const tasks = ref([])
const loaded = ref(false)
const runningTask = ref('')
const runningAll = ref(false)
const progressPercent = ref(0)
const progressTaskName = ref('')
const progressMessage = ref('')
let progressTimer = null

function formatTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 19)
}

const metricName = (key) => ({ snapshot: '输入快照', silhouette: '轮廓系数', recall_at_5: 'Recall@5',
  precision_at_5: 'Precision@5', user_coverage: '用户覆盖率', popular_baseline_recall_at_5: '热门基线 Recall@5',
  recall_gain_over_popular: '相对热门基线提升', accuracy: '人工测试准确率', undetermined_rate: '未确定业务比例',
  mean_confidence: '平均模型置信度（非准确率）', labeled_test_count: '有效人工测试样本数', macro_f1: '人工测试宏平均F1',
  classification_coverage: '人工测试分类覆盖率', accepted_accuracy: '已接受分类的测试准确率',
  accuracy_ci95_lower: '准确率95%区间下界', accuracy_ci95_upper: '准确率95%区间上界' }[key] || key)
const metricStatus = (status) => ({ evaluated: '已评估', unvalidated: '未验证', insufficient_data: '样本不足',
  completed: '已完成', descriptive: '描述性统计' }[status] || status)

const loadTasks = async () => {
  try {
    const res = await getTaskList()
    tasks.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
  loaded.value = true
}

// 单独运行一个任务
const runOne = async (t) => {
  try {
    await showConfirmDialog({ title: `运行「${t.taskName}」`, message: '将导出独立快照并执行分析任务，可能需要数分钟，确认执行？' })
  } catch {
    return
  }
  runningTask.value = t.task
  try {
    const res = await runAnalysisTask(t.task)
    const d = res.data || {}
    if (d.success) {
      showToast(`「${t.taskName}」执行成功，耗时 ${d.elapsedSeconds ?? '?'} 秒`)
      await loadTasks()
    } else {
      showToast(`「${t.taskName}」执行失败：${d.message || '未知错误'}`)
    }
  } catch (e) { /* 拦截器已提示错误原因 */ } finally {
    runningTask.value = ''
  }
}

// 一键运行全部（轮询后端进度）
const runAll = async () => {
  if (runningAll.value) return
  runningAll.value = true
  progressPercent.value = 0
  progressTaskName.value = '准备中…'
  progressMessage.value = ''

  const poll = async () => {
    try {
      const res = await getAnalysisProgress()
      const p = res.data || {}
      if (p.current !== undefined && p.total) {
        progressPercent.value = Math.round((p.current / p.total) * 100)
      }
      if (p.taskName) progressTaskName.value = p.taskName
      if (p.message) progressMessage.value = p.message
    } catch (e) { /* 静默失败 */ }
  }
  progressTimer = setInterval(poll, 2000)
  poll()

  try {
    const res = await runAllAnalyses()
    const results = res.data || []
    const ok = results.filter(r => r.success).length
    const fail = results.filter(r => !r.success)
    progressPercent.value = 100
    progressTaskName.value = fail.length ? '存在失败任务' : '全部成功'
    progressMessage.value = ''
    showToast(`执行完成：成功 ${ok} / ${results.length}${fail.length ? '，失败任务请查看后端日志' : ''}`)
    await loadTasks()
  } catch (e) {
    showToast('分析任务执行失败，请查看后端日志')
  } finally {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
    runningAll.value = false
  }
}

onMounted(loadTasks)
onUnmounted(() => {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
})
</script>

<style scoped>
.analysis-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  margin-bottom: 12px;
}
.mode-hint {
  font-size: 12px;
  color: #999;
}
.task-table-wrap {
  background: #fff;
  border-radius: 8px;
  overflow-x: auto;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.task-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.task-table th,
.task-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
  vertical-align: middle;
}
.task-table th {
  background: #f7f8fa;
  color: #646566;
  font-weight: 600;
  white-space: nowrap;
}
.task-table tbody tr:hover {
  background: #f5f9ff;
}
.t-name {
  font-weight: 600;
  color: #323233;
  white-space: nowrap;
}
.t-desc {
  color: #646566;
  line-height: 1.5;
}
.metric-line { margin-top: 6px; font-size: 12px; overflow-wrap: anywhere; }
.progress-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
.progress-card {
  width: 300px;
  background: #fff;
  border-radius: 12px;
  padding: 24px 20px;
  text-align: center;
}
.progress-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #323233;
}
.progress-task {
  margin-top: 14px;
  font-size: 14px;
  color: #1989fa;
  font-weight: 600;
}
.progress-msg {
  margin-top: 6px;
  font-size: 12px;
  color: #969799;
  min-height: 18px;
}
.progress-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #c8c9cc;
}
</style>
