# 最小启动：不需要 AI Key、语音模型或 Hadoop 集群

需要 JDK 17、Maven 3.8+、Node.js 22、Python 3.10+ 和 MySQL 8。以下命令均从项目根目录执行，使用全新的本地数据库；不要对已有业务库直接执行初始化。

## 1. 准备数据库和私有配置

先复制 `deploy/mysql-users.sql.example` 到仓库外的临时文件，替换其中三处密码占位符，再用本机数据库管理员在 MySQL 客户端执行。此 SQL 创建 `elderly_study` 及应用、迁移、备份三个账号。配置中的数据库地址须使用 `127.0.0.1`，与模板的 TCP 账号来源一致。

当前历史迁移包含 `USE elderly_study`，本版本数据库名固定为 `elderly_study`；不要只修改 JDBC URL 为其他库名。为保持已部署数据库的 Flyway 校验，本次没有重写历史迁移。

```powershell
New-Item -ItemType Directory -Force backend/config | Out-Null
Copy-Item deploy/application-local.yml.example backend/config/application-local.yml
# 生成一次随机 JWT 密钥，把输出填入私有配置的 jwt.secret，不提交该文件。
python -c "import secrets; print(secrets.token_urlsafe(48))"
```

编辑 `backend/config/application-local.yml`：填写 `spring.datasource.password`（应用密码）、`spring.flyway.password`（迁移密码）和 `jwt.secret`。示例已经关闭外部大模型、模型预热和内容预生成。不要填写示例密码或使用生产凭据。

若需要首次管理员，在同一私有文件中显式添加：

```yaml
app:
  bootstrap-admin:
    enabled: true
    username: interview_admin
    password: '' # 填入自选随机密码，至少12字符，UTF-8不超过72字节
```

首次成功启动后改回 `enabled: false`，删除上述管理员明文密码并重启。已有管理员时引导程序不会覆盖账号。普通学习者通过注册页创建，没有默认公共演示密码。

## 2. 构建和启动

```powershell
mvn -B -f spark-analysis/pom.xml install
mvn -B -f backend/pom.xml spring-boot:run
```

另开终端：

```powershell
npm --prefix frontend ci
npm --prefix frontend run dev
```

打开 [本地页面](http://localhost:5173)。后端默认端口 8081；前端通过 Vite 代理转发 `/api` 和 `/uploads`。Flyway 会自动执行 V1 至后续迁移，初始化课程目录；不会导入真实用户或历史学习数据。

## 3. 无外部服务时可体验什么

| 能力 | 最小环境行为 |
| --- | --- |
| 注册登录、课程列表、文字学习、收藏、学习记录、显示设置 | 使用本地数据库 |
| 首页推荐 | 内容、热门、最新课程补位；新账号不保证有协同过滤候选 |
| 智能问答 | 人工 FAQ / 本地关键词规则兜底；页面来源标识不能当作大模型输出 |
| 管理后台 | 需要显式创建管理员；空库统计为空是正常状态 |
| Spark | 默认 `local[2]`，无需 YARN；首次构建需下载依赖，模拟评估需额外样本 |
| 语音朗读、动态音色试听 | 需要单独部署 TTS；关闭预热不等于点击朗读后可以合成 |
| 天气与外部教程 | 依赖网络或外部服务，不是离线验收项 |
| 课程配图与预录音频 | 公开包排除待核验媒体，缺图时文字仍保留；可用自有素材补充 |

如果只想看登录和注册界面，可仅运行前端两条命令；登录和数据功能仍需要后端与数据库。

## 常见问题

- 数据库拒绝访问：检查三个账号的来源、密码和 `127.0.0.1` 地址。
- 建表权限不足：检查私有配置的 `spring.flyway.user/password`，不要给应用账号追加 DDL 权限。
- JWT 启动失败：填写随机密钥，至少32字节；不要把生成结果写入公开示例。
- 语音服务报连接失败：最小环境不包含 TTS；确认两个预热开关均为 false。
- 已有库迁移失败：停止套用空库流程，按 [首次部署文档](first-deployment.md) 核验基线和版本。

## 验证

```powershell
npm --prefix frontend test
npm --prefix frontend run build
mvn -B -f backend/pom.xml verify
python -m unittest discover -s tools -p "test_*.py"
python tools/audit_git.py --source-tree
```

MySQL 初始化集成测试和联网天气测试需显式环境变量才执行；默认测试通过不代表已验证真实数据库初始化、外部 AI、语音或 YARN。
