ALTER TABLE analysis_text_classify
  ADD COLUMN avg_confidence DOUBLE NULL COMMENT '已接受分类的平均模型置信度（非准确率）；未确定及旧结果为空';
ALTER TABLE analysis_text_classify
  ADD COLUMN min_confidence DOUBLE NULL COMMENT '已接受分类的最低模型置信度（非准确率）';
ALTER TABLE analysis_text_classify
  ADD COLUMN high_confidence_count BIGINT NULL COMMENT '已接受分类中模型置信度达到0.8的提问数；旧结果为空';

CREATE TABLE analysis_question_label (
  question_id BIGINT NOT NULL COMMENT '提问记录编号，同一记录仅一条人工标签',
  question_text TEXT NOT NULL COMMENT '人工标注时的原始提问，防止文本变更后沿用标签',
  category VARCHAR(64) NOT NULL COMMENT '人工确认的业务类别或未确定业务；仅用于独立测试',
  reviewer_id BIGINT NOT NULL COMMENT '人工标注者用户编号',
  reviewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近人工标注时间',
  PRIMARY KEY (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问答分类人工测试标签（不参与模型训练）';
