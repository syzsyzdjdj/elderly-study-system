-- 定时提醒模板（管理员统一配置，老人端一键启用）
CREATE TABLE IF NOT EXISTS reminder_template (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    template_name VARCHAR(64) NOT NULL COMMENT '模板名称',
    title        VARCHAR(64)  NOT NULL COMMENT '提醒标题，如：学习打卡',
    content      VARCHAR(500) NOT NULL COMMENT '语音播报文本',
    remind_type  VARCHAR(16)  NOT NULL DEFAULT 'DAILY' COMMENT '重复类型: DAILY/WEEKLY/MONTHLY/ONCE',
    remind_time  TIME         NOT NULL DEFAULT '09:00:00' COMMENT '提醒时间 HH:mm:ss',
    day_of_week  INT          DEFAULT NULL COMMENT '周提醒: 1-7(周一到周日)',
    day_of_month INT          DEFAULT NULL COMMENT '月提醒: 1-31',
    enabled      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用: 1=启用 0=停用',
    sort         INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT ='定时提醒模板（管理员配置，老人端一键选用）';

-- 预置 4 条适老场景模板
INSERT INTO reminder_template (template_name, title, content, remind_type, remind_time, day_of_week, day_of_month, enabled, sort)
VALUES ('每日学习打卡', '学习打卡',
        '您好，今天的适老课程还没有学习哦，坚持每天学一点，智能手机越用越熟练！',
        'DAILY', '19:00:00', NULL, NULL, 1, 1),
       ('养老待遇资格认证提醒', '养老待遇资格认证',
        '温馨提醒：本月养老金领取资格认证时间快到啦，请记得打开"养老认证"服务完成刷脸认证，以免影响待遇发放。',
        'MONTHLY', '09:00:00', NULL, 1, 1, 2),
       ('医保缴费提醒', '医保缴费提醒',
        '您好，城乡居民基本医疗保险集中缴费期即将开始，请及时关注缴费通知并完成缴费。',
        'MONTHLY', '10:00:00', NULL, 15, 1, 3),
       ('每周反诈知识学习', '反诈知识学习',
        '本周反诈小课堂更新啦，花三分钟学一个防骗案例，守好自己的养老钱袋子。',
        'WEEKLY', '15:00:00', 3, NULL, 1, 4);
