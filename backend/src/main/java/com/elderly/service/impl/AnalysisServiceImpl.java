package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderly.common.BizException;
import com.elderly.entity.AnalysisAssociationRule;
import com.elderly.entity.AnalysisCategoryShare;
import com.elderly.entity.AnalysisCourseHot;
import com.elderly.entity.AnalysisRecommend;
import com.elderly.entity.AnalysisRecommendAls;
import com.elderly.entity.AnalysisResult;
import com.elderly.entity.AnalysisStudyTrend;
import com.elderly.entity.AnalysisTextClassify;
import com.elderly.entity.AnalysisWordFreq;
import com.elderly.entity.CourseInfo;
import com.elderly.mapper.AnalysisAssociationRuleMapper;
import com.elderly.mapper.AnalysisCategoryShareMapper;
import com.elderly.mapper.AnalysisCourseHotMapper;
import com.elderly.mapper.AnalysisRecommendAlsMapper;
import com.elderly.mapper.AnalysisRecommendMapper;
import com.elderly.mapper.AnalysisResultMapper;
import com.elderly.mapper.AnalysisStudyTrendMapper;
import com.elderly.mapper.AnalysisTextClassifyMapper;
import com.elderly.mapper.AnalysisWordFreqMapper;
import com.elderly.service.AnalysisService;
import com.elderly.util.TaskRunner;
import com.elderly.util.AnalysisResultStore;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学情分析服务实现：调用 TaskRunner（local 内嵌 / YARN 集群），解析结果落库。
 * 计算使用不可变输入快照；每个任务的结果和评估记录由 AnalysisResultStore 原子替换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private final TaskRunner taskRunner;
    private final AnalysisResultMapper analysisResultMapper;
    private final AnalysisCourseHotMapper courseHotMapper;
    private final AnalysisCategoryShareMapper categoryShareMapper;
    private final AnalysisStudyTrendMapper trendMapper;
    private final AnalysisAssociationRuleMapper ruleMapper;
    private final AnalysisWordFreqMapper wordFreqMapper;
    private final AnalysisRecommendMapper recommendMapper;
    private final AnalysisRecommendAlsMapper recommendAlsMapper;
    private final AnalysisTextClassifyMapper textClassifyMapper;
    private final HybridRecommendationService hybridRecommendationService;
    private final AnalysisResultStore resultStore;
    private final JdbcTemplate jdbc;
    private final com.elderly.config.SparkConfig sparkConfig;


    private final Map<String, Object> analysisProgress = new ConcurrentHashMap<>();

    // ---------------- K-Means 聚类 ----------------

    @Override
    public synchronized List<AnalysisResult> runCluster() {
        execute("kmeans");
        return getLatestResult();
    }


    @Override
    public List<AnalysisResult> getLatestResult() {
        return analysisResultMapper.selectList(new QueryWrapper<AnalysisResult>().orderByAsc("id"));
    }

    // ---------------- 课程热度 + 分类占比 ----------------

    @Override
    public synchronized List<AnalysisCourseHot> runCourseHot() {
        execute("course_hot");
        return getCourseHot();
    }


    @Override
    public List<AnalysisCourseHot> getCourseHot() {
        return courseHotMapper.selectList(new QueryWrapper<AnalysisCourseHot>().orderByAsc("rank_no"));
    }

    @Override
    public List<AnalysisCategoryShare> getCategoryShare() {
        return categoryShareMapper.selectList(new QueryWrapper<AnalysisCategoryShare>().orderByDesc("study_count"));
    }

    // ---------------- 学习趋势 ----------------

    @Override
    public synchronized List<AnalysisStudyTrend> runStudyTrend() {
        execute("study_trend");
        return getStudyTrend();
    }

    @Override
    public List<AnalysisStudyTrend> getStudyTrend() {
        return trendMapper.selectList(new QueryWrapper<AnalysisStudyTrend>().orderByAsc("study_date"));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Map<String, Object> getStudyTrendSnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rows", getStudyTrend());
        List<Map<String, Object>> metadata = jdbc.queryForList(
                "SELECT run_id AS runId,data_scope AS dataScope,create_time AS createTime "
                + "FROM analysis_model_metric WHERE task='study_trend' AND metric='snapshot' ORDER BY id DESC LIMIT 1");
        Map<String, Object> info = metadata.isEmpty() ? null : metadata.get(0);
        if (info != null) {
            // Dates come from the saved input snapshot, never today's configuration or the last active day.
            try {
                String runId = java.util.UUID.fromString(String.valueOf(info.get("runId"))).toString();
                java.nio.file.Path file = java.nio.file.Paths.get(sparkConfig.getExportDir(), "runs", runId, "snapshot.properties");
                java.util.Properties properties = new java.util.Properties();
                try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                info.put("fromDate", LocalDate.parse(properties.getProperty("fromDate")).toString());
                info.put("toDate", LocalDate.parse(properties.getProperty("toDate")).toString());
            } catch (Exception e) {
                log.debug("趋势批次没有可用日期窗口，缺失日期保留未知: {}", info.get("runId"));
            }
        }
        result.put("metadata", info);
        result.put("today", LocalDate.now().toString());
        return result;
    }

    // ---------------- 课程关联规则 ----------------

    @Override
    public synchronized List<AnalysisAssociationRule> runAssociation() {
        execute("fp_growth");
        return getAssociation();
    }

    @Override
    public List<AnalysisAssociationRule> getAssociation() {
        return ruleMapper.selectList(new QueryWrapper<AnalysisAssociationRule>().orderByDesc("lift").orderByDesc("support"));
    }


    // ---------------- 热点词频 ----------------

    @Override
    public synchronized List<AnalysisWordFreq> runWordFreq() {
        execute("word_freq");
        return getWordFreq();
    }

    @Override
    public List<AnalysisWordFreq> getWordFreq() {
        return wordFreqMapper.selectList(new QueryWrapper<AnalysisWordFreq>().orderByAsc("rank_no"));
    }

    // ---------------- 个性化推荐 ----------------

    @Override
    public synchronized List<AnalysisRecommend> runRecommend() {
        execute("item_cf");
        return recommendMapper.selectList(new QueryWrapper<AnalysisRecommend>().orderByAsc("id"));
    }

    // ---------------- ALS 矩阵分解个性化推荐（用户-课程协同过滤） ----------------

    @Override
    public synchronized List<AnalysisRecommendAls> runAlsRecommend() {
        execute("als_recommend");
        return getAlsRecommend();
    }

    @Override
    public List<AnalysisRecommendAls> getAlsRecommend() {
        return recommendAlsMapper.selectList(new QueryWrapper<AnalysisRecommendAls>().orderByAsc("id"));
    }

    // ---------------- 问答文本分类（朴素贝叶斯：地区 × 业务双维度） ----------------

    @Override
    public synchronized List<AnalysisTextClassify> runTextClassify() {
        execute("text_classify");
        return getTextClassify();
    }

    @Override
    public List<AnalysisTextClassify> getTextClassify() {
        return textClassifyMapper.selectList(
                new QueryWrapper<AnalysisTextClassify>().orderByAsc("province").orderByDesc("question_count"));
    }

    @Override
    public List<CourseInfo> recommendCourses(Long userId) {
        return hybridRecommendationService.recommend(userId);
    }

    // ---------------- 一键全跑 ----------------

    @Override
    public synchronized List<Map<String, Object>> runAll() {
        return taskRunner.withSnapshot(this::runBatch);
    }

    private List<Map<String, Object>> runBatch() {
        List<AnalysisTaskType> tasks = Arrays.asList(AnalysisTaskType.values());
        // 重置进度
        analysisProgress.put("running", true);
        analysisProgress.put("total", tasks.size());
        analysisProgress.put("current", 0);
        analysisProgress.put("taskName", "准备中…");
        analysisProgress.put("message", "");
        analysisProgress.put("runId", taskRunner.snapshotId());

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            String t = tasks.get(i).key;
            analysisProgress.put("current", i);
            analysisProgress.put("taskName", tasks.get(i).title);

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("task", t);
            r.put("runId", taskRunner.snapshotId());
            long start = System.currentTimeMillis();
            try {
                runOne(t);
                r.put("success", true);
                String elapsed = "成功，耗时 " + (System.currentTimeMillis() - start) / 1000 + " 秒";
                r.put("message", elapsed);
                analysisProgress.put("message", tasks.get(i).title + " " + elapsed);
            } catch (Exception e) {
                log.error("分析任务 {} 失败", t, e);
                r.put("success", false);
                r.put("message", e.getMessage());
                analysisProgress.put("message", tasks.get(i).title + " 失败: " + e.getMessage());
            }
            results.add(r);
        }
        analysisProgress.put("running", false);
        analysisProgress.put("current", tasks.size());
        long failures = results.stream().filter(r -> Boolean.FALSE.equals(r.get("success"))).count();
        analysisProgress.put("taskName", failures == 0 ? "全部成功" : "存在失败任务");
        analysisProgress.put("message", "成功 " + (tasks.size() - failures) + " 个，失败 " + failures + " 个；失败任务保留原结果");
        return results;
    }

    @Override
    public Map<String, Object> getProgress() {
        Map<String, Object> snapshot = new HashMap<>(analysisProgress);
        snapshot.putIfAbsent("running", false);
        snapshot.putIfAbsent("total", AnalysisTaskType.values().length);
        snapshot.putIfAbsent("current", 0);
        snapshot.putIfAbsent("taskName", "");
        snapshot.putIfAbsent("message", "");
        return snapshot;
    }

    @Override
    public synchronized Map<String, Object> runSingle(String task) {
        return taskRunner.withSnapshot(() -> runSingleInSnapshot(task));
    }

    private Map<String, Object> runSingleInSnapshot(String task) {
        boolean valid = Arrays.stream(AnalysisTaskType.values()).anyMatch(type -> type.key.equals(task));
        if (!valid) throw new BizException("未知分析任务: " + task);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("task", task);
        r.put("runId", taskRunner.snapshotId());
        long start = System.currentTimeMillis();
        try {
            runOne(task);
            r.put("success", true);
            r.put("elapsedSeconds", (System.currentTimeMillis() - start) / 1000);
        } catch (Exception e) {
            log.error("分析任务 {} 单独运行失败", task, e);
            r.put("success", false);
            r.put("message", e.getMessage());
        }
        return r;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<Map<String, Object>> getTaskCatalog() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AnalysisTaskType meta : AnalysisTaskType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("task", meta.key);
            item.put("taskName", meta.title);
            item.put("description", meta.description);

            Object resultCount = 0;
            Object lastRunTime = null;
            @SuppressWarnings("rawtypes")
            BaseMapper mapper = mapperOf(meta.key);
            if (mapper != null) {
                List<Map<String, Object>> stat = mapper.selectMaps(
                        new QueryWrapper<>().select("COUNT(*) AS cnt, MAX(create_time) AS last_time"));
                if (stat != null && !stat.isEmpty()) {
                    resultCount = stat.get(0).get("cnt");
                    lastRunTime = stat.get(0).get("last_time");
                }
            }
            item.put("resultCount", resultCount);
            item.put("lastRunTime", lastRunTime);
            List<Map<String, Object>> metrics = jdbc.queryForList(
                    "SELECT run_id AS runId,data_scope AS dataScope,metric,value,samples,status,detail,create_time AS createTime FROM analysis_model_metric WHERE task = ? ORDER BY id", meta.key);
            item.put("metrics", metrics);
            if (!metrics.isEmpty()) item.put("lastRunTime", metrics.get(0).get("createTime"));
            list.add(item);
        }
        return list;
    }

    @SuppressWarnings("rawtypes")
    private BaseMapper mapperOf(String task) {
        switch (AnalysisTaskType.fromKey(task)) {
            case KMEANS: return analysisResultMapper;
            case COURSE_HOT: return courseHotMapper;
            case STUDY_TREND: return trendMapper;
            case FP_GROWTH: return ruleMapper;
            case WORD_FREQ: return wordFreqMapper;
            case ITEM_CF: return recommendMapper;
            case ALS_RECOMMEND: return recommendAlsMapper;
            case TEXT_CLASSIFY: return textClassifyMapper;
            default: return null;
        }
    }

    private void runOne(String task) {
        switch (AnalysisTaskType.fromKey(task)) {
            case KMEANS:
                runCluster();
                break;
            case COURSE_HOT:
                runCourseHot();
                break;
            case STUDY_TREND:
                runStudyTrend();
                break;
            case FP_GROWTH:
                runAssociation();
                break;
            case WORD_FREQ:
                runWordFreq();
                break;
            case ITEM_CF:
                runRecommend();
                break;
            case ALS_RECOMMEND:
                runAlsRecommend();
                break;
            case TEXT_CLASSIFY:
                runTextClassify();
                break;
            default:
                throw new IllegalArgumentException("未知任务: " + task);
        }
    }

    private void execute(String task) {
        if (taskRunner.snapshotId().isEmpty()) {
            taskRunner.withSnapshot(() -> { execute(task); return null; });
            return;
        }
        resultStore.save(task, taskRunner.runTask(task), taskRunner.snapshotId());
    }

}
