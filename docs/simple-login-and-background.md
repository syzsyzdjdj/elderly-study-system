# 简化注册、保持登录与后台暂停

> 开源副本已脱敏：服务器地址、实例标识和账号均为示例，不能用于访问原部署。

2026-11-03 已部署至 https://203.0.113.10/ ，Android 1.3.0（versionCode 4）可在 https://203.0.113.10/download.html 下载。

## 用户行为

注册只需账号、密码，密码可点眼睛检查，长度6至72字符；注册成功自动登录进入学习。昵称、手机号和地区可稍后在“我的”填写。原账号继续有效，无需重新注册。

普通用户登录和注册页默认勾选“这是我的手机，保持登录30天”，公用设备应取消。既有登录不会自动变成长期登录，需要下次登录时勾选。期限从登录起固定30天，访问不会无限延长；管理员及其他管理角色仍使用原24小时访问凭证。

长期登录使用 Secure、HttpOnly、SameSite=Strict Cookie，路径 /api。数据库仅存随机设备凭证的SHA-256、密码指纹和到期时间，不存明文密码或原始设备凭证。访问JWT过期时自动续签；退出登录撤销当前设备凭证，密码改变、停用或角色改变会阻止后续续签。已签发的访问JWT仍遵循原有到期策略，不承诺立即全端失效。刷新请求需 X-Session-Request: 1，自定义头与SameSite共同约束跨站请求。

本地HTTP调试如需测试Cookie，必须显式配置 app.session.secure-cookie=false；生产保持默认true。前端不会在网络故障时直接抹除账号，也不会应用退出登录前尚未返回的续签响应。

## 资源行为

保留上次页面会保留部分手机WebView内存和缓存，不会为每个后台App新增一个服务器进程。系统内存紧张时可回收进程，恢复页面不是永久保证。

网页在后台停止提醒/天气轮询、视频和语音；学习计时排除后台时间。B站iframe退后台卸载，回前台重载且不自动播放。Android 1.3.0补充原生生命周期暂停WebView计时器和系统朗读，回前台恢复计时器；Cookie在onStop落盘。页面保留以便继续阅读，不主动清空登录或缓存。后台提醒因此暂停，不等同于原生推送服务。

## 验证

- 前端21项测试通过，覆盖续签合并、退出竞态、过期/离线差异、前台计时；生产构建通过。
- 后端持续登录服务3项、控制器2项测试通过；另2项课程地区测试通过。H2执行实际V26迁移；验证凭证哈希、到期、撤销、密码/状态/角色变化及Cookie保护属性。
- 本地真实Register组件验收：请求注册只有username/password，之后登录带rememberMe并进入学习，未创建线上测试账号。
- Android release、lintRelease和签名验证通过，沿用发布证书；APK 920653字节，SHA-256 `26d4e9993d1b0746ae5a08f6275347b0a70ca2cf011dbc45911ea97851a97fa4`。
- 线上elderly-study/MySQL/Nginx均active，Flyway V26成功，原管理员登录成功且未创建管理长期凭证；课程total=22。公网首页与构建文件一致，APK内容/大小/MIME/哈希一致，刷新缺头403、无有效Cookie401。注册界面截图见 screenshots/simple-registration-live.jpg。
- 未连接真机或模拟器，尚未实测手机耗电、系统回收和后台生命周期。

## 部署和回滚

发布包 work/simple-login/simple-login-update.tar.gz，SHA-256 `48a3326d04948306d4618997d38869504e7de5613216e6a10572b7805d0f154c`。补丁核对旧类哈希，保留Spring Boot嵌套依赖压缩属性。仅新增login_session表，不修改既有课程、账号和学习记录。

服务器后端备份 /opt/elderly-study/backend.before-simple-login.jar；数据库备份 /var/backups/elderly-study/before-simple-login.sql（root 0600）；旧首页和下载元数据位于 /home/ubuntu/simple-login-update/index.before.html、latest.before.json。回滚应用和网页时保留V26表与迁移记录，不删除生产数据；1.2.0 APK仍保留。如需恢复数据库，先评估备份后的真实新增数据，不直接覆盖。
