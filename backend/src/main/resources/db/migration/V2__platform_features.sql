-- =====================================================================
-- 智慧助老数字学习系统 v2.0 升级脚本
-- 对照开题报告补齐：地区、收藏、积分、奖品、公告、消息、政务、字典、
--                  操作日志、参数配置、AI陪伴话术
-- 幂等：可重复执行（列存在则跳过、表/数据存在则跳过）
-- =====================================================================
USE `elderly_study`;

-- ---------- 安全加列工具（列已存在时跳过） ----------
DROP PROCEDURE IF EXISTS p_add_col;
DELIMITER $$
CREATE PROCEDURE p_add_col(IN t VARCHAR(64), IN c VARCHAR(64), IN ddl VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD COLUMN ', ddl);
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

-- ---------- 1. 用户表：常住地区 + 适老化界面参数 ----------
CALL p_add_col('sys_user', 'province', "`province` VARCHAR(50) DEFAULT NULL COMMENT '常住省'");
CALL p_add_col('sys_user', 'city',     "`city` VARCHAR(50) DEFAULT NULL COMMENT '常住市'");
CALL p_add_col('sys_user', 'district', "`district` VARCHAR(50) DEFAULT NULL COMMENT '常住区县'");
CALL p_add_col('sys_user', 'font_scale', "`font_scale` INT NOT NULL DEFAULT 100 COMMENT '界面字号百分比 100/115/130'");
CALL p_add_col('sys_user', 'high_contrast', "`high_contrast` TINYINT NOT NULL DEFAULT 0 COMMENT '高对比度模式 0关 1开'");

-- ---------- 2. 课程表：地区（NULL=全国通用课程） ----------
CALL p_add_col('course_info', 'province', "`province` VARCHAR(50) DEFAULT NULL COMMENT '所属省(NULL全国通用)'");
CALL p_add_col('course_info', 'city',     "`city` VARCHAR(50) DEFAULT NULL COMMENT '所属市'");
CALL p_add_col('course_info', 'district', "`district` VARCHAR(50) DEFAULT NULL COMMENT '所属区县'");

DROP PROCEDURE IF EXISTS p_add_col;

-- ---------- 3. 定时语音提醒（旧脚本 reminder.sql，未执行过则补建） ----------
CREATE TABLE IF NOT EXISTS `reminder` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT       NOT NULL                COMMENT '所属用户ID',
  `title`           VARCHAR(64)  NOT NULL                COMMENT '提醒标题',
  `content`         VARCHAR(256) NOT NULL                COMMENT '语音播报文本',
  `remind_type`     VARCHAR(16)  NOT NULL DEFAULT 'DAILY' COMMENT 'DAILY/WEEKLY/MONTHLY/ONCE',
  `remind_time`     TIME         NOT NULL                COMMENT '提醒时间',
  `day_of_week`     TINYINT      DEFAULT NULL            COMMENT '周提醒 1-7',
  `day_of_month`    TINYINT      DEFAULT NULL            COMMENT '月提醒 1-31',
  `next_trigger_time` DATETIME   DEFAULT NULL            COMMENT '下次触发时间',
  `enabled`         TINYINT      NOT NULL DEFAULT 1      COMMENT '1开启 0关闭',
  `triggered_today` TINYINT      NOT NULL DEFAULT 0      COMMENT '今日已触发',
  `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_next` (`user_id`, `next_trigger_time`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时语音提醒';

-- ---------- 4. 课程收藏 ----------
CREATE TABLE IF NOT EXISTS `course_favorite` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT NOT NULL COMMENT '用户ID',
  `course_id`   BIGINT NOT NULL COMMENT '课程ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_course` (`user_id`, `course_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程收藏';

-- ---------- 5. 积分规则 ----------
CREATE TABLE IF NOT EXISTS `point_rule` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `rule_code`   VARCHAR(32) NOT NULL COMMENT '规则编码 CHECKIN/COURSE/QA',
  `rule_name`   VARCHAR(64) NOT NULL COMMENT '规则名称',
  `points`      INT NOT NULL DEFAULT 0 COMMENT '每次奖励积分',
  `daily_limit` INT NOT NULL DEFAULT 0 COMMENT '每日上限次数 0不限',
  `enabled`     TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '说明',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分获取规则';

INSERT INTO `point_rule` (rule_code, rule_name, points, daily_limit, enabled, remark)
SELECT 'CHECKIN','每日打卡',5,1,1,'每天首次打卡奖励5积分'
WHERE NOT EXISTS (SELECT 1 FROM point_rule WHERE rule_code='CHECKIN');
INSERT INTO `point_rule` (rule_code, rule_name, points, daily_limit, enabled, remark)
SELECT 'COURSE','完成课程',5,0,1,'每完成一门课程奖励（积分数也可在课程中单独设置）'
WHERE NOT EXISTS (SELECT 1 FROM point_rule WHERE rule_code='COURSE');
INSERT INTO `point_rule` (rule_code, rule_name, points, daily_limit, enabled, remark)
SELECT 'QA','AI问答',2,5,1,'使用AI智能问答，每日最多奖励5次'
WHERE NOT EXISTS (SELECT 1 FROM point_rule WHERE rule_code='QA');

-- ---------- 6. 积分明细 ----------
CREATE TABLE IF NOT EXISTS `point_record` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT NOT NULL COMMENT '用户ID',
  `change_amount` INT NOT NULL COMMENT '积分变动（正=获得 负=消耗）',
  `balance`     INT NOT NULL DEFAULT 0 COMMENT '变动后余额',
  `change_type` VARCHAR(16) NOT NULL COMMENT 'CHECKIN/COURSE/QA/EXCHANGE/ADMIN',
  `remark`      VARCHAR(128) DEFAULT NULL COMMENT '备注',
  `ref_id`      BIGINT DEFAULT NULL COMMENT '关联业务ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分明细';

-- ---------- 7. 福利奖品 ----------
CREATE TABLE IF NOT EXISTS `prize` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(100) NOT NULL COMMENT '奖品名称',
  `cover`       VARCHAR(255) DEFAULT NULL COMMENT '奖品图片',
  `points_cost` INT NOT NULL DEFAULT 0 COMMENT '兑换所需积分',
  `stock`       INT NOT NULL DEFAULT 0 COMMENT '库存',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '奖品说明',
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分福利奖品';

INSERT INTO `prize` (name, points_cost, stock, description, status)
SELECT '农家鸡蛋一份（10枚）',100,50,'凭兑换码到社区服务点领取',1
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name='农家鸡蛋一份（10枚）');
INSERT INTO `prize` (name, points_cost, stock, description, status)
SELECT '抽纸一提（3包装）',200,40,'生活用品，社区服务点领取',1
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name='抽纸一提（3包装）');
INSERT INTO `prize` (name, points_cost, stock, description, status)
SELECT '食堂餐券一张',300,30,'社区老年食堂可用',1
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name='食堂餐券一张');
INSERT INTO `prize` (name, points_cost, stock, description, status)
SELECT '保温杯一个',500,20,'适老防滑保温杯',1
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name='保温杯一个');
INSERT INTO `prize` (name, points_cost, stock, description, status)
SELECT '免费健康体检套餐',800,10,'社区卫生服务中心老年体检套餐',1
WHERE NOT EXISTS (SELECT 1 FROM prize WHERE name='免费健康体检套餐');

