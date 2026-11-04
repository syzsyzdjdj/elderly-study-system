-- V15: 修复课程分类归属错乱
-- 旧课程(category_id)指向早期分类体系的ID，与现行分类树(init_categories)对不上，
-- 导致按"政务办事/就医挂号"等子分类筛选时列表为空。
-- 处理：①确保标准分类树存在 ②按课程标题重新映射到正确子分类 ③清理残留旧分类

-- ① 标准分类树（幂等）
INSERT INTO course_category (id, parent_id, category_name, sort, status, create_time) VALUES
  (1, 0, '智能手机操作', 1, 1, NOW()),
  (2, 0, '生活服务', 2, 1, NOW()),
  (3, 0, '反诈防骗', 3, 1, NOW()),
  (4, 0, '健康养生', 4, 1, NOW()),
  (5, 0, '休闲娱乐', 5, 1, NOW()),
  (11, 1, '基础操作', 1, 1, NOW()),
  (12, 1, '社交聊天', 2, 1, NOW()),
  (13, 1, '支付购物', 3, 1, NOW()),
  (14, 1, '出行交通', 4, 1, NOW()),
  (21, 2, '就医挂号', 1, 1, NOW()),
  (22, 2, '政务办事', 2, 1, NOW()),
  (23, 2, '便民缴费', 3, 1, NOW()),
  (31, 3, '电信诈骗', 1, 1, NOW()),
  (32, 3, '网络安全', 2, 1, NOW()),
  (41, 4, '运动锻炼', 1, 1, NOW()),
  (42, 4, '饮食营养', 2, 1, NOW()),
  (51, 5, '影音娱乐', 1, 1, NOW()),
  (52, 5, '兴趣爱好', 2, 1, NOW())
ON DUPLICATE KEY UPDATE category_name = VALUES(category_name);

-- ② 按标题把 17 门旧课程归位到正确子分类
UPDATE `course_info` SET `category_id` = 22 WHERE `title` = '手机完成养老金资格认证';   -- 政务办事
UPDATE `course_info` SET `category_id` = 22 WHERE `title` = '医保电子凭证申领与使用';   -- 政务办事
UPDATE `course_info` SET `category_id` = 22 WHERE `title` = '异地就医备案办理指南';     -- 政务办事
UPDATE `course_info` SET `category_id` = 22 WHERE `title` = '电子社保卡领取与使用';     -- 政务办事
UPDATE `course_info` SET `category_id` = 12 WHERE `title` = '微信视频通话入门';         -- 社交聊天
UPDATE `course_info` SET `category_id` = 12 WHERE `title` = '微信发红包与收付款';       -- 社交聊天
UPDATE `course_info` SET `category_id` = 13 WHERE `title` = '微信支付安全设置';         -- 支付购物
UPDATE `course_info` SET `category_id` = 11 WHERE `title` = '手机字体调大与声音设置';   -- 基础操作
UPDATE `course_info` SET `category_id` = 11 WHERE `title` = '手机拍照与照片管理';       -- 基础操作
UPDATE `course_info` SET `category_id` = 31 WHERE `title` = '识破常见电信诈骗套路';     -- 电信诈骗
UPDATE `course_info` SET `category_id` = 31 WHERE `title` = '保健品骗局识别指南';       -- 电信诈骗
UPDATE `course_info` SET `category_id` = 31 WHERE `title` = '刷单返利骗局警示';         -- 电信诈骗
UPDATE `course_info` SET `category_id` = 32 WHERE `title` = '网络谣言识别与应对';       -- 网络安全
UPDATE `course_info` SET `category_id` = 21 WHERE `title` = '网上预约挂号操作指南';     -- 就医挂号
UPDATE `course_info` SET `category_id` = 21 WHERE `title` = '在线问诊与药店买药';       -- 就医挂号
UPDATE `course_info` SET `category_id` = 23 WHERE `title` = '水电燃气线上缴费';         -- 便民缴费
UPDATE `course_info` SET `category_id` = 14 WHERE `title` = '网约车与公交出行';         -- 出行交通

-- ③ 清理不在标准树内的残留旧分类（养老金认证/医保办理/网上挂号/生活缴费等历史行）
DELETE FROM `course_category`
WHERE `id` NOT IN (1,2,3,4,5,11,12,13,14,21,22,23,31,32,41,42,51,52);
