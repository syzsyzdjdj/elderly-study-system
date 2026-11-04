ALTER TABLE sys_user ADD COLUMN is_mock TINYINT NOT NULL DEFAULT 0 COMMENT '模拟用户标记';
-- 仅回填历史生成器的用户名与昵称组合，不凭用户名通配符标记普通用户。
UPDATE sys_user SET is_mock = 1
WHERE username REGEXP '^virtual[0-9]+$' AND nickname LIKE '虚拟学员%';
ALTER TABLE analysis_association_rule ADD COLUMN lift DOUBLE NULL COMMENT '提升度';
CREATE TABLE analysis_model_metric (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task VARCHAR(40) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  data_scope VARCHAR(16) NOT NULL,
  metric VARCHAR(64) NOT NULL,
  value DOUBLE NULL,
  samples BIGINT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL,
  detail VARCHAR(1000) NOT NULL DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_metric_task (task)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
