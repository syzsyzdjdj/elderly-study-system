-- V11: 补充各省市本地政策（含福建省福州市等）

-- 福建省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '福建省老年人优待规定','福建省对60周岁以上老年人提供公共交通优待、景区门票减免、医疗优先等服务。70周岁以上凭"福建省老年人优待证"免费乘坐市内公交。可通过"闽政通"APP办理老年优待证、高龄津贴等事项。','福建省人民政府','PROVINCE','福建省',NULL,'https://www.fujian.gov.cn/','2023-01-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%福建省老年人优待%');

-- 福州市
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '福州市智慧助老进社区行动','福州市在全市社区推广"智慧助老"服务，社区工作者手把手教老年人使用手机挂号、扫码乘车、电子社保卡等。各街道设立"数字助老志愿者"，定期开展智能手机使用培训。老年人可通过"e福州"APP查询社区活动并报名。','福州市民政局','CITY','福建省','福州市','https://www.fuzhou.gov.cn/','2024-03-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%福州市智慧助老%');

-- 山东省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '山东省养老服务条例','山东省规定县级以上人民政府应当推进养老服务数字化建设，为老年人提供智慧居家养老、紧急呼叫等服务。60周岁以上老年人可享受公立医院优先就诊、公交优惠等待遇，相关事项可在"爱山东"APP办理。','山东省人大常委会','PROVINCE','山东省',NULL,'https://www.shandong.gov.cn/','2022-07-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%山东省养老服务条例%');

-- 河南省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '河南省适老化改造行动方案','河南省推动各级政务服务大厅、医院、银行等场所进行适老化改造，设置老年人绿色通道，保留现金收费窗口。推广"豫事办"APP长者模式，大字版界面方便老年人在线办理社保查询、医保缴费等高频事项。','河南省人民政府','PROVINCE','河南省',NULL,'https://www.henan.gov.cn/','2023-06-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%河南省适老化改造%');

-- 湖北省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '湖北省养老服务体系建设规划','湖北省推进"互联网+养老"服务，建设全省统一的养老服务信息平台。老年人家属可通过平台预约居家上门服务、查询养老机构信息。各社区配备"数字助老员"，帮助老年人学习智能手机使用。','湖北省人民政府','PROVINCE','湖北省',NULL,'https://www.hubei.gov.cn/','2023-04-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%湖北省养老服务体系建设%');

-- 湖南省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '湖南省老年人数字素养提升行动','湖南省开展"银龄跨越数字鸿沟"专项行动，组织社区志愿者深入小区教老年人使用微信、手机支付、网上挂号等。在"湘易办"APP上线长者服务专区，提供养老认证、高龄津贴申请等事项大字版办理入口。','湖南省人民政府','PROVINCE','湖南省',NULL,'https://www.hunan.gov.cn/','2023-09-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%湖南省老年人数字素养%');

-- 安徽省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '安徽省养老服务促进条例','安徽省鼓励运用智能技术为老年人提供养老服务，支持企业开发适老化智能产品。80周岁以上老年人享受高龄津贴，可通过"皖事通办"APP在线申请。社区建立老年人信息台账，定期上门关怀独居老人。','安徽省人大常委会','PROVINCE','安徽省',NULL,'https://www.ah.gov.cn/','2022-12-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%安徽省养老服务促进%');

-- 河北省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '河北省养老服务高质量发展措施','河北省推动养老服务数字化升级，建设省市县三级养老服务信息平台。老年人在"冀时办"APP可办理社保查询、养老金认证、医保缴费等事项，APP提供大字版和语音播报功能。','河北省人民政府','PROVINCE','河北省',NULL,'https://www.hebei.gov.cn/','2023-02-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%河北省养老服务高质量%');

-- 辽宁省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '辽宁省关爱空巢老人行动','辽宁省建立空巢老人关爱服务平台，社区工作人员定期走访独居老人。在"辽事通"APP上线老年人专区，提供高龄津贴申请、养老待遇认证、社区助餐点查询等服务。鼓励志愿者结对帮扶困难老人学习智能手机。','辽宁省人民政府','PROVINCE','辽宁省',NULL,'https://www.ln.gov.cn/','2023-07-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%辽宁省关爱空巢老人%');

