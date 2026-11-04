# Android 工程快速打包指南

## 环境要求
- Android Studio 2023.1.1+
- JDK 17（AS 自带）
- Android SDK 35（首次 Build 时按提示安装）

## 打包步骤
1. Android Studio → **Open** → 选择 `android-app` 目录
2. 等待 Gradle 同步（首次会下载 Gradle 8.7 与 AGP 8.5.2）
3. 若提示缺 SDK 34 → 点击安装
4. 通过 Gradle 参数明确指定自己的 HTTPS 服务：`gradle -PappBaseUrl=https://your-server.example/ assembleDebug`。Android Studio 可在本机用户 Gradle 属性中配置 `appBaseUrl`，不要将真实部署配置提交。
5. APK 产物：`app/build/outputs/apk/debug/app-debug.apk`
6. 传到手机安装即可（需开启"允许安装未知来源应用"）

## 注意事项
- 1.3.1（versionCode 5）在 Android 11 及以上由原生容器统一处理状态栏、摄像头挖孔、底部导航栏和键盘边距；Android 7–10 保留系统默认窗口适配。横竖屏切换或键盘显隐时重新计算，已处理的边距以零值继续传给 WebView，避免网页重复留白。参考 [Android WebView 边距处理文档](https://developer.android.com/develop/ui/views/layout/webapps/understand-window-insets)。此修复需要更新 APK，单独刷新网页不会更新原生容器。
- 真机验收：在挖孔/刘海手机上检查登录、首页、课程标题栏与弹窗；切换横屏及手势/三键导航；在登录和问答页面打开、关闭键盘，确认标题、输入框、底部按钮无遮挡且没有残留留白。
- 当前发布版通过 HTTPS 连接云端页面，不要求手机与开发电脑在同一 Wi-Fi，使用时需联网。
- 必须通过 `-PappBaseUrl=https://服务器地址/` 指定自己的站点，不能附加 `/api`、账号密码或查询参数。缺少参数会停止构建；CI 的 `example.invalid` 仅用于编译检查。
- 正式发布使用 `assembleRelease`，并设置 `ELDERLY_SIGNING_FILE` 为本机签名 properties 文件路径。配置包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`，不提交、不上传。没有签名配置时生成的未签名产物不能作为下载发布包。
- 发布记录及验收边界见 [云端部署记录](../docs/cloud-deployment-2026-11-03.md)。
- WebView 内语音朗读（服务端合成 mp3）正常；语音输入（麦克风调用 Web Speech API）在 WebView 中不可用，请用键盘输入
