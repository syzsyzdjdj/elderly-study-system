package com.elderly.service.impl;

import com.elderly.config.RecommendationConfig;
import com.elderly.entity.CourseInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 在线融合内容候选和 Spark 候选；独立于离线模型评估，不把补位结果写回模型表。 */
@Service
@RequiredArgsConstructor
public class HybridRecommendationService {
    private final JdbcTemplate jdbc;
    private final RecommendationConfig config;
    private final MockRecommendationSnapshots mockSnapshots;
    private static final Pattern TITLE_PART = Pattern.compile("[\\p{IsHan}]+|[a-z0-9]+");
    private static final Pattern BOILERPLATE = Pattern.compile("课程|入门|基础|教学|学习|教程|使用|操作|方法|指南|老年|视频|如何");

    @Transactional(readOnly = true)
    public List<CourseInfo> recommend(Long userId) {
        boolean mock = userId != null && Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE id=? AND is_mock=1", Integer.class, userId));
        return recommend(userId, mock, mock ? mockSnapshots.latest() : Optional.empty());
    }

    private List<CourseInfo> recommend(Long userId, boolean mock, Optional<MockRecommendationSnapshots.Snapshot> snapshot) {
        int limit = Math.max(1, Math.min(20, config.getLimit()));
        // 下架课仍可作为兴趣种子，只有上架课能进入返回候选。
        List<CourseInfo> catalog = jdbc.query("SELECT c.id,c.category_id,c.title,c.cover,c.intro,c.duration,"
                        + "c.points_reward,c.status,c.province,c.city,c.district,c.create_time,cat.category_name "
                        + "FROM course_info c LEFT JOIN course_category cat ON cat.id=c.category_id",
                BeanPropertyRowMapper.newInstance(CourseInfo.class));
        Map<Long, CourseInfo> online = catalog.stream().filter(c -> Integer.valueOf(1).equals(c.getStatus()))
                .collect(Collectors.toMap(CourseInfo::getId, c -> c));
        if (online.isEmpty()) return Collections.emptyList();

        Map<Long, Double> history = userId == null ? Collections.emptyMap() : history(userId);
        Set<Long> seen = history.keySet();
        Map<Long, Candidate> candidates = new HashMap<>();
        Map<Long, Long> parents = new HashMap<>();
        jdbc.query("SELECT id,parent_id FROM course_category", rs -> {
            parents.put(rs.getLong("id"), rs.getObject("parent_id") == null ? 0L : rs.getLong("parent_id"));
        });
        List<CourseInfo> seeds = catalog.stream().filter(c -> history.getOrDefault(c.getId(), 0.0) > 0)
                .sorted(Comparator.comparing(CourseInfo::getId)).toList();
        Map<Long, Set<String>> tokens = new HashMap<>();
        catalog.forEach(c -> tokens.put(c.getId(), titleTokens(c.getTitle())));
        for (CourseInfo course : online.values()) {
            if (seen.contains(course.getId())) continue;
            double best = 0;
            String reason = null;
            for (CourseInfo seed : seeds) {
                boolean sameCategory = course.getCategoryId() != null && course.getCategoryId().equals(seed.getCategoryId());
                Long root = root(course.getCategoryId(), parents);
                boolean sameRoot = root != null && root.equals(root(seed.getCategoryId(), parents));
                double title = jaccard(tokens.get(course.getId()), tokens.get(seed.getId()));
                // 弱标题重叠不独立生成候选；同分类始终可补上协同过滤的冷启动缺口。
                if (!sameCategory && !sameRoot && title < 0.15) continue;
                double score = ((sameCategory ? 0.7 : sameRoot ? 0.35 : 0) + 0.3 * title) * history.get(seed.getId());
                if (score > best) {
                    best = score;
                    reason = "与你学过的《" + seed.getTitle() + "》" + (sameCategory ? "分类相同" : sameRoot ? "属于同一大类" : "标题相关");
                }
            }
            if (best > 0) {
                Candidate c = new Candidate(course); c.contentScore = best;
                c.sources.add("content"); c.reasons.add(reason); candidates.put(course.getId(), c);
            }
        }
        List<Candidate> content = candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.contentScore).reversed().thenComparing(c -> c.course.getId())).toList();
        for (int i = 0; i < content.size(); i++) content.get(i).score += 0.6 / (11 + i);

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(Math.max(1, config.getLookbackDays()) - 1L);
        if (userId != null && seeds.size() >= Math.max(1, config.getMinUserCourses()) && enoughBehavior(from, today, mock)) {
            if (mock) {
                snapshot.ifPresent(s -> {
                    addRanked(s.als().getOrDefault(userId, List.of()), "als", "结合模拟学习偏好排序", 0.25, online, seen, candidates);
                    addRanked(s.itemCf().getOrDefault(userId, List.of()), "item_cf", "模拟共学关联推荐", 0.15, online, seen, candidates);
                });
            } else {
                // 只读真实数据批次；过期、模拟和没有快照元数据的旧版结果不进入线上排序。
                addCollaborative("als_recommend", "analysis_recommend_als", "als", "结合相似学习偏好排序", 0.25,
                        userId, online, seen, candidates);
                addCollaborative("item_cf", "analysis_recommend", "item_cf", "学过相关课程的学员也在学习", 0.15,
                        userId, online, seen, candidates);
            }
        }
        List<CourseInfo> result = candidates.values().stream()
                .sorted(Comparator.comparingDouble((Candidate c) -> c.score).reversed().thenComparing(c -> c.course.getId()))
                .limit(limit).map(Candidate::result).collect(Collectors.toCollection(ArrayList::new));
        Set<Long> selected = result.stream().map(CourseInfo::getId).collect(Collectors.toSet());
        if (result.size() < limit) {
            // 按当前学员所属来源的启用用户去重计热度，真实/模拟人群严格分开。
            jdbc.query("SELECT l.course_id,COUNT(DISTINCT l.user_id) AS learners FROM study_log l "
                            + "JOIN sys_user u ON u.id=l.user_id WHERE u.status=1 AND u.is_mock=? "
                            + "AND l.study_date>=? AND l.study_date<=? GROUP BY l.course_id "
                            + "ORDER BY learners DESC,l.course_id ASC", rs -> {
                        Long id = rs.getLong("course_id");
                        if (result.size() < limit && online.containsKey(id) && !seen.contains(id) && selected.add(id)) {
                            CourseInfo c = online.get(id);
                            c.setRecommendationSources(List.of("popular"));
                            c.setRecommendationReason("热门补充：最近" + Math.max(1, config.getLookbackDays()) + "天有"
                                    + rs.getLong("learners") + "位学员学习");
                            result.add(c);
                        }
                    }, mock ? 1 : 0, from, today);
        }
        // 无历史的新用户或热门课不够时，明示最新课程补位，绝不伪装成个性化/热门结果。
        online.values().stream().filter(c -> !seen.contains(c.getId()) && !selected.contains(c.getId()))
                .sorted(Comparator.comparing(CourseInfo::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CourseInfo::getId, Comparator.reverseOrder()))
                .limit(Math.max(0, limit - result.size())).forEach(c -> {
                    c.setRecommendationSources(List.of("latest")); c.setRecommendationReason("最新课程补充，供你探索新内容"); result.add(c);
                });
        result.forEach(c -> c.setRecommendationDataScope(mock ? "mock" : "real"));
        return result;
    }

    private Map<Long, Double> history(Long userId) {
        Map<Long, Double> result = new HashMap<>();
        // 不加90天限制：过去学过的课也要排除；study_record 补上已归档/清理日志的历史。
        jdbc.query("SELECT course_id,MAX(progress) AS progress,MAX(is_finish) AS finished,MAX(study_time) AS minutes FROM ("
                        + "SELECT course_id,progress,is_finish,study_time FROM study_log WHERE user_id=? UNION ALL "
                        + "SELECT course_id,progress,is_finish,study_time FROM study_record WHERE user_id=?"
                        + ") h GROUP BY course_id", rs -> {
                    int progress = Math.max(0, Math.min(100, rs.getInt("progress")));
                    boolean effective = rs.getInt("finished") == 1 || progress > 0 || rs.getInt("minutes") > 0;
                    result.put(rs.getLong("course_id"), effective ? 0.5 + 0.5 * (rs.getInt("finished") == 1 ? 1 : progress / 100.0) : 0);
                }, userId, userId);
        return result;
    }

    private boolean enoughBehavior(LocalDate from, LocalDate today, boolean mock) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN COUNT(DISTINCT user_id)>=? AND COUNT(*)>=? "
                        + "THEN TRUE ELSE FALSE END FROM (SELECT l.user_id,l.course_id FROM study_log l JOIN sys_user u ON u.id=l.user_id "
                        + "JOIN course_info c ON c.id=l.course_id AND c.status=1 "
                        + "WHERE u.status=1 AND u.is_mock=? AND l.study_date>=? AND l.study_date<=? "
                        + "AND (l.is_finish=1 OR l.progress>=60) GROUP BY l.user_id,l.course_id) p",
                Boolean.class, Math.max(1, config.getMinUsers()), Math.max(1, config.getMinPositivePairs()), mock ? 1 : 0, from, today));
    }

    private void addCollaborative(String task, String table, String source, String reason, double weight, Long userId,
                                  Map<Long, CourseInfo> online, Set<Long> seen, Map<Long, Candidate> candidates) {
        Integer snapshots = jdbc.queryForObject("SELECT COUNT(*) FROM analysis_model_metric WHERE task=? AND metric='snapshot' "
                        + "AND status='completed' AND data_scope='real' AND create_time>=? AND create_time<=?", Integer.class,
                task, LocalDateTime.now().minusDays(Math.max(1, config.getMaxModelAgeDays())), LocalDateTime.now());
        if (snapshots == null || snapshots == 0) return;
        // 表名只来自本类固定常量；先去重、剔除下架/已学，再计算名次，避免原始分数量纲不同。
        List<Long> ids = jdbc.query("SELECT course_id,MAX(score) AS score FROM " + table
                        + " WHERE user_id=? AND score IS NOT NULL GROUP BY course_id ORDER BY score DESC,course_id ASC",
                (rs, row) -> rs.getLong("course_id"), userId).stream()
                .filter(id -> online.containsKey(id) && !seen.contains(id)).toList();
        addRanked(ids, source, reason, weight, online, seen, candidates);
    }

    private void addRanked(List<Long> input, String source, String reason, double weight,
                           Map<Long, CourseInfo> online, Set<Long> seen, Map<Long, Candidate> candidates) {
        List<Long> ids = input.stream().filter(id -> online.containsKey(id) && !seen.contains(id)).distinct().toList();
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            Candidate c = candidates.computeIfAbsent(id, key -> new Candidate(online.get(key)));
            c.score += weight / (11 + i); c.sources.add(source); c.reasons.add(reason);
        }
    }

    private static Long root(Long category, Map<Long, Long> parents) {
        if (category == null) return null;
        Set<Long> visited = new HashSet<>();
        while (visited.add(category)) {
            Long parent = parents.get(category);
            if (parent == null || parent == 0) return category;
            category = parent;
        }
        return null;
    }

    static Set<String> titleTokens(String title) {
        if (title == null) return Collections.emptySet();
        String text = BOILERPLATE.matcher(Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceAll(" ");
        Set<String> result = new HashSet<>();
        Matcher parts = TITLE_PART.matcher(text);
        while (parts.find()) {
            String part = parts.group();
            if (part.codePointAt(0) < 128) { result.add(part); continue; }
            int[] points = part.codePoints().toArray();
            for (int i = 0; i + 1 < points.length; i++) result.add(new String(points, i, 2));
        }
        return result;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        long intersection = a.stream().filter(b::contains).count();
        return (double) intersection / (a.size() + b.size() - intersection);
    }

    private static final class Candidate {
        final CourseInfo course;
        final List<String> sources = new ArrayList<>();
        final List<String> reasons = new ArrayList<>();
        double contentScore;
        double score;
        Candidate(CourseInfo course) { this.course = course; }
        CourseInfo result() {
            course.setRecommendationSources(sources); course.setRecommendationReason(String.join("；", reasons)); return course;
        }
    }
}
