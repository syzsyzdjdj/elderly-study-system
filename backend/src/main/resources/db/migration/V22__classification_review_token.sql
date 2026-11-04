ALTER TABLE analysis_question_label
  ADD COLUMN review_token VARCHAR(36) NOT NULL DEFAULT '' COMMENT '每次保存时更新的复核令牌，用于防止陈旧页面覆盖或删除新标签';
