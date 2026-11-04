-- =====================================================
-- V3: Spark 文本分类结果表（地区 × 业务类别 双维度问答量统计）
-- 对应任务：text_classify（jieba 分词 + TF-IDF + 朴素贝叶斯）
-- =====================================================
CREATE TABLE IF NOT EXISTS analysis_text_classify (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  province       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '省份（未绑定地区为未知地区）',
  category       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '预测业务类别（课程顶级分类名）',
  question_count BIGINT       NOT NULL DEFAULT 0  COMMENT '该地区该类别的问答数量',
  create_time    DATETIME     NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分析时间',
  PRIMARY KEY (id),
  KEY idx_atc_province (province),
  KEY idx_atc_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Spark问答文本分类结果（地区×业务双维度）';
