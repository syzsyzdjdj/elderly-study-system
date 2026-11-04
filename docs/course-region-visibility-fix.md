# 新增课程在全部课程中不可见

2026-11-03线上复现：新增课程ID22已保存且status=1，province和city为`""`。无地区参数的公开课程列表共22条、第一条为22；传入广东省/广州市后只返回21条，第一条为21。

原因：管理员表单将留空地区作为空字符串提交，而查询仅以`IS NULL`判断全国或省级通用范围。学员资料有地区时，新课程会被误过滤；不是新增失败或未上架。

修复：

- 地区筛选同时接受NULL、空字符串及历史纯空白值。省和市条件各自分组，保留通用课程及匹配地区课程。
- 新增/编辑课程时去掉地区值首尾空格。未提交的null保留原有部分更新语义；显式空字符串允许清除地区限制。
- 全部课程页增加刷新入口；分页参数由pageSize改为后端实际使用的size；刷新/切换筛选后忽略旧请求返回。
- 没有修改、删除或重新添加生产课程，也没有通过迁移批量改写地区数据。

测试使用实际MyBatis-Plus Mapper和独立H2数据库，覆盖NULL/空串/纯空白的全国课程、NULL/空串的省级课程、本市、同省其他城市、其他省及分页total；2项回归测试通过。前端和后端构建通过。

增量包SHA-256：`52bdfdbf9e7b38775122a28bef58baeb70cd4b54a0aa30aeaa66f12a2e05c43a`。替换前校验旧CourseServiceImpl类指纹并备份 `/opt/elderly-study/backend.before-course-visibility.jar`，重启一次后端；首页备份 `/home/ubuntu/course-visibility-update/index.before.html`。

上线后公网验证：无地区参数、广东省/广州市、浙江省/杭州市三种查询均返回total=22，首条ID=22；列表使用size=10。公网首页和CourseList脚本哈希与构建包一致。验证报告 `work/course-visibility/public-verification.json`。未通过造新测试课程或改写现有课程地区来达成可见性。
