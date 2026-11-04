package com.elderly.service.impl;

import com.elderly.common.BizException;
import com.elderly.common.SecurityUtil;
import com.elderly.config.SparkConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** 人工标签只进入独立测试；原文和复核令牌共同防止陈旧页面覆盖新标签。 */
@Service
@RequiredArgsConstructor
public class ClassificationReviewService {
    private final JdbcTemplate jdbc;
    private final SparkConfig config;
    private static final String CATEGORY_SQL =
            "SELECT DISTINCT TRIM(CASE WHEN top_cat.id IS NOT NULL THEN top_cat.category_name ELSE cat.category_name END) AS category "
            + "FROM course_info c JOIN course_category cat ON c.category_id=cat.id "
            + "LEFT JOIN course_category top_cat ON NULLIF(cat.parent_id,0)=top_cat.id WHERE c.status=1";
    private static final String CURRENT_LABEL = "l.question_id IS NOT NULL "
            + "AND SHA2(q.question,256)=SHA2(l.question_text,256) AND LENGTH(TRIM(l.category))>0 "
            // Labels and course categories may have different MySQL collations after migrations.
            // Compare normalized text digests, as with question text, without changing database collation.
            + "AND (TRIM(l.category)='未确定业务' OR SHA2(TRIM(l.category),256) IN "
            + "(SELECT SHA2(category,256) FROM (" + CATEGORY_SQL + ") allowed_categories))";
    private static final String CURRENT_PREDICTION = "p.question_id IS NOT NULL "
            + "AND p.data_scope=CASE WHEN u.is_mock=1 THEN 'mock' ELSE 'real' END "
            + "AND p.question_hash=SHA2(REPLACE(REPLACE(REPLACE(q.question,CHAR(9),' '),CHAR(13),' '),CHAR(10),' '),256)";
    private static final String PREDICTION_GROUP = "CASE WHEN COALESCE((" + CURRENT_PREDICTION
            + "),FALSE) THEN CASE WHEN p.category='未确定业务' THEN 'undetermined' ELSE 'classified' END ELSE 'unprocessed' END";