-- ---------- 8. 奖品兑换记录 ----------
CREATE TABLE IF NOT EXISTS `prize_exchange` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT NOT NULL,
  `prize_id`    BIGINT NOT NULL,
  `prize_name`  VARCHAR(100) NOT NULL COMMENT '奖品名称快照',
  `points_cost` INT NOT NULL COMMENT '消耗积分快照',
  `status`      VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待发放 DONE已发放 CANCEL已取消',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `handle_time` DATETIME DEFAULT NULL COMMENT '发放时间',
  `handler_id`  BIGINT DEFAULT NULL COMMENT '发放管理员ID',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='奖品兑换记录';

-- ---------- 9. 平台公告 ----------
CREATE TABLE IF NOT EXISTS `announcement` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `title`       VARCHAR(200) NOT NULL COMMENT '公告标题',
  `content`     TEXT COMMENT '公告内容',
  `ann_type`    VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM系统公告 GOV政务消息',
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '1发布 0下架',
  `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',
  `create_by`   BIGINT DEFAULT NULL COMMENT '发布人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_time` (`status`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台公告资讯';

INSERT INTO `announcement` (title, content, ann_type, status, publish_time, create_by)
SELECT '欢迎使用老年数字素养学习系统','各位叔叔阿姨好！平台开设了政务办事、手机操作、反诈安全、便民生活、文娱休闲五类课程，所有课程都可以"听"着学，还有每日打卡积分兑换福利活动，快来学习吧！','SYSTEM',1,NOW(),1
WHERE NOT EXISTS (SELECT 1 FROM announcement LIMIT 1);
INSERT INTO `announcement` (title, content, ann_type, status, publish_time, create_by)
SELECT '谨防"免费领鸡蛋"养老诈骗提示','近期有不法分子以免费领礼品、健康讲座为名向老年人推销假冒保健品。请大家牢记：不轻信、不透露、不转账，遇到可疑情况请及时拨打110或全国反诈专线96110。','SYSTEM',1,NOW(),1
WHERE NOT EXISTS (SELECT 1 FROM announcement WHERE title='谨防"免费领鸡蛋"养老诈骗提示');

-- ---------- 10. 用户消息通知 ----------
CREATE TABLE IF NOT EXISTS `user_message` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT NOT NULL COMMENT '接收用户ID',
  `title`       VARCHAR(128) NOT NULL COMMENT '消息标题',
  `content`     VARCHAR(1000) DEFAULT NULL COMMENT '消息内容',
  `msg_type`    VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM/REMINDER/GOV/EXCHANGE',
  `biz_id`      BIGINT DEFAULT NULL COMMENT '关联业务ID',
  `is_read`     TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息通知';

-- ---------- 11. 政务政策（本地政策库） ----------
CREATE TABLE IF NOT EXISTS `gov_policy` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT,
  `title`         VARCHAR(200) NOT NULL COMMENT '政策标题',
  `content`       TEXT COMMENT '政策摘要',
  `source`        VARCHAR(128) DEFAULT NULL COMMENT '发布单位',
  `policy_level`  VARCHAR(16) NOT NULL DEFAULT 'NATIONAL' COMMENT 'NATIONAL全国 PROVINCE省级 CITY市级',
  `province`      VARCHAR(50) DEFAULT NULL COMMENT '省(全国政策为空)',
  `city`          VARCHAR(50) DEFAULT NULL,
  `gov_url`       VARCHAR(255) DEFAULT NULL COMMENT '政策原文链接',
  `publish_time`  DATE DEFAULT NULL COMMENT '发布日期',
  `status`        TINYINT NOT NULL DEFAULT 1,
  `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_region_level` (province, city, policy_level, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='政务政策库';

-- 全国通用政策
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '关于切实解决老年人运用智能技术困难的实施方案','国务院办公厅印发（国办发〔2020〕45号）：要求坚持传统服务方式与智能化服务创新并行，完善"健康码"通行、就医、消费、办事、文娱等高频事项适老化服务，不得拒收现金，保留线下办理渠道。','国务院办公厅','NATIONAL',NULL,NULL,'https://www.gov.cn/zhengce/content/2020-11/24/content_5563804.htm','2020-11-24',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%切实解决老年人运用智能技术困难%');
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '关于发展银发经济增进老年人福祉的意见','国务院办公厅2024年印发：提出开展数字适老化能力提升工程，推进互联网应用、移动终端适老化改造，发展老年数字教育，帮助老年人跨越数字鸿沟。','国务院办公厅','NATIONAL',NULL,NULL,'https://www.gov.cn/zhengce/content/202401/content_6925837.htm','2024-01-15',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%银发经济%');
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '关于推进信息无障碍的指导意见','工信部、中国残联等提出：加快推动互联网网站、移动互联网应用无障碍与适老化改造，鼓励企业提供语音朗读、放大字体、远程协助等便老功能。','工业和信息化部','NATIONAL',NULL,NULL,'https://www.gov.cn/zhengce/zhengceku/2020-09/23/content_5546219.htm','2020-09-11',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%信息无障碍%');
-- 地方政策示例
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '北京市老年人养老服务补贴津贴政策','北京市老年人养老服务补贴津贴包括困难老年人养老服务补贴、失能老年人护理补贴、高龄老年人津贴。80周岁及以上本市户籍老年人可申领高龄津贴，可通过"北京通"或街道政务服务窗口办理。','北京市民政局','CITY','北京市','北京市','https://mzj.beijing.gov.cn/','2023-01-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%北京市老年人养老服务补贴%');
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '上海市老年综合津贴制度','上海市对年满65周岁的户籍老年人按年龄分档发放综合津贴（每月75元至600元），通过"上海市敬老卡"按季度发放，可在"一网通办"在线申请。','上海市民政局','CITY','上海市','上海市','https://mzj.sh.gov.cn/','2023-06-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%上海市老年综合津贴%');
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '广东省老年人权益保障条例','条例规定全社会应当弘扬敬老、养老、助老传统美德，为老年人提供优待和照顾；各级政务服务平台应当为老年人办事提供便利，保留人工窗口。','广东省人大常委会','PROVINCE','广东省',NULL,'https://www.gd.gov.cn/','2022-01-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%广东省老年人权益保障%');
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '浙江省智慧健康养老服务推广政策','浙江省推进"浙里康养"建设，鼓励发展智慧养老、远程诊疗、紧急救援等数字化为老服务，老年人可通过"浙里办"办理养老补贴、高龄津贴等事项。','浙江省人民政府','PROVINCE','浙江省',NULL,'https://www.zj.gov.cn/','2023-03-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%浙里康养%');
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '江苏省老年人优待办法','江苏省对老年人进入政府兴办的公园、景点、公共文化场馆实行免费或优惠；60周岁以上老年人可凭有效证件享受公交优待，相关事项可在"苏服办"查询办理。','江苏省人民政府','PROVINCE','江苏省',NULL,'https://www.jiangsu.gov.cn/','2022-09-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%江苏省老年人优待%');
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '四川省天府通办适老化服务','四川省"天府通办"政务服务平台开通长者服务专区，提供高龄津贴、养老待遇认证、医保查询等高频事项大字版办理入口。','四川省人民政府办公厅','PROVINCE','四川省',NULL,'https://www.sczwfw.gov.cn/','2023-05-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%天府通办适老化%');

