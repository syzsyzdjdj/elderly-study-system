# 服务器部署与配置

已准备生产配置和部署模板，并在独立临时 MySQL 中验证初始化和权限；没有连接或修改目标服务器、API 控制台或域名。首次部署按 [first-deployment.md](first-deployment.md) 执行，实际环境仍需账号创建、凭据轮换、证书配置与恢复演练。

## 开发与生产隔离

- 默认 profile 为 `dev`。当前开发机原有数据库、JWT、API、YARN 与语音路径配置已迁入 `backend/config/application-local.yml`，由 `.gitignore` 忽略且位于 Maven 资源目录之外，不进入新构建的 jar。从仓库根目录或 backend 目录启动可读取此文件。
- 新开发机复制 `deploy/application-local.yml.example` 到上述位置并填值；也可完全使用环境变量。Spring 标准 `SPRING_DATASOURCE_PASSWORD` 等环境变量优先于私有配置。本机文件仍可能保留原 root 账号，仅用于保留开发环境，不复制到服务器。
- 生产必须显式设置 `SPRING_PROFILES_ACTIVE=prod`，模板 systemd 服务已设置。不能同时启用 dev。生产不导入开发机私有文件。
- 公共配置已删除数据库密码、固定 JWT 密钥和 DeepSeek Key 默认值；Spark 默认 `local[2]`，不依赖本机虚拟机；语音默认 HTTP，不启动 Windows Python。
- production 在连接数据库前检查密码、至少32字节 JWT 密钥、非 root 业务账号和独立非 root 迁移账号、绝对数据路径、SQL 日志及 CORS 来源。启用 DeepSeek 时必须配置 Key。错误不打印凭据值。
- 已出现在仓库中的旧凭据，需要在原服务端实际轮换。移除源码和忽略文件不撤销旧凭据，也不清理 Git 历史及以前分发的 jar、备份。

## 服务器布局

```text
/opt/elderly-study/backend.jar
/opt/elderly-study/deploy/{jvm.options,backup.sh}
/var/www/elderly-study/                 前端 dist 内容
/etc/elderly-study/backend.env          服务器私有环境变量
/etc/elderly-study/backup.cnf           备份专用 MySQL 凭据
/var/lib/elderly-study/uploads/         持久上传、视频、TTS 缓存
/var/lib/elderly-study/spark-tmp/        分析快照及结果
/var/log/elderly-study/                 后端滚动日志
/var/backups/elderly-study/             数据库和上传文件备份
```

本模板针对单机 Linux、systemd、JDK17 和 MySQL8。创建无 sudo 权限的 `elderly` 系统用户；systemd 的 `StateDirectory`、`LogsDirectory` 创建相应目录并设置属主。上传目录必须可写；发布 jar 时保留这些数据目录。自定义 `UPLOAD_DIR`、`LOG_FILE` 等目录时，要自行配置权限。

取得服务器后可先运行 `bash deploy/preflight.sh` 做只读环境检查；它不会购买服务器、安装软件或修改系统。

## 数据库与凭据

1. 由 DBA 参考 `deploy/mysql-users.sql.example` 创建业务账号（仅 CRUD）、迁移账号（限定 elderly_study 库内 DDL/例程权限）和备份账号。先替换三处随机密码占位符，模板不会自动执行。远程数据库按真实来源限制账号 host，不使用 `%`。
2. 填写 `deploy/backend.env.example`，复制到 `/etc/elderly-study/backend.env`，设为 `root:root 0600`。不要把新值提交。JWT 可使用 `openssl rand -hex 32` 生成并直接写入私有文件。示例文件由 systemd 读取，不会因放在仓库里自动生效。
3. 默认由 Flyway 使用独立迁移账号更新 schema；业务连接池仍使用 `elderly_app`。若迁移由独立发布流程执行，设置 `DB_MIGRATIONS_ENABLED=false`，可不提供迁移凭据。
4. 已补齐 `V1__base_schema.sql`：17张基础表、角色/菜单/分类及17门基础课程目录，后续迁移补全课程内容与其余表。新空库由 Flyway 从 V1 顺序执行到 V24，不需要导入开发库。V1 不创建用户，也不导入学习日志、问答或分析结果。已有 baseline(version=1) 的库会跳过 V1；其他已有库必须核验结构后再显式 baseline，生产不自动接受未知结构。不要直接复制旧库的模拟用户或测试记录。
5. 已在隔离 MySQL8 中演练空库全部迁移、业务账号 CRUD/禁止 DDL、首次管理员创建和旧版 baseline 升级。`DatabaseInitializationMySqlTest` 只接受显式指定的临时数据目录及非3306端口，拒绝在已有业务表上执行；它不会修改开发库。

### 首次管理员

V1 不预置管理员密码。第一次生产启动前，在私有 `backend.env` 中设置：

```text
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=自行选择的管理员用户名
# 填入自行生成的至少12字符随机密码
BOOTSTRAP_ADMIN_PASSWORD=
```

用户名为3-50位字母、数字或下划线；密码UTF-8编码不超过72字节。初始化只在不存在管理员时执行，保存 BCrypt 哈希；已有管理员时不会修改任何密码，也不会把同名普通用户提升为管理员。首次登录验证后改为 `BOOTSTRAP_ADMIN_ENABLED=false`，删除该用户名/密码配置值并重启。

