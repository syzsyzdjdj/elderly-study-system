package db.migration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparkChineseCommentsTest {
    @Test void commentReplacementKeepsAllColumnAttributes() {
        String definition = "`id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '旧备注'";
        assertEquals("`id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键编号'",
                V20__spark_chinese_comments.withComment(definition, "主键编号"));
        String timestamp = "`create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)";
        assertEquals(timestamp + " COMMENT '时间'", V20__spark_chinese_comments.withComment(timestamp, "时间"));
    }
    @Test void quotedCommentWordsInsideDefaultsAreNotRemoved() {
        String definition = "`title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT ' COMMENT ''测试''' COMMENT '旧\\'说明' INVISIBLE";
        assertEquals("`title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT ' COMMENT ''测试''' COMMENT '新说明' INVISIBLE",
                V20__spark_chinese_comments.withComment(definition, "新说明"));
    }
    @Test void definitionsAreMatchedExactlyAndExistingSuffixesKept() {
        String ddl = "CREATE TABLE `t` (\n  `study_time` int DEFAULT '0',\n  `study_time_extra` int DEFAULT NULL\n)";
        assertEquals("`study_time` int DEFAULT '0'", V20__spark_chinese_comments.columnDefinition(ddl, "study_time"));
        assertNull(V20__spark_chinese_comments.columnDefinition(ddl, "unknown"));
        String generated = "`value` int GENERATED ALWAYS AS ((1 + 1)) STORED COMMENT 'old'";
        assertEquals("`value` int GENERATED ALWAYS AS ((1 + 1)) STORED COMMENT '指标'",
                V20__spark_chinese_comments.withComment(generated, "指标"));
    }
    @Test void allSparkTablesHaveChineseLabelsAndKeyMetricsExplainUnits() {
        assertEquals(15, V20__spark_chinese_comments.tableComments().size());
        assertTrue(V20__spark_chinese_comments.columnComments("study_log").get("study_time").contains("新增"));
        assertTrue(V20__spark_chinese_comments.columnComments("analysis_course_hot").get("study_count").contains("独立"));
        assertTrue(V20__spark_chinese_comments.columnComments("analysis_recommend_als").get("score").contains("非概率"));
    }
}
