-- V10: 新增最新国家政策（2025-2026年）
-- 补充银发经济、数字素养、适老化方向最新文件

-- 1. 2026年提升全民数字素养与技能工作要点（2026年5月）
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '2026年提升全民数字素养与技能工作要点',
'中央网信办、教育部、工业和信息化部、人力资源社会保障部联合印发，部署6个方面15项重点任务：完善数字素养培育体系、深化数字应用场景建设、提升全民人工智能素养、促进数字普惠包容发展、营造安全有序网络空间、健全协同联动工作机制。明确提出深化信息无障碍环境建设、打造数字助老惠民公益项目，帮助老年人跨越数字鸿沟。',
'中央网信办 教育部 工业和信息化部 人力资源社会保障部',
'NATIONAL', NULL, NULL,
'https://www.gov.cn/lianbo/202605/content_7070654.htm',
'2026-05-29', 1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%2026年提升全民数字素养%');

-- 2. 关于培育养老服务经营主体 促进银发经济发展的若干措施（2025年12月）
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '关于培育养老服务经营主体 促进银发经济发展的若干措施',
'民政部、国家发展改革委、工业和信息化部等8部门联合印发（民发〔2025〕66号），围绕推进品牌化建设、促进养老服务市场化供需对接、大力支持科技赋能养老服务三方面提出具体措施。明确加强养老服务领域数智化升级，将大数据、云计算、人工智能、北斗技术运用到老年人健康监测、安全报警及个性化定制服务中，鼓励养老服务机器人产业发展。',
'民政部 国家发展改革委 工业和信息化部 财政部 自然资源部 商务部 市场监管总局 国家知识产权局',
'NATIONAL', NULL, NULL,
'https://www.gov.cn/zhengce/zhengceku/202601/content_7054605.htm',
'2025-12-25', 1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%培育养老服务经营主体%');

-- 3. 促进数字技术适老化高质量发展工作方案（2023年12月，目标到2025年底）
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '促进数字技术适老化高质量发展工作方案',
'工业和信息化部印发，提出到2025年底数字技术适老化标准规范体系更加健全、适老化改造规模有效扩大。重点任务包括：完善标准规范体系（推动20项以上急需标准出台）、丰富硬件产品供给（指导企业研制100款以上适老化智能产品）、深化互联网应用适老化及无障碍改造（聚焦新闻资讯、社交通讯、生活购物、医疗健康、金融服务、学习教育、交通出行七大领域）、强化适老化数字技术创新应用能力。',
'工业和信息化部',
'NATIONAL', NULL, NULL,
'https://www.gov.cn/govweb/zhengce/zhengceku/202312/P020231228427669447392.pdf',
'2023-12-15', 1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%数字技术适老化高质量发展%');
