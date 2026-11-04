-- =====================================================
-- 定时语音提醒表
-- =====================================================
CREATE TABLE IF NOT EXISTS `reminder` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT       NOT NULL                COMMENT '所属用户ID',
  `title`           VARCHAR(64)  NOT NULL                COMMENT '提醒标题，如"学习打卡"',
  `content`         VARCHAR(256) NOT NULL                COMMENT '语音播报文本，如"老王您好，今天还没打卡哦"',
  `remind_type`     VARCHAR(16)  NOT NULL DEFAULT 'DAILY' COMMENT '重复类型: DAILY=每天, WEEKLY=每周, MONTHLY=每月, ONCE=仅一次',
  `remind_time`     TIME         NOT NULL                COMMENT '提醒时间 HH:mm:ss',
  `day_of_week`     TINYINT      DEFAULT NULL            COMMENT '周提醒用: 1-7(周一到周日)',
  `day_of_month`    TINYINT      DEFAULT NULL            COMMENT '月提醒用: 1-31',
  `next_trigger_time` DATETIME   DEFAULT NULL            COMMENT '下次触发时间(服务端计算)',
  `enabled`         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否开启: 1=开启 0=关闭',
  `triggered_today` TINYINT      NOT NULL DEFAULT 0      COMMENT '今日是否已触发过(防重复)',
  `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_next` (`user_id`, `next_trigger_time`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时语音提醒';

-- 可选：给演示用户预置几条提醒（用之前的 AI 演示用户 ID）
-- INSERT INTO reminder (user_id, title, content, remind_type, remind_time, day_of_week, next_trigger_time, enabled)
-- VALUES (1, '每日学习打卡', '您有一条学习打卡待完成，去学 15 分钟课程吧', 'DAILY', '09:00:00', NULL, CURDATE() + INTERVAL 1 DAY + INTERVAL 9 HOUR, 1);
