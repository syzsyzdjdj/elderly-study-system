-- V12: 补充山西、吉林、黑龙江、贵州四省及省会城市政策

-- 山西省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '山西省养老服务条例','山西省规定县级以上人民政府应推进养老服务信息化建设，为老年人提供智慧居家养老、紧急呼叫等服务。60周岁以上老年人享受公立医院优先就诊、公交优待。可在"三晋通"APP办理养老认证、社保查询、高龄津贴申请等事项，APP提供大字版模式。','山西省人大常委会','PROVINCE','山西省',NULL,'https://www.shanxi.gov.cn/','2022-11-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%山西省养老服务条例%');

-- 太原市
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '太原市数字助老进社区方案','太原市在全市社区推广数字助老服务，社区工作者定期教老年人使用手机挂号、扫码乘车、微信视频通话等。各区设立"智慧助老志愿服务站"，免费提供智能手机使用指导。老年人可通过"我的太原"APP查询社区培训活动并报名。','太原市民政局','CITY','山西省','太原市','https://www.taiyuan.gov.cn/','2024-03-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%太原市数字助老%');

-- 吉林省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '吉林省老年人权益保障办法','吉林省对60周岁以上老年人提供公交优惠、景区减免、医疗优先等优待。70周岁以上凭老年优待证免费乘坐市内公交。在"吉事办"APP上线长者服务专区，提供养老金认证、高龄津贴申请、医保查询等事项大字版办理入口。','吉林省人民政府','PROVINCE','吉林省',NULL,'https://www.jl.gov.cn/','2023-04-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%吉林省老年人权益保障%');

-- 长春市
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '长春市智慧养老服务平台建设','长春市建设全市统一的智慧养老服务平台，整合居家养老、社区养老、机构养老资源。老年人可通过"长春政务"APP查询附近养老中心、预约助餐服务。平台接入紧急呼叫系统，为独居老人提供一键呼救。社区定期开展智能手机使用培训。','长春市民政局','CITY','吉林省','长春市','https://www.changchun.gov.cn/','2023-09-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%长春市智慧养老服务%');

-- 黑龙江省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '黑龙江省基本养老服务清单','黑龙江省制定基本养老服务清单，明确高龄津贴、养老服务补贴、居家上门服务等保障项目。60周岁以上老年人享受公交优待、医院优先就诊。在"全省事"APP上线长者专区，提供养老认证、社保查询、医保缴费等大字版服务入口。','黑龙江省人民政府','PROVINCE','黑龙江省',NULL,'https://www.hlj.gov.cn/','2023-06-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%黑龙江省基本养老服务%');

-- 哈尔滨市
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '哈尔滨市老年人数字素养提升行动','哈尔滨市开展"银龄数字课堂"专项行动，组织社区志愿者深入小区教老年人使用微信、手机支付、网上挂号等。在"e冰城"APP上线长者服务专区，提供高龄津贴申请、养老认证等事项办理。对行动不便的老年人提供上门认证服务。','哈尔滨市民政局','CITY','黑龙江省','哈尔滨市','https://www.harbin.gov.cn/','2024-01-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%哈尔滨市老年人数字素养%');

-- 贵州省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '贵州省养老服务高质量发展方案','贵州省推进养老服务数字化建设，建设省市县三级养老服务信息平台。鼓励企业开发适老化智能产品。60周岁以上老年人享受公交优待、景区减免。在"贵州政务服务"APP上线长者专区，提供养老认证、高龄津贴、医保查询等大字版办理入口，支持语音播报。','贵州省人民政府','PROVINCE','贵州省',NULL,'https://www.guizhou.gov.cn/','2023-08-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%贵州省养老服务高质量发展%');

-- 贵阳市
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '贵阳市社区数字助老服务','贵阳市在各社区设立"数字助老服务点"，配备志愿者手把手教老年人使用智能手机。重点教授手机挂号、电子社保卡、扫码付款、微信视频通话等常用功能。老年人可通过"贵人服务"APP查询社区活动并报名参加培训。对独居老人建立定期探访台账。','贵阳市民政局','CITY','贵州省','贵阳市','https://www.guiyang.gov.cn/','2024-02-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%贵阳市社区数字助老%');
