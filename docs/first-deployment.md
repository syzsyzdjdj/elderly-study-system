# 首次部署顺序

当前阶段没有目标服务器。本发布包已准备后端、前端、版本迁移、29个预置课程图片、环境模板和服务模板；不包含数据库用户记录、实际密钥、开发机配置或语音模型。

## 1. 确认环境

目标为 Linux + systemd + JDK17 + MySQL8 + Nginx；ChatTTS 可单独部署。取得服务器后先执行：

```bash
bash deploy/preflight.sh
```

脚本只读检查软件、系统、磁盘与内存，不自动安装软件。还需实际确认 SSH、域名、TLS证书、业务数据库、语音内网地址和异机备份位置。当前 JVM 模板使用最大3GB堆，机器内存还须容纳 MySQL、系统及可能的本地 Spark；语音模型资源另行评估。

## 2. 创建目录和账号

解压发布包后，由有管理权限的操作人员执行以下操作。若已有同名系统用户，请核对后复用，不重复创建。

```bash
sudo useradd --system --user-group --home-dir /opt/elderly-study --shell /usr/sbin/nologin elderly
sudo install -d -m 0755 /opt/elderly-study /var/www/elderly-study
sudo install -d -m 0700 /etc/elderly-study
sudo install -d -o elderly -g elderly -m 0750 /var/lib/elderly-study/uploads /var/lib/elderly-study/spark-tmp /var/log/elderly-study
```

MySQL管理账号登录后，参考 `deploy/mysql-users.sql.example` 替换密码并创建专用业务、迁移和备份账号。保持正式数据库名 `elderly_study`。此处创建的应为空数据库，不导入开发机用户数据。

## 3. 安装程序和配置

在发布包解压目录中执行：

```bash
sudo install -m 0644 backend.jar /opt/elderly-study/backend.jar
sudo cp -R deploy /opt/elderly-study/
sudo cp -R frontend/. /var/www/elderly-study/
sudo cp -R uploads/. /var/lib/elderly-study/uploads/
sudo chown -R elderly:elderly /var/lib/elderly-study/uploads
sudo install -m 0600 deploy/backend.env.example /etc/elderly-study/backend.env
```

编辑 `/etc/elderly-study/backend.env`，填入新数据库凭据和 JWT 随机密钥。首次创建管理员时临时设置 `BOOTSTRAP_ADMIN_ENABLED=true`，并填写管理员用户名和至少12字符的密码。不要直接使用文档中的占位文字；密码不提交版本控制。DeepSeek 未准备好时保持禁用。

语音模型未部署时可暂设 `TTS_STARTUP_WARMUP=false` 和 `TTS_PREWARM_ON_STARTUP=false` 以验证网站核心流程；这不代表服务端语音已上线。语音部署完成后恢复预热并测试。

## 4. 首次启动

```bash
sudo install -m 0644 deploy/elderly-study.service /etc/systemd/system/elderly-study.service
sudo systemctl daemon-reload
sudo systemctl enable --now elderly-study
sudo journalctl -u elderly-study -n 100 --no-pager
```

空库会从 V1 执行到当前源码最新迁移，初始用户数为0；启用首次管理员配置后再创建管理员，存储 BCrypt 密码哈希。任何迁移错误先停下来查看日志，不要删除 Flyway 历史或随意执行 repair。已有数据库须先按 `docs/server-deployment.md` 核验版本，不直接套用空库流程。

管理员登录验证成功后，关闭 `BOOTSTRAP_ADMIN_ENABLED`、移除管理员用户名与密码配置值并重启。重复启动不会重置已有管理员密码。

## 5. 访问、备份和验收

修改 `deploy/nginx.conf.example` 中的域名、证书位置及必要目录，按服务器的 Nginx 目录布局安装配置。先执行 `nginx -t`，通过后 reload。公网只暴露所需入口，后端默认监听回环地址8081。

完成网页登录、注册、课程学习、学习记录保存、语音播放、权限和备份恢复演练后再开放使用。备份配置及定时器见 `docs/server-deployment.md`。只在隔离 MySQL 中完成过的迁移测试，不能替代正式服务器验收。

## 重新构建发布包

若使用 `deploy/install-small-server.sh` 安装到自己的服务器，必须显式提供 `APP_PUBLIC_IP` 和 `INITIAL_ADMIN_USERNAME`；文档示例 IP 不能直接用于安装。该脚本仅用于空白服务器，不用于覆盖既有部署。

```powershell
mvn -f spark-analysis/pom.xml install
mvn -f backend/pom.xml package
npm --prefix frontend run build
python tools/package_server.py --backend-jar backend/target/elderly-study-backend-1.0.0.jar
```

若开发服务锁定 `backend/target`，可使用 `-Delderly.build.directory=绝对路径` 指定独立构建目录，并将其中的新 jar 传给打包工具。工具拒绝缺少V1或首次管理员组件的旧 jar。发布包及 SHA256 校验文件默认输出到 `work/releases/`。
# 媒体公开范围更新（2026-11-03）

现有课程媒体没有自制记录或转载授权，默认部署包不包含这些文件；后文关于预置资源的描述仅说明可选目录结构。只有取得全部素材许可证据后才使用 `tools/package_server.py --include-verified-media`。公开源码请使用 `tools/prepare_public_release.py`，见 [素材核验](media-rights.md)。