现有迁移中包含 `USE elderly_study`，本模板使用该数据库名。换库名需先检查迁移脚本，不能只改 JDBC URL。

## 构建与启动

在仓库根目录构建：

```powershell
mvn -f spark-analysis/pom.xml clean install
mvn -f backend/pom.xml clean package
npm --prefix frontend ci
npm --prefix frontend run build
```

构建后端前先使 Spark 模块版本一致并安装到 Maven 仓库。将可执行 backend jar 放到 `/opt/elderly-study/backend.jar`，复制部署目录中的 JVM 参数及服务文件；前端只发布 `frontend/dist` 的内容，不发布整个仓库、私有配置或开发服务器。

安装 `deploy/elderly-study.service` 到 `/etc/systemd/system/`，检查 `/usr/bin/java` 指向 JDK17，准备好环境文件和目录后执行：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now elderly-study
sudo journalctl -u elderly-study -n 100 --no-pager
```

`jvm.options` 包含 Spark 在 JDK17 上需要的模块开放参数，不能在直接 java -jar 启动时遗漏。示例堆上限为3GB，应结合机器内存与 MySQL、语音服务占用调整。后台初次语音预热仍需要时间，等待管理端进度完成后验收。

## Nginx 与前端

- 修改 `deploy/nginx.conf.example` 的域名、证书路径和前端目录，在服务器执行 `nginx -t` 后再 reload。此模板不是已经配置好的证书服务。
- 生产前端 Web 的 `VITE_API_BASE` 留空；Nginx 同源代理 `/api/`、`/uploads/`，保留后端路径和视频 Range 行为。上传限制105MB，业务单文件限制仍为100MB。
- 后端默认只监听 `127.0.0.1:8081`，公网开放80/443；MySQL和语音服务保持私有。修改后端端口时同步修改 Nginx upstream 地址。
- 前端删除了原生应用的固定局域网地址。打包 Capacitor 应用前，在未提交的 `frontend/.env.local` 中设置 `VITE_API_BASE=https://真实域名`，不附加 `/api`。Web 构建时清除该覆盖。同源 Web 无需 CORS；原生应用若需要跨域，按实际 WebView Origin 显式设置 `CORS_ALLOWED_ORIGINS`。
- 后端日志滚动限制单文件20MB、保留14天、总上限500MB。Nginx访问日志使用服务器本身的日志轮转配置。

## Spark 与语音

生产默认本地 Spark 模式，不需要 Hadoop/YARN。限制为 `local[2]`，仍会消耗网站 JVM 资源，应限制分析任务并发并在低峰运行。

需要独立 YARN 时显式设置 `SPARK_MODE=yarn`、`SPARK_YARN_HOST`、`SPARK_YARN_USER`、`SPARK_SSH_KEY`、`SPARK_HDFS_BASE`、`SPARK_JAR_PATH` 等，SSH 使用专用非 root 账号。默认 JDBC 取数关闭，采用已有快照中转流程，避免远程集群直连业务库。仅在独立环境联通并验证后切换。

语音部署参考 [tts-deployment.md](tts-deployment.md)。`TTS_APP_ROOT`、`HF_HOME` 和 Python 路径均可配置，公共脚本不再默认引用开发机盘符。模型及 Enhanced 外部依赖仍需单独准备，配置改造不会自动安装模型。

## 备份、恢复与发布

1. 将备份账号的凭据写入 `/etc/elderly-study/backup.cnf`，设为 `root:root 0600`。脚本从文件读取密码，不写入命令行参数。
2. 安装 `elderly-study-backup.service`、`.timer`。先手动 `systemctl start elderly-study-backup` 并检查退出码和生成文件，再启用 timer。计划为北京时间每天03:00，服务停机错过后会补跑。
3. 备份包括 MySQL schema/数据（含 Flyway 历史）和完整 uploads。脚本使用事务快照、gzip/tar 完整性检查；全部成功才移除 `.partial` 后缀。不要将 partial 文件当作完整备份。临时或失败文件、历史备份不会自动删除，需要按容量与保留策略清理，并将成功备份复制到其他机器或对象存储。
4. 备份默认不额外导出存储过程/事件；当前迁移中的辅助过程用后即删。若部署中自行新增永久例程或事件，需要扩展备份参数和账号权限。
5. 数据库事务快照和上传目录归档不是跨资源原子快照。需要严格一致的恢复点时，维护窗口暂停写入后备份；恢复时选择同一批时间戳的数据库和 uploads，并在隔离目录/临时库演练，不能直接覆盖正式数据。
6. 发布前保留当前 jar、dist、配置版本，并先做备份。数据库迁移可能与旧代码不兼容，因此回退不能仅依赖替换旧 jar，需事先演练数据库恢复或前向修复。

## 本次验证边界

配置测试覆盖生产隔离、缺少凭据、root/共用迁移账号、目录和 CORS 错配；工具测试覆盖环境变量解析；前端测试覆盖服务器地址校验。备份使用模拟 mysqldump 检查成功归档和失败退出，不代表已通过真实 MySQL 恢复验收。Nginx、systemd、账号授权与模型部署需在目标服务器完成实际验收。
