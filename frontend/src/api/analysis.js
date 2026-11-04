import request from '../utils/request'

// Spark 任务 / 模拟数据生成耗时较长（10 万条日志下全量分析约数分钟），
// 单独放宽超时到 10 分钟，覆盖全局 20s 默认值
const LONG_TIMEOUT = 10 * 60 * 1000

// 学习人群聚类分析（触发 Spark K-Means 并返回结果）
export function getStudyCluster(params) {
  return request.get('/analysis/studyCluster', { params, timeout: LONG_TIMEOUT })
}

// 分析结果（只读，不触发分析）
export function getAnalysisResult(params) {
  return request.get('/analysis/result', { params })
}

// 课程热度排行（只读）
export function getCourseHot(params) {
  return request.get('/analysis/courseHot', { params })
}

// 课程分类占比（只读）
export function getCategoryShare(params) {
  return request.get('/analysis/categoryShare', { params })
}

// 学习趋势（只读）
export function getStudyTrend(params) {
  return request.get('/analysis/studyTrend', { params })
}

export function getStudyTrendSnapshot() {
  return request.get('/analysis/studyTrendSnapshot')
}

// 课程关联规则（只读）
export function getAssociation(params) {
  return request.get('/analysis/association', { params })
}

// 问答热点词频（只读）
export function getWordFreq(params) {
  return request.get('/analysis/wordFreq', { params })
}

// 问答文本分类结果（朴素贝叶斯：地区 × 业务双维度，只读）
export function getTextClassify(params) {
  return request.get('/analysis/textClassify', { params })
}

export function getClassificationReviews(params) {
  return request.get('/analysis/classification-review', { params })
}

export function saveClassificationLabel(id, category, question, reviewToken) {
  return request.post(`/analysis/classification-review/${id}`, { category, question, reviewToken })
}

export function deleteClassificationLabel(id, question, reviewToken) {
  return request.delete(`/analysis/classification-review/${id}`, { data: { question, reviewToken } })
}

// 一键运行全部 6 个 Spark 分析任务（local 内嵌 / YARN 集群，由后端 spark.mode 决定）
export function runAllAnalyses() {
  return request.post('/analysis/runAll', null, { timeout: 8 * LONG_TIMEOUT })
}

// 一键分析进度轮询
export function getAnalysisProgress() {
  return request.get('/analysis/progress', { silent: true })
}

// Spark 任务目录（8 个任务元数据 + 结果条数/最近运行时间）
export function getTaskList() {
  return request.get('/analysis/tasks', { silent: true })
}

// 单独触发一个 Spark 分析任务
export function runAnalysisTask(task) {
  return request.post(`/analysis/run/${task}`, null, { timeout: LONG_TIMEOUT })
}

// 生成模拟数据（仅管理员）
export function genMockData(params) {
  return request.post('/analysis/genData', null, { params, timeout: LONG_TIMEOUT })
}

// 清理模拟数据（仅管理员）
export function clearMockData() {
  return request.post('/analysis/clearMockData', null, { timeout: LONG_TIMEOUT })
}

// 分地区学情统计（省级汇总；传 province 附带市级下钻）
export function getRegionStats(params) {
  return request.get('/analysis/regionStats', { params })
}
