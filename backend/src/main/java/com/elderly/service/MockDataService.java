package com.elderly.service;

import com.elderly.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 模拟数据生成（仅 ADMIN 调用，用于 Spark 集群分析演示）：
 * - N 名虚拟学员（username=virtual<1001+>，密码 123456，role=学员）
 * - M 条学习行为日志（近 180 天，老年人作息分布，按课程热度加权）
 * - K 条模拟 AI 提问（老年人关心的数字生活主题）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockDataService {

    private final JdbcTemplate jdbcTemplate;

    /** 123456 的 BCrypt 密文（与演示账号一致） */
    private static final String PASSWORD_HASH = "$2a$10$Xf8/1RBJ2PEphIelSLr8sORjk8xJf86E2b/v3aknKUQF87TI9zLdi";

    /** 17 门课的热度权重（数字越大越常被学） */
    private static final int[] COURSE_WEIGHTS = {
            8, 5, 8, 5, 7, 5, 5, 6, 6, 4, 5, 9, 7, 9, 7, 5, 5};

    /** 虚拟学员常住地区（与政务政策/办事入口种子数据保持一致，支撑分地区看板演示） */
    private static final String[][] REGIONS = {
            {"北京市", "北京市", "东城区"},
            {"北京市", "北京市", "朝阳区"},
            {"上海市", "上海市", "浦东新区"},
            {"上海市", "上海市", "徐汇区"},
            {"广东省", "广州市", "天河区"},
            {"广东省", "深圳市", "南山区"},
            {"浙江省", "杭州市", "西湖区"},
            {"浙江省", "宁波市", "海曙区"},
            {"江苏省", "南京市", "鼓楼区"},
            {"江苏省", "苏州市", "姑苏区"},
            {"四川省", "成都市", "武侯区"},
            {"四川省", "绵阳市", "涪城区"}
    };

    /** 老年人常见数字生活提问模板 */
    private static final String[] QUESTION_TEMPLATES = {
            "手机怎么交水电费？",
            "微信怎么发红包？",
            "养老金认证在手机上怎么做？",
            "健康码在哪里打开？",
            "收到中奖短信是不是诈骗？",
            "陌生人打电话说医保卡停用了怎么办？",
            "手机银行怎么转账给子女？",
            "网上挂号怎么操作？",
            "微信支付密码忘了怎么办？",
            "抖音怎么拍视频发给家人？",
            "快递到付诈骗怎么识别？",
            "社保年审在手机上能办吗？",
            "怎么用手机查公交车到站时间？",
            "老年卡手机充值怎么弄？",
            "视频通话怎么打给孙子？",
            "手机相册照片怎么传到电脑上？",
            "收到免费领鸡蛋的讲座信息能去吗？",
            "保健品电话推销是不是骗局？",
            "微信里的钱怎么提现到银行卡？",
            "手机流量不够用了怎么办？",
            "网上买药怎么用医保？",
            "打车软件怎么叫车？",
            "短信里的链接能不能点？",
            "手机验证码能告诉别人吗？",
            "微信好友发来借钱消息怎么核实？",
            "电视购物退货怎么办理？",
            "电子社保卡怎么申领？",
            "手机丢了微信里的钱怎么办？",
            "淘宝购物怎么申请退款？",
            "养老助残卡余额怎么查询？"};

    /**
     * 生成模拟数据，返回各表写入数量与耗时（秒）。
     */
    public Map<String, Object> generate(int users, int logs, int aiQuestions) {
        Integer existed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE is_mock = 1", Integer.class);
        if (existed != null && existed > 0) {
            throw new BizException("已存在 " + existed + " 个虚拟学员，如需重新生成请先调用 /api/analysis/clearMockData");
        }
        long start = System.currentTimeMillis();
        Random random = new Random(42);

        // 1. 学员角色 id
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_key = 'user' LIMIT 1", Long.class);
        if (roleId == null) {
            throw new BizException("未找到学员角色，请检查 sys_role 数据");
        }

        // 2. 批量插入虚拟学员（随机分配常住省/市/区，用于分地区学情统计）
        List<Object[]> userBatch = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            String[] region = REGIONS[random.nextInt(REGIONS.length)];
            userBatch.add(new Object[]{
                    "virtual" + (1001 + i),
                    PASSWORD_HASH,
                    "虚拟学员" + (i + 1),
                    "199" + (10000000 + random.nextInt(90000000)),
                    roleId,
                    random.nextInt(301),
                    region[0], region[1], region[2],
                    100, 0,
                    "default", 1, 1,
                    LocalDateTime.now(), LocalDateTime.now()
            });
        }
        batchInsert("INSERT INTO sys_user (username, password, nickname, phone, role_id, points, province, city, district, font_scale, high_contrast, voice, status, is_mock, create_time, update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                userBatch);
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE is_mock = 1 ORDER BY id", Long.class);
        log.info("已插入 {} 个虚拟学员", userIds.size());

        // 3. 批量插入学习行为日志（近 180 天，老年人作息：早 8-11 / 午 14-17 / 晚 18-21 高峰）
        //    每个学员有 4~7 门偏好课程，约 80% 日志落在偏好课上（模拟真实兴趣偏好，
        //    让 FP-Growth 关联规则与协同过滤推荐产生有区分度的结果）
        Map<Long, List<Integer>> prefs = new HashMap<>();
        for (Long uid : userIds) {
            List<Integer> list = new ArrayList<>();
            int n = 4 + random.nextInt(4);
            while (list.size() < n) {
                int c = pickCourse(random);
                if (!list.contains(c)) {
                    list.add(c);
                }
            }
            prefs.put(uid, list);
        }
        LocalDate today = LocalDate.now();
        List<Object[]> logBatch = new ArrayList<>();
        for (int i = 0; i < logs; i++) {
            Long userId = userIds.get(random.nextInt(userIds.size()));
            List<Integer> userPrefs = prefs.get(userId);
            int courseId = random.nextDouble() < 0.8
                    ? userPrefs.get(random.nextInt(userPrefs.size()))
                    : pickCourse(random);
            int finish = random.nextDouble() < 0.55 ? 1 : 0;
            int progress = finish == 1 ? 100 : 5 + random.nextInt(91);
            int studyTime = 5 + (int) (Math.pow(random.nextDouble(), 1.5) * 55);
            LocalDate date = pickDate(random, today);
            LocalDateTime time = date.atTime(pickHour(random), random.nextInt(60));
            logBatch.add(new Object[]{userId, (long) courseId, studyTime, progress, finish, date, time});
            if (logBatch.size() >= 1000) {
                batchInsert("INSERT INTO study_log (user_id, course_id, study_time, progress, is_finish, study_date, create_time) VALUES (?,?,?,?,?,?,?)",
                        logBatch);
                logBatch.clear();
            }
        }
        if (!logBatch.isEmpty()) {
            batchInsert("INSERT INTO study_log (user_id, course_id, study_time, progress, is_finish, study_date, create_time) VALUES (?,?,?,?,?,?,?)",
                    logBatch);
        }
        log.info("已插入 {} 条学习行为日志", logs);

        // 4. 批量插入模拟 AI 提问
        List<Object[]> questionBatch = new ArrayList<>();
        for (int i = 0; i < aiQuestions; i++) {
            Long userId = userIds.get(random.nextInt(userIds.size()));
            String q = QUESTION_TEMPLATES[random.nextInt(QUESTION_TEMPLATES.length)];
            LocalDateTime time = pickDate(random, today).atTime(pickHour(random), random.nextInt(60));
            questionBatch.add(new Object[]{userId, q, "", time});
            if (questionBatch.size() >= 1000) {
                batchInsert("INSERT INTO ai_question_record (user_id, question, answer, create_time) VALUES (?,?,?,?)",
                        questionBatch);
                questionBatch.clear();
            }
        }
        if (!questionBatch.isEmpty()) {
            batchInsert("INSERT INTO ai_question_record (user_id, question, answer, create_time) VALUES (?,?,?,?)",
                    questionBatch);
        }
        log.info("已插入 {} 条模拟 AI 提问", aiQuestions);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("virtualUsers", userIds.size());
        result.put("studyLogs", logs);
        result.put("aiQuestions", aiQuestions);
        result.put("elapsedSeconds", (System.currentTimeMillis() - start) / 1000);
        return result;
    }

    /** 清理全部模拟数据（日志/提问/推荐/虚拟学员） */
    public Map<String, Object> clear() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studyLog", jdbcTemplate.update(
                "DELETE FROM study_log WHERE user_id IN (SELECT id FROM sys_user WHERE is_mock = 1)"));
        result.put("aiQuestion", jdbcTemplate.update(
                "DELETE FROM ai_question_record WHERE user_id IN (SELECT id FROM sys_user WHERE is_mock = 1)"));
        result.put("recommend", jdbcTemplate.update(
                "DELETE FROM analysis_recommend WHERE user_id IN (SELECT id FROM sys_user WHERE is_mock = 1)"));
        result.put("recommendAls", jdbcTemplate.update(
                "DELETE FROM analysis_recommend_als WHERE user_id IN (SELECT id FROM sys_user WHERE is_mock = 1)"));
        result.put("virtualUser", jdbcTemplate.update("DELETE FROM sys_user WHERE is_mock = 1"));
        log.info("模拟数据清理完成: {}", result);
        return result;
    }

    // ---------------- 内部工具 ----------------

    private void batchInsert(String sql, List<Object[]> batch) {
        jdbcTemplate.batchUpdate(sql, batch);
    }

    /** 按权重挑课程（返回 1..17） */
    private int pickCourse(Random random) {
        int total = 0;
        for (int w : COURSE_WEIGHTS) {
            total += w;
        }
        int r = random.nextInt(total);
        int acc = 0;
        for (int i = 0; i < COURSE_WEIGHTS.length; i++) {
            acc += COURSE_WEIGHTS[i];
            if (r < acc) {
                return i + 1;
            }
        }
        return 17;
    }

    /** 近 180 天内随机一天（周末概率降低） */
    private LocalDate pickDate(Random random, LocalDate today) {
        for (int i = 0; i < 5; i++) {
            LocalDate d = today.minusDays(random.nextInt(180));
            if (d.getDayOfWeek().getValue() >= 6 && random.nextDouble() < 0.6) {
                continue;
            }
            return d;
        }
        return today.minusDays(random.nextInt(180));
    }

    /** 老年人活跃时段：早 8-11(35%) / 午 14-17(30%) / 晚 18-21(35%) */
    private int pickHour(Random random) {
        double r = random.nextDouble();
        if (r < 0.35) {
            return 8 + random.nextInt(4);
        } else if (r < 0.65) {
            return 14 + random.nextInt(4);
        }
        return 18 + random.nextInt(4);
    }
}
