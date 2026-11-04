-- =====================================================
-- 老年学堂预置分类树（可重复执行，已有同名分类会跳过）
-- =====================================================

-- 顶级分类
INSERT INTO course_category (id, parent_id, category_name, sort, status, create_time) VALUES
  (1, 0, '智能手机操作', 1, 1, NOW()),
  (2, 0, '生活服务', 2, 1, NOW()),
  (3, 0, '反诈防骗', 3, 1, NOW()),
  (4, 0, '健康养生', 4, 1, NOW()),
  (5, 0, '休闲娱乐', 5, 1, NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- 智能手机操作 子分类
INSERT INTO course_category (id, parent_id, category_name, sort, status, create_time) VALUES
  (11, 1, '基础操作', 1, 1, NOW()),
  (12, 1, '社交聊天', 2, 1, NOW()),
  (13, 1, '支付购物', 3, 1, NOW()),
  (14, 1, '出行交通', 4, 1, NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- 生活服务 子分类
INSERT INTO course_category (id, parent_id, category_name, sort, status, create_time) VALUES
  (21, 2, '就医挂号', 1, 1, NOW()),
  (22, 2, '政务办事', 2, 1, NOW()),
  (23, 2, '便民缴费', 3, 1, NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- 反诈防骗 子分类
INSERT INTO course_category (id, parent_id, category_name, sort, status, create_time) VALUES
  (31, 3, '电信诈骗', 1, 1, NOW()),
  (32, 3, '网络安全', 2, 1, NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- 健康养生 子分类
INSERT INTO course_category (id, parent_id, category_name, sort, status, create_time) VALUES
  (41, 4, '运动锻炼', 1, 1, NOW()),
  (42, 4, '饮食营养', 2, 1, NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- 休闲娱乐 子分类
INSERT INTO course_category (id, parent_id, category_name, sort, status, create_time) VALUES
  (51, 5, '影音娱乐', 1, 1, NOW()),
  (52, 5, '兴趣爱好', 2, 1, NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);
