package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.*;
import java.util.*;

/** 只补充中文备注；复用 SHOW CREATE TABLE 的完整列定义，避免 MODIFY 丢失类型/默认值/约束。 */
public class V20__spark_chinese_comments extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        int quoteSetting;
        try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SELECT @@sql_quote_show_create")) {
            r.next(); quoteSetting = r.getInt(1);
        }
        try (Statement s = connection.createStatement()) { s.execute("SET SESSION sql_quote_show_create = 1"); }
        try {
            for (Map.Entry<String, String> table : tableComments().entrySet()) {
                String name = table.getKey();
                if (!exists(connection, name)) continue;
                String ddl;
                try (Statement s = connection.createStatement(); ResultSet r = s.executeQuery("SHOW CREATE TABLE `" + name + "`")) {
                    r.next(); ddl = r.getString(2);
                }
                List<String> clauses = new ArrayList<>();
                clauses.add("COMMENT = '" + table.getValue() + "'");
                for (Map.Entry<String, String> field : columnComments(name).entrySet()) {
                    String definition = columnDefinition(ddl, field.getKey());
                    if (definition != null) clauses.add("MODIFY COLUMN " + withComment(definition, field.getValue()));
                }
                try (Statement s = connection.createStatement()) {
                    s.execute("ALTER TABLE `" + name + "` " + String.join(", ", clauses));
                }
            }
        } finally {
            try (Statement s = connection.createStatement()) { s.execute("SET SESSION sql_quote_show_create = " + quoteSetting); }
        }
    }

    private static boolean exists(Connection c, String table) throws SQLException {
        try (ResultSet r = c.getMetaData().getTables(c.getCatalog(), null, table, new String[]{"TABLE"})) {
            while (r.next()) if (table.equals(r.getString("TABLE_NAME"))) return true;
        }
        return false;
    }

    static Map<String, String> tableComments() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("study_log", "学习行为日志（增量上报，Spark分析数据源）");
        m.put("sys_user", "系统用户（含常住地区、启用状态及模拟用户标记）");
        m.put("course_info", "课程信息（标题、内容、分类及上架状态）");
        m.put("course_category", "课程分类（含父子分类关系）");
        m.put("ai_question_record", "智能问答记录（用户提问与系统回答）");
        m.put("analysis_result", "Spark学习人群聚类结果（相对活跃度分组）");
        m.put("analysis_course_hot", "Spark课程热度结果（课程独立学习用户排行）");
        m.put("analysis_category_share", "Spark课程分类参与分布结果（用户可跨分类参与）");
        m.put("analysis_study_trend", "Spark每日学习趋势结果（活跃用户数与增量分钟）");
        m.put("analysis_association_rule", "Spark课程共学关联规则（支持度、置信度、提升度）");
        m.put("analysis_word_freq", "Spark问答热点词频结果（包含该词的提问条数）");
        m.put("analysis_recommend", "Spark物品协同过滤课程推荐结果（ItemCF）");
        m.put("analysis_recommend_als", "Spark隐式反馈矩阵分解课程推荐结果（ALS）");
        m.put("analysis_text_classify", "Spark问答业务分类结果（当前常住省份与预测业务）");
        m.put("analysis_model_metric", "Spark运行与模型评估记录（批次、来源、指标及验证状态）");
        return m;
    }

    static Map<String, String> columnComments(String table) {
        Map<String, String> m = new LinkedHashMap<>();
        add(m, "id", "主键编号", "create_time", table.startsWith("analysis_") ? "分析结果入库时间" : "记录创建时间");
        switch (table) {
            case "study_log":
                add(m, "user_id", "学习用户编号（关联sys_user）", "course_id", "学习课程编号（关联course_info）",
                        "study_time", "本次上报新增学习分钟数（非累计时长；日志不等于学习会话）",
                        "progress", "本次上报课程进度（0至100）", "is_finish", "本次上报是否完成（0未完成，1已完成）",
                        "study_date", "学习日期（用于活跃天数及每日趋势统计）"); break;
            case "sys_user":
                add(m, "username", "用户登录账号", "nickname", "用户昵称", "province", "用户当前常住省份（非提问时所在地）",
                        "city", "用户当前常住城市", "district", "用户当前常住区县", "status", "用户状态（0停用，1启用）",
                        "is_mock", "模拟用户标记（0真实用户，1模拟用户；分析时分别取数）"); break;
            case "course_info":
                add(m, "title", "课程标题（用于展示与文本分类弱监督训练）", "category_id", "所属课程分类编号",
                        "status", "课程状态（0下架，1上架；Spark只分析上架课程）", "content", "课程图文内容（HTML）"); break;
            case "course_category":
                add(m, "category_name", "课程分类中文名称", "parent_id", "父分类编号（0或空表示顶级分类）"); break;
            case "ai_question_record":
                add(m, "user_id", "提问用户编号（关联sys_user）", "question", "用户提问文本（词频与业务分类输入）",
                        "answer", "智能问答系统回答文本"); break;
            case "analysis_result":
                add(m, "cluster_id", "模型簇编号（编号本身不代表活跃度高低）", "cluster_name", "相对活跃度分组名称",
                        "user_count", "该组参与分析的用户数", "avg_study_time", "组内用户活跃日平均累计分钟的均值",
                        "avg_finish_rate", "组内用户课程完成率的均值（0至1，非完成日志比例）", "description", "分组解释与适用限制"); break;
            case "analysis_course_hot":
                add(m, "rank_no", "按独立学习用户数排序的名次", "course_id", "课程编号", "title", "课程标题快照",
                        "study_count", "分析窗口内课程独立学习用户数（非日志数或会话数）"); break;
            case "analysis_category_share":
                add(m, "category_name", "课程分类名称", "study_count", "分析窗口内该分类独立学习用户数",
                        "share_rate", "分类参与占比（百分数；分母为各分类独立用户数之和，用户可跨分类）"); break;
            case "analysis_study_trend":
                add(m, "study_date", "学习统计日期", "study_count", "当日独立活跃学习用户数",
                        "study_duration_min", "当日上报新增学习分钟数之和"); break;
            case "analysis_association_rule":
                add(m, "antecedent_ids", "关联规则前件课程编号（逗号分隔）", "antecedent_titles", "前件课程标题",
                        "consequent_ids", "关联规则后件课程编号（逗号分隔）", "consequent_titles", "后件课程标题",
                        "confidence", "置信度（接触前件用户中也接触后件的比例，0至1）",
                        "support", "支持度（同时接触前后件的用户比例，0至1）",
                        "lift", "提升度（置信度除以后件普及率，接近1表示关联较弱）"); break;
            case "analysis_word_freq":
                add(m, "rank_no", "热点词排名", "word", "分词后的热点词", "freq", "包含该词的提问条数（单条提问重复词仅计一次）"); break;
            case "analysis_recommend": case "analysis_recommend_als":
                add(m, "user_id", "接收推荐的用户编号", "course_id", "推荐课程编号", "title", "推荐课程标题快照",
                        "score", table.equals("analysis_recommend") ? "用户候选内归一化排序分数（0至1，非概率，不跨用户比较）" : "ALS隐式反馈模型排序分数（非概率）"); break;
            case "analysis_text_classify":
                add(m, "province", "用户当前常住省份（未绑定时为未知地区）", "category", "预测业务类别（低可信结果为未确定业务）",
                        "question_count", "该省份该预测业务类别的有效提问条数"); break;
            case "analysis_model_metric":
                add(m, "task", "Spark任务英文标识", "run_id", "数据快照批次编号（同批任务复用同一输入）",
                        "data_scope", "数据来源（real真实数据，mock模拟数据）", "metric", "评估指标英文标识",
                        "value", "指标数值（未验证或样本不足时可为空）", "samples", "该指标使用的样本数（口径见detail）",
                        "status", "验证状态（evaluated已评估，unvalidated未验证，insufficient_data样本不足）",
                        "detail", "指标口径、测试分割及使用限制说明"); break;
            default: break;
        }
        return m;
    }

    private static void add(Map<String, String> m, String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
    }

    static String columnDefinition(String ddl, String column) {
        for (String line : ddl.split("\\r?\\n")) {
            String value = line.trim();
            if (value.startsWith("`" + column + "` ")) return value.endsWith(",") ? value.substring(0, value.length() - 1) : value;
        }
        return null;
    }

    static String withComment(String definition, String comment) {
        char quote = 0;
        for (int i = 0; i < definition.length(); i++) {
            char c = definition.charAt(i);
            if (quote != 0) {
                if (c == '\\') { i++; continue; }
                if (c == quote) {
                    if (i + 1 < definition.length() && definition.charAt(i + 1) == quote) i++; else quote = 0;
                }
            } else if (c == '\'' || c == '"' || c == '`') quote = c;
            else if (definition.regionMatches(true, i, "COMMENT", 0, 7)
                    && i > 0 && Character.isWhitespace(definition.charAt(i - 1))
                    && i + 7 < definition.length() && Character.isWhitespace(definition.charAt(i + 7))) {
                int start = i + 7;
                while (Character.isWhitespace(definition.charAt(start))) start++;
                if (definition.charAt(start) != '\'') throw new IllegalArgumentException("无法识别原列备注: " + definition);
                int end = start + 1;
                for (; end < definition.length(); end++) {
                    if (definition.charAt(end) == '\\') { end++; continue; }
                    if (definition.charAt(end) == '\'') {
                        if (end + 1 < definition.length() && definition.charAt(end + 1) == '\'') { end++; continue; }
                        break;
                    }
                }
                if (end >= definition.length()) throw new IllegalArgumentException("原列备注缺少结束引号");
                return definition.substring(0, i) + "COMMENT '" + comment.replace("'", "''") + "'" + definition.substring(end + 1);
            }
        }
        return definition + " COMMENT '" + comment.replace("'", "''") + "'";
    }
}
