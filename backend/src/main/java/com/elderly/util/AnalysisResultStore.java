package com.elderly.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderly.config.SparkConfig;
import com.elderly.entity.*;
import com.elderly.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** 计算在事务外完成；校验、整表替换及评估记录在一个短事务内提交。 */
@Component
@RequiredArgsConstructor
public class AnalysisResultStore {
    private final AnalysisResultMapper clusterMapper;
    private final AnalysisCourseHotMapper hotMapper;
    private final AnalysisCategoryShareMapper categoryMapper;
    private final AnalysisStudyTrendMapper trendMapper;
    private final AnalysisAssociationRuleMapper ruleMapper;
    private final AnalysisWordFreqMapper wordMapper;
    private final AnalysisRecommendMapper cfMapper;
    private final AnalysisRecommendAlsMapper alsMapper;
    private final AnalysisTextClassifyMapper textMapper;
    private final CourseInfoMapper courseMapper;
    private final JdbcTemplate jdbc;
    private final SparkConfig config;

    @Transactional(rollbackFor = Exception.class)
    public void save(String task, Map<String, List<Map<String, String>>> raw, String runId) {
        List<Map<String, String>> rows = required(raw, task);
        switch (task) {
            case "kmeans":
                rows = new ArrayList<>(rows);
                rows.sort(Comparator.comparingDouble((Map<String, String> r) -> number(r, "score_avg")).reversed());
                final int[] rank = {0};
                replace(clusterMapper, rows, r -> {
                    AnalysisResult a = new AnalysisResult();
                    a.setClusterId(Math.toIntExact(integer(r, "cluster_id")));
                    a.setClusterName(rank[0]++ == 0 ? "相对高活跃组" : "相对低活跃组");
                    a.setUserCount(Math.toIntExact(integer(r, "user_count")));
                    a.setAvgStudyTime(number(r, "avg_study_time"));
                    a.setAvgFinishRate(number(r, "avg_finish_rate"));
                    a.setDescription("按窗口内活跃天数、每天累计分钟、用户课程完成率分组；相对活跃度不等于持续或间断学习。");
                    return a;
                });
                break;
            case "course_hot":
                // 两个输出必须同时存在；任一转换/插入失败会回滚整个事务。
                List<Map<String, String>> categories = required(raw, "course_category");
                replace(hotMapper, rows, r -> {
                    AnalysisCourseHot a = new AnalysisCourseHot();
                    a.setRankNo(Math.toIntExact(integer(r, "rank"))); a.setCourseId(integer(r, "course_id"));
                    a.setTitle(text(r, "title")); a.setStudyCount(integer(r, "study_count")); return a;
                });
                replace(categoryMapper, categories, r -> {
                    AnalysisCategoryShare a = new AnalysisCategoryShare();
                    a.setCategoryName(text(r, "category_name")); a.setStudyCount(integer(r, "study_count"));
                    a.setShareRate(number(r, "share_rate")); return a;
                });
                break;
            case "study_trend":
                replace(trendMapper, rows, r -> {
                    AnalysisStudyTrend a = new AnalysisStudyTrend(); a.setStudyDate(LocalDate.parse(text(r, "study_date")));
                    a.setStudyCount(integer(r, "study_count")); a.setStudyDurationMin(integer(r, "study_duration_min")); return a;
                });
                break;
            case "fp_growth":
                replace(ruleMapper, rows, r -> {
                    AnalysisAssociationRule a = new AnalysisAssociationRule();
                    a.setAntecedentIds(text(r, "antecedent_ids")); a.setConsequentIds(text(r, "consequent_ids"));
                    a.setAntecedentTitles(titles(a.getAntecedentIds())); a.setConsequentTitles(titles(a.getConsequentIds()));
                    a.setConfidence(number(r, "confidence")); a.setSupport(number(r, "support")); a.setLift(number(r, "lift")); return a;
                }); break;
            case "word_freq":
                replace(wordMapper, rows, r -> {
                    AnalysisWordFreq a = new AnalysisWordFreq(); a.setRankNo(Math.toIntExact(integer(r, "rank")));
                    a.setWord(text(r, "word")); a.setFreq(integer(r, "freq")); return a;
                }); break;
            case "item_cf":
                replace(cfMapper, rows, r -> {
                    AnalysisRecommend a = new AnalysisRecommend(); a.setUserId(integer(r, "user_id"));
                    a.setCourseId(integer(r, "course_id")); a.setTitle(text(r, "title")); a.setScore(number(r, "score")); return a;
                }); break;
            case "als_recommend":
                replace(alsMapper, rows, r -> {
                    AnalysisRecommendAls a = new AnalysisRecommendAls(); a.setUserId(integer(r, "user_id"));
                    a.setCourseId(integer(r, "course_id")); a.setTitle(text(r, "title")); a.setScore(number(r, "score")); return a;
                }); break;
            case "text_classify":
                replace(textMapper, rows, r -> {
                    AnalysisTextClassify a = new AnalysisTextClassify(); a.setProvince(text(r, "province"));
                    a.setCategory(text(r, "category")); a.setQuestionCount(integer(r, "question_count"));
                    Double average = optionalProbability(r, "avg_confidence"), minimum = optionalProbability(r, "min_confidence");
                    long high = integer(r, "high_confidence_count");
                    boolean unknown = "未确定业务".equals(a.getCategory());
                    if (a.getQuestionCount() == 0 || high > a.getQuestionCount()
                            || (unknown && (average != null || minimum != null || high != 0))
                            || (!unknown && (average == null || minimum == null || minimum < 0.6 - 1e-9 || average < minimum - 1e-9))) {
                        throw new IllegalArgumentException("分类置信度与分类结果不一致");
                    }
                    a.setAvgConfidence(average); a.setMinConfidence(minimum); a.setHighConfidenceCount(high); return a;
                });
                saveQuestionPredictions(raw, rows, runId);
                break;
            default: throw new IllegalArgumentException("未知任务: " + task);
        }
        List<Map<String, String>> metrics = raw.get(task + "_metrics");
        if (Arrays.asList("kmeans", "item_cf", "als_recommend", "text_classify").contains(task) && (metrics == null || metrics.isEmpty())) {
            throw new IllegalArgumentException("缺少评估输出: " + task);
        }
        jdbc.update("DELETE FROM analysis_model_metric WHERE task = ?", task);
        jdbc.update("INSERT INTO analysis_model_metric(task,run_id,data_scope,metric,value,samples,status,detail) VALUES(?,?,?,?,?,?,?,?)",
                task, runId, config.getDataScope(), "snapshot", null, rows.size(), "completed",
                "最近" + config.getLookbackDays() + "天；热度=课程独立用户；趋势=日活用户；分类占比=用户分类参与次数分布；时长=增量分钟");
        if (metrics != null) for (Map<String, String> r : metrics) {
            jdbc.update("INSERT INTO analysis_model_metric(task,run_id,data_scope,metric,value,samples,status,detail) VALUES(?,?,?,?,?,?,?,?)",
                    task, runId, config.getDataScope(), text(r, "metric"), optionalNumber(r, "value"),
                    integer(r, "samples"), text(r, "status"), text(r, "detail"));
        }
    }