    public List<String> categories() {
        List<String> result = jdbc.queryForList(CATEGORY_SQL, String.class).stream()
                .filter(Objects::nonNull).map(String::trim).filter(c -> !c.isEmpty())
                .filter(c -> !"未确定业务".equals(c)).distinct().sorted().collect(Collectors.toList());
        result.add("未确定业务");
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> page(int page, int size, boolean onlyPending) {
        return page(page, size, onlyPending, "all");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> page(int page, int size, boolean onlyPending, String predictionGroup) {
        if (page < 1 || size < 1 || size > 100) throw new BizException("分页参数不合法");
        if (!Arrays.asList("all", "classified", "undetermined", "unprocessed").contains(predictionGroup)) throw new BizException("预测分组不合法");
        List<Object> params = scopeParams();
        String from = " FROM ai_question_record q JOIN sys_user u ON q.user_id=u.id "
                + "LEFT JOIN analysis_question_label l ON q.id=l.question_id "
                + "LEFT JOIN analysis_question_prediction p ON p.question_id=q.id ";
        String where = scopeWhere() + (onlyPending ? " AND NOT COALESCE((" + CURRENT_LABEL + "),FALSE)" : "");
        List<Map<String,Object>> countRows = jdbc.queryForList("SELECT " + PREDICTION_GROUP + " AS predictionGroup,COUNT(*) AS cnt"
                + from + where + " GROUP BY " + PREDICTION_GROUP, params.toArray());
        Map<String,Long> counts = new LinkedHashMap<>();
        for (String key : Arrays.asList("classified", "undetermined", "unprocessed")) counts.put(key,0L);
        for (Map<String,Object> row : countRows) counts.put((String)row.get("predictionGroup"), ((Number)row.get("cnt")).longValue());
        if (!"all".equals(predictionGroup)) { where += " AND (" + PREDICTION_GROUP + ")=?"; params.add(predictionGroup); }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, params.toArray());
        params.add(size); params.add(Math.multiplyExact((long) page - 1, size));
        // ID返回字符串，避免浏览器把超过2^53的BIGINT舍入；令牌在过期标签上也必须保留。
        List<Map<String, Object>> records = jdbc.queryForList(
                "SELECT CONCAT('',q.id) AS id,q.question,u.province,CASE WHEN " + CURRENT_LABEL
                + " THEN TRIM(l.category) ELSE NULL END AS category,l.reviewed_at AS reviewedAt,l.review_token AS reviewToken,"
                + PREDICTION_GROUP + " AS predictionGroup,CASE WHEN " + CURRENT_PREDICTION + " THEN p.category END AS sparkCategory,"
                + "CASE WHEN " + CURRENT_PREDICTION + " THEN p.confidence END AS sparkConfidence,"
                + "CASE WHEN " + CURRENT_PREDICTION + " THEN p.run_id END AS sparkRunId"
                + from + where + " ORDER BY MD5(CAST(q.id AS CHAR)),q.id LIMIT ? OFFSET ?", params.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records); result.put("total", total); result.put("categories", categories());
        result.put("dataScope", config.getDataScope()); result.put("lookbackDays", config.getLookbackDays());
        result.put("groupCounts",counts);
        return result;
    }

    @Transactional
    public void save(long questionId, String category, String expectedQuestion, String expectedReviewToken) {
        Long reviewer = SecurityUtil.getCurrentUserId();
        if (reviewer == null) throw new BizException("请先登录");
        String question = lockQuestion(questionId, expectedQuestion);
        String normalizedCategory = category == null ? null : category.trim();
        if (!categories().contains(normalizedCategory)) throw new BizException("请选择有效的业务类别");
        checkReviewToken(questionId, expectedReviewToken);
        jdbc.update("INSERT INTO analysis_question_label(question_id,question_text,category,reviewer_id,reviewed_at,review_token) "
                + "VALUES(?,?,?,?,CURRENT_TIMESTAMP,?) ON DUPLICATE KEY UPDATE "
                + "question_text=VALUES(question_text),category=VALUES(category),reviewer_id=VALUES(reviewer_id),"
                + "reviewed_at=CURRENT_TIMESTAMP,review_token=VALUES(review_token)",
                questionId, question, normalizedCategory, reviewer, UUID.randomUUID().toString());
    }

    @Transactional
    public void remove(long questionId, String expectedQuestion, String expectedReviewToken) {
        if (SecurityUtil.getCurrentUserId() == null) throw new BizException("请先登录");
        lockQuestion(questionId, expectedQuestion);
        checkReviewToken(questionId, expectedReviewToken);
        jdbc.update("DELETE FROM analysis_question_label WHERE question_id=?", questionId);
    }

    /** 同一题目的写操作先锁定提问行，串行检查版本；不依赖秒级标注时间来判断并发。 */
    private String lockQuestion(long questionId, String expectedQuestion) {
        if (questionId <= 0 || expectedQuestion == null) throw new BizException("缺少题目原文，请刷新复核页面后重试");
        List<Object> params = scopeParams(); params.add(questionId);
        List<String> questions = jdbc.queryForList("SELECT q.question FROM ai_question_record q JOIN sys_user u ON q.user_id=u.id"
                + scopeWhere() + " AND q.id=? FOR UPDATE", String.class, params.toArray());
        if (questions.isEmpty()) throw new BizException("提问不存在或不属于当前分析来源及日期窗口");
        if (!Objects.equals(questions.get(0), expectedQuestion)) throw new BizException("题目原文已变化，请刷新后重新标注");
        return questions.get(0);
    }

    private void checkReviewToken(long questionId, String expected) {
        List<String> tokens = jdbc.queryForList("SELECT review_token FROM analysis_question_label WHERE question_id=? FOR UPDATE", String.class, questionId);
        String current = tokens.isEmpty() ? null : tokens.get(0);
        if (!Objects.equals(current, expected)) throw new BizException("标签已被其他操作修改，请刷新后重新确认");
    }

    private List<Object> scopeParams() {
        if (!Arrays.asList("real", "mock").contains(config.getDataScope())) throw new BizException("分析来源配置不合法");
        if (config.getLookbackDays() < 1 || config.getLookbackDays() > 3650) throw new BizException("分析日期窗口配置不合法");
        LocalDate today = LocalDate.now();
        return new ArrayList<>(Arrays.asList("mock".equals(config.getDataScope()) ? 1 : 0,
                today.minusDays(config.getLookbackDays() - 1).atStartOfDay(), today.plusDays(1).atStartOfDay()));
    }

    private String scopeWhere() {
        return " WHERE u.status=1 AND u.is_mock=? AND q.create_time>=? AND q.create_time<? "
                + "AND q.question IS NOT NULL AND TRIM(q.question)<>''";
    }
}
