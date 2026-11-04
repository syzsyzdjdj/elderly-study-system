package com.elderly.service.impl;

import com.elderly.config.RecommendationConfig;
import com.elderly.entity.CourseInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HybridRecommendationServiceTest {
    private JdbcTemplate jdbc;
    private RecommendationConfig config;
    private HybridRecommendationService service;
    private MockRecommendationSnapshots mockSnapshots;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:hybrid-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", ""));
        jdbc.execute("CREATE TABLE course_category(id BIGINT PRIMARY KEY,parent_id BIGINT,category_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE course_info(id BIGINT PRIMARY KEY,category_id BIGINT,title VARCHAR(255),cover VARCHAR(255),"
                + "intro VARCHAR(255),duration INT,points_reward INT,status INT,province VARCHAR(64),city VARCHAR(64),district VARCHAR(64),create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,status INT,is_mock INT)");
        jdbc.execute("CREATE TABLE study_log(user_id BIGINT,course_id BIGINT,progress INT,is_finish INT,study_time INT,study_date DATE)");
        jdbc.execute("CREATE TABLE study_record(user_id BIGINT,course_id BIGINT,progress INT,is_finish INT,study_time INT)");
        jdbc.execute("CREATE TABLE analysis_model_metric(task VARCHAR(40),metric VARCHAR(40),status VARCHAR(40),data_scope VARCHAR(16),create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE analysis_recommend(user_id BIGINT,course_id BIGINT,score DOUBLE)");
        jdbc.execute("CREATE TABLE analysis_recommend_als(user_id BIGINT,course_id BIGINT,score DOUBLE)");
        jdbc.update("INSERT INTO course_category VALUES(10,0,'手机'),(11,10,'支付'),(12,10,'设置'),(20,0,'健康')");
        jdbc.update("INSERT INTO sys_user VALUES(1,1,0),(2,1,0),(3,1,0),(4,1,0),(5,1,0),(6,1,1),(7,0,0)");
        mockSnapshots = mock(MockRecommendationSnapshots.class);
        config = new RecommendationConfig(); service = new HybridRecommendationService(jdbc, config, mockSnapshots);
    }

    @Test void sparseDataStillRecommendsSimilarUnseenCoursesAndFillsEachVacancy() {
        course(1, 11L, "微信支付账单", 1); course(2, 11L, "微信支付安全", 1);
        course(3, 20L, "健康运动", 1); course(4, null, "出行问路", 1);
        study(1, 1, 100, LocalDate.now()); study(2, 3, 70, LocalDate.now());
        snapshot("als_recommend", "real", LocalDateTime.now());
        jdbc.update("INSERT INTO analysis_recommend_als VALUES(1,4,99999)");
        List<CourseInfo> result = service.recommend(1L);
        assertEquals(List.of(2L, 3L, 4L), ids(result));
        assertEquals(List.of("content"), result.get(0).getRecommendationSources());
        assertTrue(result.get(0).getRecommendationReason().contains("微信支付账单"));
        assertEquals(List.of("popular"), result.get(1).getRecommendationSources());
        assertEquals(List.of("latest"), result.get(2).getRecommendationSources());
    }

    @Test void fullHistoryAndRecordOnlyHistoryExcludeLearnedCoursesIncludingOfflineSeeds() {
        course(1, 11L, "微信支付", 0); course(2, 11L, "微信转账", 1);
        course(3, 11L, "微信收款", 1); course(4, 11L, "微信账单", 1);
        course(5, 11L, "付款码", 0);
        study(1, 1, 100, LocalDate.now().minusDays(150));
        study(1, 2, 1, LocalDate.now());
        jdbc.update("INSERT INTO study_record VALUES(1,3,100,1,20)");
        assertEquals(List.of(4L), ids(service.recommend(1L)));
        assertEquals(List.of("content"), service.recommend(1L).get(0).getRecommendationSources());
    }

    @Test void titleAndCategoryHierarchyFindCoursesWithNoBehaviorOfTheirOwn() {
        course(1, 11L, "微信支付账单", 1); course(2, null, "微信支付账单查询", 1);
        course(3, 12L, "通知权限设置", 1); course(4, null, "课程学习基础教程", 1);
        study(1, 1, 80, LocalDate.now());
        List<CourseInfo> result = service.recommend(1L);
        assertEquals(List.of("content"), find(result, 2).getRecommendationSources());
        assertTrue(find(result, 2).getRecommendationReason().contains("标题相关"));
        assertEquals(List.of("content"), find(result, 3).getRecommendationSources());
        assertTrue(find(result, 3).getRecommendationReason().contains("同一大类"));
        assertEquals(List.of("latest"), find(result, 4).getRecommendationSources());
    }

    @Test void enoughRealBehaviorCombinesIndependentModelRanksAndDeduplicates() {
        course(1, 11L, "微信支付", 1); course(2, 11L, "手机转账", 1);
        course(3, 11L, "账单查询", 1); course(4, 20L, "饮食健康", 1);
        course(5, 20L, "身体锻炼", 1); course(6, 20L, "线下课程", 0);
        learnTwoAndAddCohort();
        snapshot("als_recommend", "real", LocalDateTime.now()); snapshot("item_cf", "real", LocalDateTime.now());
        jdbc.update("INSERT INTO analysis_recommend_als VALUES(1,1,999999),(1,6,88888),(1,3,0.02),(1,3,0.01),(1,4,0.01)");
        jdbc.update("INSERT INTO analysis_recommend VALUES(1,4,1000),(1,3,100),(1,5,1)");
        List<CourseInfo> result = service.recommend(1L);
        assertEquals(List.of(3L, 4L, 5L), ids(result));
        assertEquals(List.of("content", "als", "item_cf"), result.get(0).getRecommendationSources());
        assertEquals(List.of("als", "item_cf"), result.get(1).getRecommendationSources());
        assertEquals(List.of("item_cf"), result.get(2).getRecommendationSources());
        // 原始 ALS 分数整体放大不影响融合结果。
        jdbc.update("UPDATE analysis_recommend_als SET score=score*1000000");
        assertEquals(ids(result), ids(service.recommend(1L)));
        // 新增学习发生在离线批次之后，也必须立即过滤。
        study(1, 3, 1, LocalDate.now());
        assertFalse(ids(service.recommend(1L)).contains(3L));
    }

    @Test void invalidAlsCandidatesDoNotBlockItemCfCandidates() {
        config.setMinPositivePairs(10);
        course(1, 11L, "手机支付", 1); course(2, 11L, "账单查询", 1);
        course(3, 20L, "健康饮食", 1); course(4, 20L, "下架课程", 0);
        learnTwoAndAddCohort();
        snapshot("als_recommend", "real", LocalDateTime.now()); snapshot("item_cf", "real", LocalDateTime.now());
        jdbc.update("INSERT INTO analysis_recommend_als VALUES(1,4,100)");
        jdbc.update("INSERT INTO analysis_recommend VALUES(1,3,0)");
        assertEquals(List.of("item_cf"), find(service.recommend(1L), 3).getRecommendationSources());
    }

    @Test void mockExpiredFutureAndUntrackedModelsCannotBePresentedAsPersonalized() {
        config.setMinPositivePairs(10);
        course(1, 11L, "手机支付", 1); course(2, 11L, "账单查询", 1); course(3, 20L, "健康饮食", 1);
        learnTwoAndAddCohort();
        jdbc.update("INSERT INTO analysis_recommend_als VALUES(1,3,100)");
        for (String kind : List.of("legacy", "mock", "expired", "future")) {
            jdbc.update("DELETE FROM analysis_model_metric");
            if (!kind.equals("legacy")) snapshot("als_recommend", kind.equals("mock") ? "mock" : "real",
                    kind.equals("expired") ? LocalDateTime.now().minusDays(31)
                            : kind.equals("future") ? LocalDateTime.now().plusDays(1) : LocalDateTime.now());
            assertFalse(find(service.recommend(1L), 3).getRecommendationSources().contains("als"), kind);
        }
    }

    @Test void duplicateLogsMockDisabledAndOldUsersCannotInflateBehaviorGate() {
        course(1, 11L, "支付", 1); course(2, 11L, "转账", 1); course(3, 20L, "健康", 1);
        study(1, 1, 100, LocalDate.now()); study(1, 2, 100, LocalDate.now());
        for (int i = 0; i < 30; i++) {
            study(1, 1, 100, LocalDate.now());
            study(6, i, 100, LocalDate.now()); study(7, i, 100, LocalDate.now());
            study(2, i, 100, LocalDate.now().minusDays(100)); study(3, i, 100, LocalDate.now().plusDays(1));
        }
        snapshot("als_recommend", "real", LocalDateTime.now());
        jdbc.update("INSERT INTO analysis_recommend_als VALUES(1,3,1)");
        assertEquals(List.of("latest"), find(service.recommend(1L), 3).getRecommendationSources());
    }

    @Test void singleUserCourseIsNotEnoughForCollaborativeEvenWithLargeCohort() {
        config.setMinPositivePairs(5);
        course(1, 11L, "支付", 1); course(2, 20L, "健康饮食", 1);
        study(1, 1, 100, LocalDate.now()); addCohort();
        snapshot("als_recommend", "real", LocalDateTime.now());
        jdbc.update("INSERT INTO analysis_recommend_als VALUES(1,2,1)");
        assertEquals(List.of("popular"), find(service.recommend(1L), 2).getRecommendationSources());
    }

    @Test void anonymousPopularityCountsDistinctRealUsersThenUsesNewestUnpopularCourses() {
        course(1, 11L, "手机", 1); course(2, 20L, "健康", 1); course(3, 20L, "锻炼", 1);
        course(4, 20L, "散步", 1);
        for (int i = 0; i < 20; i++) study(1, 1, 100, LocalDate.now());
        study(2, 2, 100, LocalDate.now()); study(3, 2, 100, LocalDate.now());
        study(6, 3, 100, LocalDate.now()); study(7, 3, 100, LocalDate.now());
        study(4, 3, 100, LocalDate.now().minusDays(100)); study(5, 3, 100, LocalDate.now().plusDays(1));
        config.setLimit(3);
        List<CourseInfo> result = service.recommend(null);
        assertEquals(List.of(2L, 1L, 4L), ids(result));
        assertTrue(result.get(0).getRecommendationReason().contains("2位学员"));
        assertTrue(result.get(1).getRecommendationReason().contains("1位学员"));
        assertEquals(List.of("latest"), result.get(2).getRecommendationSources());
    }

    @Test void zeroActivityIsExcludedButDoesNotSeedAndAllLearnedReturnsEmpty() {
        course(1, 11L, "支付", 1); course(2, 11L, "转账", 1);
        study(1, 1, 0, LocalDate.now());
        List<CourseInfo> result = service.recommend(1L);
        assertEquals(List.of(2L), ids(result));
        assertEquals(List.of("latest"), result.get(0).getRecommendationSources());
        study(1, 2, 100, LocalDate.now());
        assertTrue(service.recommend(1L).isEmpty());
    }

    @Test void normalizationHandlesLatinAndChineseTitlesWithoutGenericBoilerplate() {
        assertEquals(HybridRecommendationService.titleTokens("ＷＥＣＨＡＴ 支付入门"), HybridRecommendationService.titleTokens("wechat 支付基础教程"));
        assertTrue(HybridRecommendationService.titleTokens(null).isEmpty());
        assertTrue(HybridRecommendationService.titleTokens("课程学习基础教程").isEmpty());
    }

    @Test void mockLearnerUsesOnlyMockSnapshotModelsAndMockPopularity() {
        config.setMinUsers(1); config.setMinPositivePairs(2);
        course(1, 11L, "支付", 1); course(2, 11L, "转账", 1);
        course(3, 20L, "健康", 1); course(4, 20L, "锻炼", 1); course(5, null, "最新探索", 1);
        study(6, 1, 100, LocalDate.now()); study(6, 2, 100, LocalDate.now());
        study(1, 5, 100, LocalDate.now());
        when(mockSnapshots.latest()).thenReturn(Optional.of(new MockRecommendationSnapshots.Snapshot("mock-run",
                Map.of(6L, List.of(3L)), Map.of(6L, List.of(4L)))));
        snapshot("item_cf", "real", LocalDateTime.now());
        jdbc.update("INSERT INTO analysis_recommend VALUES(6,5,9999)");
        List<CourseInfo> result = service.recommend(6L);
        assertEquals(List.of(3L, 4L, 5L), ids(result));
        assertEquals(List.of("als"), find(result, 3).getRecommendationSources());
        assertEquals(List.of("item_cf"), find(result, 4).getRecommendationSources());
        assertEquals(List.of("latest"), find(result, 5).getRecommendationSources());
        assertTrue(result.stream().allMatch(c -> "mock".equals(c.getRecommendationDataScope())));
        study(6, 3, 1, LocalDate.now());
        assertFalse(ids(service.recommend(6L)).contains(3L));
    }

    @Test void mockPopularityAndRealPopularityNeverMix() {
        course(1, null, "甲", 1); course(2, null, "乙", 1); course(3, null, "丙", 1);
        jdbc.update("INSERT INTO sys_user VALUES(8,1,1)");
        study(6, 1, 1, LocalDate.now()); study(8, 3, 1, LocalDate.now()); study(2, 2, 100, LocalDate.now());
        List<CourseInfo> mockResult = service.recommend(6L);
        assertEquals(List.of(3L, 2L), ids(mockResult));
        assertEquals(List.of("popular"), find(mockResult, 3).getRecommendationSources());
        assertEquals(List.of("latest"), find(mockResult, 2).getRecommendationSources());
        clearInvocations(mockSnapshots);
        List<CourseInfo> realResult = service.recommend(1L);
        assertEquals(List.of("popular"), find(realResult, 2).getRecommendationSources());
        assertTrue(realResult.stream().allMatch(c -> "real".equals(c.getRecommendationDataScope())));
        verifyNoInteractions(mockSnapshots);
    }

    private void course(long id, Long category, String title, int status) {
        jdbc.update("INSERT INTO course_info(id,category_id,title,status,create_time) VALUES(?,?,?,?,?)",
                id, category, title, status, LocalDateTime.now().minusDays(100 - id));
    }
    private void study(long user, long course, int progress, LocalDate date) {
        jdbc.update("INSERT INTO study_log VALUES(?,?,?,?,?,?)", user, course, progress, progress == 100 ? 1 : 0, progress > 0 ? 1 : 0, date);
    }
    private void snapshot(String task, String scope, LocalDateTime time) {
        jdbc.update("INSERT INTO analysis_model_metric VALUES(?,'snapshot','completed',?,?)", task, scope, time);
    }
    private void learnTwoAndAddCohort() {
        study(1, 1, 100, LocalDate.now()); study(1, 2, 100, LocalDate.now()); addCohort();
    }
    private void addCohort() {
        for (int u = 2; u <= 5; u++) for (int c = 1; c <= 5; c++) study(u, c, 100, LocalDate.now());
    }
    private List<Long> ids(List<CourseInfo> result) { return result.stream().map(CourseInfo::getId).toList(); }
    private CourseInfo find(List<CourseInfo> result, long id) { return result.stream().filter(c -> c.getId() == id).findFirst().orElseThrow(); }
}
