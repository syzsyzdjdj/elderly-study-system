CREATE TABLE analysis_question_prediction (
  question_id BIGINT NOT NULL COMMENT '提问编号',
  question_hash VARCHAR(64) NOT NULL COMMENT 'Spark输入提问原文的SHA256，变更后预测失效',
  category VARCHAR(64) NOT NULL COMMENT 'Spark最终业务类别或未确定业务',
  confidence DOUBLE NULL COMMENT '模型置信度，未确定业务为空，不代表正确率',
  run_id VARCHAR(64) NOT NULL COMMENT 'Spark分析批次',
  data_scope VARCHAR(16) NOT NULL COMMENT 'real或mock',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '保存时间',
  PRIMARY KEY (question_id),
  KEY idx_prediction_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Spark逐条提问预测，不作为人工测试标签';