-- ---------- 12. 政务办事直达入口 ----------
CREATE TABLE IF NOT EXISTS `gov_service` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(64) NOT NULL COMMENT '服务名称',
  `icon`        VARCHAR(16) DEFAULT '🏛' COMMENT '图标(emoji)',
  `service_url` VARCHAR(255) NOT NULL COMMENT '直达链接',
  `scope`       VARCHAR(16) NOT NULL DEFAULT 'NATIONAL' COMMENT 'NATIONAL全国 LOCAL地方',
  `province`    VARCHAR(50) DEFAULT NULL,
  `city`        VARCHAR(50) DEFAULT NULL,
  `sort`        INT NOT NULL DEFAULT 0,
  `status`      TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_region` (province, city, scope, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='政务办事直达入口';

INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '国家政务服务平台','🏛','https://gjzwfw.www.gov.cn/','NATIONAL',NULL,NULL,1,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='国家政务服务平台');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '社保待遇资格认证','👴','http://si.12333.gov.cn/','NATIONAL',NULL,NULL,2,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='社保待遇资格认证');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '国家医保服务平台','🏥','https://fuwu.nhsa.gov.cn/','NATIONAL',NULL,NULL,3,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='国家医保服务平台');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '铁路12306购票','🚄','https://www.12306.cn/','NATIONAL',NULL,NULL,4,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='铁路12306购票');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '交管12123','🚗','https://m.122.gov.cn/','NATIONAL',NULL,NULL,5,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='交管12123');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '北京政务服务','🏛','https://banshi.beijing.gov.cn/','LOCAL','北京市','北京市',1,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='北京政务服务');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '上海一网通办','🏛','https://zwdt.sh.gov.cn/','LOCAL','上海市','上海市',1,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='上海一网通办');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '广东粤省事','🏛','https://www.gdzwfw.gov.cn/','LOCAL','广东省',NULL,1,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='广东粤省事');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '浙江浙里办','🏛','https://www.zjzwfw.gov.cn/','LOCAL','浙江省',NULL,1,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='浙江浙里办');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '江苏苏服办','🏛','https://www.jiangsu.gov.cn/col/col88325/index.html','LOCAL','江苏省',NULL,1,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='江苏苏服办');
INSERT INTO `gov_service` (name, icon, service_url, scope, province, city, sort, status)
SELECT '四川天府通办','🏛','https://www.sczwfw.gov.cn/','LOCAL','四川省',NULL,1,1
WHERE NOT EXISTS (SELECT 1 FROM gov_service WHERE name='四川天府通办');

-- ---------- 13. 系统字典 ----------
CREATE TABLE IF NOT EXISTS `sys_dict` (
  `id`        BIGINT NOT NULL AUTO_INCREMENT,
  `dict_code` VARCHAR(64) NOT NULL COMMENT '字典编码',
  `dict_name` VARCHAR(64) NOT NULL COMMENT '字典名称',
  `status`    TINYINT NOT NULL DEFAULT 1,
  `remark`    VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典类型';

CREATE TABLE IF NOT EXISTS `sys_dict_item` (
  `id`         BIGINT NOT NULL AUTO_INCREMENT,
  `dict_code`  VARCHAR(64) NOT NULL COMMENT '字典编码',
  `item_text`  VARCHAR(64) NOT NULL COMMENT '显示文本',
  `item_value` VARCHAR(64) NOT NULL COMMENT '字典值',
  `sort`       INT NOT NULL DEFAULT 0,
  `status`     TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_dict` (`dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统字典项';

INSERT INTO `sys_dict` (dict_code, dict_name, remark)
SELECT 'message_type','消息类型','用户消息分类'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code='message_type');
INSERT INTO `sys_dict` (dict_code, dict_name, remark)
SELECT 'gov_level','政策层级','全国/省/市'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code='gov_level');
INSERT INTO `sys_dict` (dict_code, dict_name, remark)
SELECT 'exchange_status','兑换状态','奖品兑换处理状态'
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_code='exchange_status');

INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'message_type','系统公告','SYSTEM',1,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='message_type' AND item_value='SYSTEM');
INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'message_type','政务消息','GOV',2,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='message_type' AND item_value='GOV');
INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'message_type','提醒通知','REMINDER',3,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='message_type' AND item_value='REMINDER');
INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'message_type','兑换通知','EXCHANGE',4,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='message_type' AND item_value='EXCHANGE');
INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'gov_level','全国通用','NATIONAL',1,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='gov_level' AND item_value='NATIONAL');
INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'gov_level','省级政策','PROVINCE',2,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='gov_level' AND item_value='PROVINCE');
INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'gov_level','市级政策','CITY',3,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='gov_level' AND item_value='CITY');
INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'exchange_status','待发放','PENDING',1,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='exchange_status' AND item_value='PENDING');
INSERT INTO `sys_dict_item` (dict_code, item_text, item_value, sort, status)
SELECT 'exchange_status','已发放','DONE',2,1
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item WHERE dict_code='exchange_status' AND item_value='DONE');

-- ---------- 14. 系统参数配置（TTS / AI 等后台可配） ----------
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`           BIGINT NOT NULL AUTO_INCREMENT,
  `config_key`   VARCHAR(64) NOT NULL,
  `config_value` VARCHAR(1000) DEFAULT NULL,
  `config_name`  VARCHAR(128) DEFAULT NULL,
  `remark`       VARCHAR(255) DEFAULT NULL,
  `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数配置';

INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'tts_speed','1.0','TTS默认语速','0.5-2.0，越大越快'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='tts_speed');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'tts_volume','1.0','TTS默认音量','0-1'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='tts_volume');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'tts_default_voice','default','TTS默认音色','音色key'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='tts_default_voice');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'tts_rate','','TTS语速百分比(edge-tts)','如 -15% 表示慢速，+10% 表示快速，留空用默认'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='tts_rate');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'tts_pitch','','TTS音调(edge-tts)','如 +10Hz，留空用默认'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='tts_pitch');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'ai_enabled','true','是否启用AI大模型','false时仅用内置知识库'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='ai_enabled');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'ai_api_url','https://api.deepseek.com/chat/completions','AI接口地址','OpenAI兼容chat/completions'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='ai_api_url');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'ai_api_key','','AI密钥(API Key)','留空则使用系统配置文件中的密钥'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='ai_api_key');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'ai_model','deepseek-chat','AI模型名称','如 deepseek-chat'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='ai_model');
INSERT INTO `sys_config` (config_key, config_value, config_name, remark)
SELECT 'system_name','老年数字素养学习系统','系统名称','展示用'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key='system_name');

-- ---------- 15. AI 数字陪伴话术 ----------
CREATE TABLE IF NOT EXISTS `ai_companion_script` (
  `id`        BIGINT NOT NULL AUTO_INCREMENT,
  `scene`     VARCHAR(32) NOT NULL COMMENT 'GREETING问候/FESTIVAL节日/ENCOURAGE鼓励/RECOMMEND推荐',
  `keyword`   VARCHAR(64) DEFAULT NULL COMMENT '节日/场景关键词 如 春节 重阳',
  `title`     VARCHAR(64) NOT NULL COMMENT '话术标题',
  `content`   VARCHAR(1000) NOT NULL COMMENT '话术内容',
  `enabled`   TINYINT NOT NULL DEFAULT 1,
  `sort`      INT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_scene` (scene, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI数字陪伴话术';

INSERT INTO `ai_companion_script` (scene, keyword, title, content, enabled, sort)
SELECT 'GREETING',NULL,'早安问候','早上好呀！新的一天开始啦，记得打开平台学一节小课程、完成今日打卡，积分可以换鸡蛋和餐券哦。',1,1
WHERE NOT EXISTS (SELECT 1 FROM ai_companion_script WHERE scene='GREETING' LIMIT 1);
INSERT INTO `ai_companion_script` (scene, keyword, title, content, enabled, sort)
SELECT 'FESTIVAL','春节','春节祝福','新春快乐！祝您和家人身体健康、万事如意。过年期间接到"中奖""退补贴"电话千万别信，守好钱袋子过个安心年！',1,1
WHERE NOT EXISTS (SELECT 1 FROM ai_companion_script WHERE keyword='春节');
INSERT INTO `ai_companion_script` (scene, keyword, title, content, enabled, sort)
SELECT 'FESTIVAL','重阳节','重阳祝福','九九重阳，祝您健康长寿！记得今天给老朋友们打个视频电话，不会操作的话可以在"手机操作"课程里学一学。',1,2
WHERE NOT EXISTS (SELECT 1 FROM ai_companion_script WHERE keyword='重阳节');
INSERT INTO `ai_companion_script` (scene, keyword, title, content, enabled, sort)
SELECT 'FESTIVAL','中秋节','中秋祝福','中秋佳节，阖家团圆！吃月饼赏月的同时，小心"高价月饼""保健品礼盒"推销骗局哦。',1,3
WHERE NOT EXISTS (SELECT 1 FROM ai_companion_script WHERE keyword='中秋节');
INSERT INTO `ai_companion_script` (scene, keyword, title, content, enabled, sort)
SELECT 'ENCOURAGE',NULL,'学习鼓励','您已经坚持学习好几天啦，给您点赞！活到老学到老，学会用智能手机，生活更方便。',1,1
WHERE NOT EXISTS (SELECT 1 FROM ai_companion_script WHERE scene='ENCOURAGE' LIMIT 1);
INSERT INTO `ai_companion_script` (scene, keyword, title, content, enabled, sort)
SELECT 'RECOMMEND',NULL,'课程推荐提醒','根据您最近的学习情况，推荐您看一看"反诈安全"分类里的课程，多学一点，骗子就离您远一点。',1,1
WHERE NOT EXISTS (SELECT 1 FROM ai_companion_script WHERE scene='RECOMMEND' LIMIT 1);

-- ---------- 16. 管理员操作日志 ----------
CREATE TABLE IF NOT EXISTS `sys_operation_log` (
  `id`             BIGINT NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT DEFAULT NULL COMMENT '操作人ID',
  `username`       VARCHAR(64) DEFAULT NULL COMMENT '操作人账号',
  `module`         VARCHAR(64) DEFAULT NULL COMMENT '功能模块',
  `description`    VARCHAR(255) DEFAULT NULL COMMENT '操作描述',
  `request_method` VARCHAR(10) DEFAULT NULL COMMENT 'HTTP方法',
  `request_url`    VARCHAR(255) DEFAULT NULL COMMENT '请求地址',
  `request_params` TEXT COMMENT '请求参数',
  `ip`             VARCHAR(64) DEFAULT NULL COMMENT 'IP地址',
  `status`         TINYINT NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
  `error_msg`      VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  `cost_time`      BIGINT DEFAULT 0 COMMENT '耗时(毫秒)',
  `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_module` (`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统操作日志';

-- ---------- 17. 管理端菜单补齐（RBAC 菜单数据） ----------
INSERT INTO `sys_menu` (parent_id, menu_name, path, component, perms, sort)
SELECT NULL, '公告管理', '/admin/announcements', 'admin/Announcements', 'announcement:list', 6
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/announcements');
INSERT INTO `sys_menu` (parent_id, menu_name, path, component, perms, sort)
SELECT NULL, '积分福利', '/admin/prizes', 'admin/Prizes', 'prize:list', 7
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/prizes');
INSERT INTO `sys_menu` (parent_id, menu_name, path, component, perms, sort)
SELECT NULL, '政务管理', '/admin/gov', 'admin/GovAdmin', 'gov:list', 8
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/gov');
INSERT INTO `sys_menu` (parent_id, menu_name, path, component, perms, sort)
SELECT NULL, '陪伴话术', '/admin/companion', 'admin/Companion', 'companion:list', 9
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/companion');
INSERT INTO `sys_menu` (parent_id, menu_name, path, component, perms, sort)
SELECT NULL, '音频资源', '/admin/tts-audio', 'admin/TtsAudio', 'tts:audio', 10
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/tts-audio');
INSERT INTO `sys_menu` (parent_id, menu_name, path, component, perms, sort)
SELECT NULL, '数据字典', '/admin/dict', 'admin/Dict', 'dict:list', 11
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/dict');
INSERT INTO `sys_menu` (parent_id, menu_name, path, component, perms, sort)
SELECT NULL, '操作日志', '/admin/logs', 'admin/OpLogs', 'log:list', 12
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/logs');

-- 给所有存量用户补发初始公告消息
INSERT INTO `user_message` (user_id, title, content, msg_type, biz_id, is_read)
SELECT u.id, a.title, a.content, 'SYSTEM', a.id, 0
FROM sys_user u
CROSS JOIN announcement a
WHERE a.status = 1
  AND NOT EXISTS (SELECT 1 FROM user_message m WHERE m.user_id = u.id AND m.biz_id = a.id AND m.msg_type='SYSTEM');
