package com.elderly.service.impl;

import com.elderly.config.SparkConfig;
import com.elderly.common.BizException;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

public class ClassificationReviewServiceTest {
    private JdbcTemplate jdbc;
    private ClassificationReviewService service;
    private SparkConfig config;
    private TransactionTemplate transactions;
    @BeforeEach void start() {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:review-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE ALIAS MD5 FOR 'com.elderly.service.impl.ClassificationReviewServiceTest.md5'");
        jdbc.execute("CREATE ALIAS SHA2 FOR 'com.elderly.service.impl.ClassificationReviewServiceTest.sha2'");
        jdbc.execute("CREATE TABLE analysis_text_classify(id BIGINT PRIMARY KEY)");
        ResourceDatabasePopulator migration = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V21__text_classification_confidence.sql"),
                new ClassPathResource("db/migration/V22__classification_review_token.sql"),
                new ClassPathResource("db/migration/V23__question_spark_predictions.sql"));
        migration.setSqlScriptEncoding("UTF-8"); migration.execute(ds);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,status INT,is_mock INT,province VARCHAR(64))");
        jdbc.execute("CREATE TABLE ai_question_record(id BIGINT PRIMARY KEY,user_id BIGINT,question VARCHAR(1000),create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE course_category(id BIGINT PRIMARY KEY,parent_id BIGINT,category_name VARCHAR(64))");
        jdbc.execute("CREATE TABLE course_info(id BIGINT PRIMARY KEY,category_id BIGINT,status INT)");
        jdbc.update("INSERT INTO course_category VALUES(1,0,'支付'),(2,0,'设置'),(3,1,'微信支付')");
        jdbc.update("INSERT INTO course_info VALUES(1,3,1),(2,2,1)");
        jdbc.update("INSERT INTO sys_user VALUES(1,1,0,'四川'),(2,1,1,'北京'),(3,0,0,'上海')");
        for (int i = 1; i <= 4; i++) jdbc.update("INSERT INTO ai_question_record VALUES(?,?,?,?)", i,
                i <= 2 ? i : i == 3 ? 3 : 1, "提问"+i, i == 4 ? LocalDateTime.now().minusDays(100) : LocalDateTime.now());
        config = new SparkConfig(); service = new ClassificationReviewService(jdbc, config);
        authenticate();
    }
    @AfterEach void stop() { SecurityContextHolder.clearContext(); }

    // H2 MySQL模式未内置这些函数，适配函数使用真实摘要算法。
    public static String md5(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    public static String sha2(String value, int bits) throws Exception {
        if (value == null) return null;
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-"+bits)
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(99L, null, Collections.emptyList()));
    }
    private String token(long id) {
        List<String> values = jdbc.queryForList("SELECT review_token FROM analysis_question_label WHERE question_id=?", String.class, id);
        return values.isEmpty() ? null : values.get(0);
    }
    private void save(long id, String category) {
        String text = jdbc.queryForObject("SELECT question FROM ai_question_record WHERE id=?", String.class, id);
        String version = token(id);
        transactions.executeWithoutResult(s -> service.save(id, category, text, version));
    }
    private void remove(long id) {
        String text = jdbc.queryForObject("SELECT question FROM ai_question_record WHERE id=?", String.class, id);
        String version = token(id);
        transactions.executeWithoutResult(s -> service.remove(id, text, version));
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> records(boolean pending) {
        return (List<Map<String,Object>>) service.page(1, 20, pending).get("records");
    }

    @Test void labelsAreHumanAttributedAndOnlyCurrentPopulationCanBeReviewed() {
        assertEquals(Arrays.asList("支付", "设置", "未确定业务"), service.categories());
        assertEquals(1L, service.page(1, 20, true).get("total"));
        save(1, "支付");
        assertEquals(99L, jdbc.queryForObject("SELECT reviewer_id FROM analysis_question_label", Long.class));
        assertEquals("提问1", jdbc.queryForObject("SELECT question_text FROM analysis_question_label", String.class));
        assertEquals(0L, service.page(1, 20, true).get("total"));
        assertEquals(1L, service.page(1, 20, false).get("total"));
        save(1, "未确定业务");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM analysis_question_label", Integer.class));
        assertThrows(BizException.class, () -> save(2, "支付"));
        assertThrows(BizException.class, () -> save(3, "支付"));
        assertThrows(BizException.class, () -> save(4, "支付"));
        assertThrows(BizException.class, () -> save(1, "伪造类别"));
        remove(1); assertEquals(1L, service.page(1, 20, true).get("total"));
        config.setDataScope("mock"); save(2, "支付");
        assertEquals(0L, service.page(1, 20, true).get("total"));
        assertThrows(BizException.class, () -> remove(1));
    }

    @Test void predictionGroupsUseSparkOutputRatherThanHumanLabelsAndRejectStaleText() throws Exception {
        jdbc.update("INSERT INTO ai_question_record VALUES(5,1,'另一个问题',?)",LocalDateTime.now());
        jdbc.update("INSERT INTO analysis_question_prediction(question_id,question_hash,category,confidence,run_id,data_scope) VALUES(?,?,?,?,?,?)",
                1, sha2("提问1",256),"支付",0.9,"spark-batch","real");
        jdbc.update("INSERT INTO analysis_question_prediction(question_id,question_hash,category,confidence,run_id,data_scope) VALUES(?,?,?,?,?,?)",
                5, sha2("另一个问题",256),"未确定业务",null,"spark-batch","real");
        assertEquals(1L,service.page(1,20,true,"classified").get("total"));
        assertEquals(1L,service.page(1,20,true,"undetermined").get("total"));
        save(1,"设置"); // Human disagreement must not move the model's prediction group.
        assertEquals(0L,service.page(1,20,true,"classified").get("total"));
        assertEquals(1L,service.page(1,20,false,"classified").get("total"));
        Map<?,?> classified = (Map<?,?>)((List<?>)service.page(1,20,false,"classified").get("records")).get(0);
        assertEquals("支付",classified.get("sparkCategory"));
        assertEquals("设置",classified.get("category"));
        jdbc.update("UPDATE ai_question_record SET question='已修改' WHERE id=1");
        assertEquals(0L,service.page(1,20,false,"classified").get("total"));
        assertEquals(1L,service.page(1,20,false,"unprocessed").get("total"));
        config.setDataScope("mock");
        assertEquals(0L,service.page(1,20,false,"classified").get("total"));
        assertEquals(1L,service.page(1,20,false,"unprocessed").get("total"));
        assertThrows(BizException.class,()->service.page(1,20,true,"invalid"));
    }

    @Test void changedQuestionCannotReceiveATagChosenForPreviousText() {
        save(1, "支付"); String oldToken = token(1);
        jdbc.update("UPDATE ai_question_record SET question='新的提问' WHERE id=1");
        assertEquals(1L, service.page(1, 20, true).get("total"));
        assertEquals(oldToken, records(true).get(0).get("reviewToken"));
        assertNull(records(true).get(0).get("category"));
        assertThrows(BizException.class, () -> transactions.executeWithoutResult(s -> service.save(1, "设置", "提问1", oldToken)));
        assertThrows(BizException.class, () -> transactions.executeWithoutResult(s -> service.remove(1, "提问1", oldToken)));
        save(1, "设置");
        assertEquals("新的提问", jdbc.queryForObject("SELECT question_text FROM analysis_question_label", String.class));
        assertNotEquals(oldToken, token(1));
    }

    @Test void staleReviewCannotOverwriteOrDeleteANewVersionEvenWithinSameSecond() {
        save(1, "支付"); String old = token(1);
        save(1, "设置"); String current = token(1);
        assertNotEquals(old, current);
        assertThrows(BizException.class, () -> transactions.executeWithoutResult(s -> service.save(1, "支付", "提问1", old)));
        assertThrows(BizException.class, () -> transactions.executeWithoutResult(s -> service.remove(1, "提问1", old)));
        assertEquals("设置", jdbc.queryForObject("SELECT category FROM analysis_question_label", String.class));
        remove(1); save(1, "支付");
        assertNotEquals(old, token(1));
        assertThrows(BizException.class, () -> transactions.executeWithoutResult(s -> service.remove(1, "提问1", old)));
    }

    @Test void simultaneousFirstReviewsDoNotSilentlyOverwriteEachOther() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        Callable<String> review = () -> {
            authenticate(); ready.countDown(); start.await();
            try {
                transactions.executeWithoutResult(s -> service.save(1, "支付", "提问1", null));
                return "saved";
            } catch (BizException conflict) { return "conflict"; }
            finally { SecurityContextHolder.clearContext(); }
        };
        try {
            Future<String> first = workers.submit(review), second = workers.submit(review);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            List<String> results = Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(results, "saved"));
            assertEquals(1, Collections.frequency(results, "conflict"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM analysis_question_label", Integer.class));
        } finally { start.countDown(); workers.shutdownNow(); }
    }

    @Test void obsoleteCategoriesArePendingAndWhitespaceMatchesTrainingTaxonomy() {
        jdbc.update("UPDATE course_category SET category_name=' 支付 ' WHERE id=1");
        assertEquals(Arrays.asList("支付", "设置", "未确定业务"), service.categories());
        save(1, "支付"); assertEquals(0L, service.page(1, 20, true).get("total"));
        jdbc.update("UPDATE course_info SET status=0 WHERE id=1");
        jdbc.update("INSERT INTO course_category VALUES(4,0,NULL)");
        jdbc.update("INSERT INTO course_info VALUES(3,4,1)");
        assertEquals(1L, service.page(1, 20, true).get("total"));
        assertNull(records(true).get(0).get("category"));
        assertNotNull(records(true).get(0).get("reviewToken"));
        assertFalse(service.categories().contains("支付"));
        jdbc.update("UPDATE course_category SET category_name=NULL WHERE id=1");
        jdbc.update("UPDATE course_info SET status=1 WHERE id=1");
        assertFalse(service.categories().contains("微信支付"));
    }

    @Test void identifiersKeepTheirFullPrecisionAndInvalidRequestsAreRejected() {
        long id = 9007199254740993L;
        jdbc.update("INSERT INTO ai_question_record VALUES(?,1,'大编号提问',?)", id, LocalDateTime.now());
        assertTrue(records(true).stream().anyMatch(q -> Long.toString(id).equals(q.get("id"))));
        save(id, "设置"); assertNotNull(token(id));
        assertThrows(BizException.class, () -> service.page(0, 20, true));
        assertThrows(BizException.class, () -> service.page(1, 101, true));
        assertThrows(BizException.class, () -> service.save(1, "支付", null, null));
        config.setDataScope("all"); assertThrows(BizException.class, () -> service.page(1, 20, true));
        config.setDataScope("real"); config.setLookbackDays(0);
        assertThrows(BizException.class, () -> service.page(1, 20, true));
        SecurityContextHolder.clearContext();
        assertThrows(BizException.class, () -> service.remove(1, "提问1", null));
    }
}