    private void saveQuestionPredictions(Map<String, List<Map<String, String>>> raw,
                                         List<Map<String, String>> aggregate, String runId) {
        // Legacy saved outputs cannot be expanded into per-question predictions.
        List<Map<String, String>> details = raw.getOrDefault("text_classify_detail", Collections.emptyList());
        Set<Long> ids = new HashSet<>();
        Map<String, Long> counts = new HashMap<>(), expected = new HashMap<>();
        for (Map<String, String> row : aggregate) expected.merge(text(row, "category"), integer(row, "question_count"), Long::sum);
        for (Map<String, String> row : details) {
            long id = integer(row, "question_id");
            String category = text(row, "category"), hash = text(row, "question_hash");
            Double confidence = optionalProbability(row, "confidence");
            if (id == 0 || !ids.add(id) || !hash.matches("[0-9a-f]{64}")
                    || ("未确定业务".equals(category) ? confidence != null : confidence == null || confidence < 0.6)) {
                throw new IllegalArgumentException("逐条分类预测不合法或重复");
            }
            counts.merge(category, 1L, Long::sum);
        }
        if (raw.containsKey("text_classify_detail") && !counts.equals(expected)) {
            throw new IllegalArgumentException("逐条预测与分类汇总数量不一致");
        }
        jdbc.update("DELETE FROM analysis_question_prediction");
        for (Map<String, String> row : details) jdbc.update(
                "INSERT INTO analysis_question_prediction(question_id,question_hash,category,confidence,run_id,data_scope) VALUES(?,?,?,?,?,?)",
                integer(row, "question_id"), text(row, "question_hash"), text(row, "category"),
                optionalProbability(row, "confidence"), runId, config.getDataScope());
    }

    private <T> void replace(BaseMapper<T> mapper, List<Map<String, String>> rows, Function<Map<String, String>, T> convert) {
        List<T> entities = new ArrayList<>();
        for (Map<String, String> row : rows) entities.add(convert.apply(row));
        mapper.delete(new QueryWrapper<>());
        for (T entity : entities) mapper.insert(entity);
    }

    private List<Map<String, String>> required(Map<String, List<Map<String, String>>> raw, String key) {
        if (raw == null || !raw.containsKey(key) || raw.get(key) == null) throw new IllegalArgumentException("缺少结果输出: " + key);
        return raw.get(key);
    }
    private String titles(String ids) {
        List<String> names = new ArrayList<>();
        for (String id : ids.split(",")) {
            CourseInfo c = courseMapper.selectById(new BigDecimal(id.trim()).longValueExact());
            names.add(c == null ? "课程" + id : c.getTitle());
        }
        return String.join("、", names);
    }
    static String text(Map<String, String> row, String key) {
        if (row.get(key) == null) throw new IllegalArgumentException("结果缺少字段: " + key);
        return row.get(key).trim();
    }
    static long integer(Map<String, String> row, String key) {
        long value = new BigDecimal(text(row, key)).longValueExact();
        if (value < 0) throw new IllegalArgumentException("结果不能为负: " + key);
        return value;
    }
    static double number(Map<String, String> row, String key) {
        double value;
        try { value = Double.parseDouble(text(row, key)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("分析结果字段不是有效数字: " + key, e); }
        if (!Double.isFinite(value)) throw new IllegalArgumentException("结果包含非有限数: " + key);
        return value;
    }

    private static Double optionalProbability(Map<String, String> row, String key) {
        Double value = optionalNumber(row, key);
        if (value == null) return null;
        if (value < 0 || value > 1) throw new IllegalArgumentException("分类概率必须在0到1之间");
        return value;
    }

    private static Double optionalNumber(Map<String, String> row, String key) {
        String value = text(row, key);
        // 兼容已保存的旧YARN输出：Spark CSV默认把缺失值写成字面量双引号。
        // 仅限可空数值字段，不能剥掉课程标题等文本本身的引号，也不能把缺失值改成0。
        return value.isEmpty() || "\"\"".equals(value) ? null : number(row, key);
    }
}