-- 陕西省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '陕西省智慧养老服务平台建设','陕西省建设全省统一的智慧养老服务平台，整合居家养老、社区养老、机构养老资源。老年人可通过"秦务员"APP查询附近养老服务中心、预约上门服务。平台接入紧急呼叫系统，为独居老人提供一键呼救功能。','陕西省人民政府','PROVINCE','陕西省',NULL,'https://www.shaanxi.gov.cn/','2023-05-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%陕西省智慧养老服务%');

-- 重庆市
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '重庆市社区养老服务全覆盖方案','重庆市推进社区养老服务设施全覆盖，每个街道至少建1个养老服务中心。在"渝快办"APP上线长者服务专区，老年人可办理高龄津贴、养老金认证、医保查询等事项，APP提供大字版和语音引导功能。','重庆市民政局','CITY','重庆市','重庆市','https://www.cq.gov.cn/','2023-08-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%重庆市社区养老服务全覆盖%');

-- 天津市
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '天津市数字适老化改造行动','天津市推动政务服务、医院、银行、公交等高频服务场景进行适老化改造。在"津心办"APP上线长者模式，大字版界面提供社保查询、医保缴费、养老认证等服务入口。各区设立"数字助老服务站"，免费教老年人使用智能手机。','天津市民政局','CITY','天津市','天津市','https://www.tj.gov.cn/','2024-01-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%天津市数字适老化改造%');

-- 云南省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '云南省养老服务体系建设方案','云南省加强农村养老服务设施建设，推动城市养老服务资源向农村延伸。在"一部手机办事通"APP上线长者服务专区，提供养老金认证、高龄津贴申请等事项办理。对偏远地区老年人开展上门认证服务。','云南省人民政府','PROVINCE','云南省',NULL,'https://www.yn.gov.cn/','2023-03-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%云南省养老服务体系建设%');

-- 江西省
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '江西省老年人数字助老行动','江西省在全省推广"数字助老"志愿服务，社区定期举办智能手机使用培训班。在"赣服通"APP上线长者专区，提供社保认证、医保查询、高龄津贴申请等大字版服务。重点帮助农村老年人学会手机挂号、视频通话等常用功能。','江西省人民政府','PROVINCE','江西省',NULL,'https://www.jiangxi.gov.cn/','2023-10-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%江西省老年人数字助老%');

-- 广西壮族自治区
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '广西壮族自治区老年人优待规定','广西对60周岁以上老年人提供公交免费、景区门票减免、医院优先就诊等优待。在"壮掌桂"APP上线长者服务专区，提供养老认证、社保查询、医保缴费等事项办理。各地社区配备数字助老志愿者帮助老年人学用智能手机。','广西壮族自治区人民政府','PROVINCE','广西壮族自治区',NULL,'https://www.gxzf.gov.cn/','2023-06-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%广西壮族自治区老年人优待%');

-- 福建省 福州市 补充
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '福州市高龄老年人津贴发放办法','福州市对80周岁以上户籍老年人发放高龄津贴，按年龄段分档发放。可通过"e福州"APP在线申请，也可到社区服务中心办理。每季度发放一次，直接打入老年人社保卡。','福州市民政局','CITY','福建省','福州市','https://mzj.fuzhou.gov.cn/','2023-01-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%福州市高龄老年人津贴%');

-- 厦门市
INSERT INTO `gov_policy` (title, content, source, policy_level, province, city, gov_url, publish_time, status)
SELECT '厦门市"i厦门"长者服务专区','厦门市在"i厦门"APP上线长者服务专区，提供养老认证、高龄津贴、公交老年卡申请等事项大字版办理入口。各区行政服务中心设立老年人绿色通道，配备专人引导。社区定期开展智能手机使用培训。','厦门市民政局','CITY','福建省','厦门市','https://www.xm.gov.cn/','2024-02-01',1
WHERE NOT EXISTS (SELECT 1 FROM gov_policy WHERE title LIKE '%厦门市%长者服务专区%');
