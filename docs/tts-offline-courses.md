# 小服务器的课程朗读与系统语音

当前策略：21门公开课程的440个去重分句在开发电脑离线生成，服务器只提供MP3文件；动态问答未购买外部服务，使用设备系统朗读。预生成课程统一使用Microsoft Huihui Desktop普通话音色，用户语速由播放器调整。

## 内容修改后的更新

1. 在电脑浏览器登录管理员，进入“音频资源”，点击“导出课程朗读清单”。清单只包含公开课程正文、简介和教学步骤，不包含私人消息或AI问答。
2. 在项目目录执行Windows离线生成：

   ```powershell
   powershell.exe -NoProfile -File tools/generate_course_audio.ps1 -Manifest course-audio-manifest.json -Output work/course-audio
   ```

   需要Windows已安装中文慧慧语音。可通过脚本的`-Voice`指定其他已安装中文音色。运行于本机，不向外部发送文本。

   使用本机ChatTTS时，先运行已有模型服务，再执行：

   ```powershell
   python tools/generate_course_audio.py course-audio-manifest.json --output work/course-audio
   ```

3. 单句可在管理页填写清单中的完整`text`并上传对应WAV/MP3/OGG；批量更新可将生成目录`prepared`上传到服务器的`/var/lib/elderly-study/uploads/tts/prepared`（保持文件名、权限和elderly用户可读）。首次上线已批量完成。
4. 同一句文字按清洗后的UTF-8 SHA-256匹配。正文修改后，新文字不会命中旧录音；未重新生成的内容暂由系统朗读。没有部署自动连接开发电脑的常驻生成任务。

## 在线接口与缓存

`TTS_MODE=system`：优先查询预生成音频，未命中立即返回可控业务错误，前端启用设备语音。不会拉起Python、ChatTTS或访问外部TTS。

未来接兼容的HTTP服务时可配置`TTS_MODE=http`、`TTS_API_URL`、可选`TTS_API_KEY`。接口请求为JSON：`text`、`voice`、`speed`和可选语音参数；Bearer密钥仅在后端使用。二进制WAV/MP3/OGG响应持久化缓存，同进程相同请求合并；相同参数重复播放复用文件。返回JSON `audio_url`的旧兼容接口只是透传URL，不等同于本地持久缓存。不同供应商仍可能需要请求格式适配，当前没有配置或验证付费服务。

## App备用朗读

Android 1.2.0加入受HTTPS源站限制的WebMessage语音桥，仅主页面可调用Android文字转语音引擎。浏览器继续使用speechSynthesis。引擎不存在或缺少中文资源时会提示启用中文语音；未把手机实装、中文引擎可用性或实际音质作为已通过验收。

Android系统朗读暂停通过停止实现，继续时从尚未完成的当前分句重新朗读；停止或换内容后忽略旧完成回调。进入后台停止系统朗读。固定课程录音不依赖手机安装中文引擎。

## 首次上线产物

- `work/tts-release/manifest.json`：使用后端原始清洗、分句规则，基于线上公开课程导出。
- `work/tts-release/system-course-audio/prepared`：440个WAV原始音频。
- `work/tts-release/tts-update/prepared`：48kbps单声道MP3，合计约19.1MB。
- `work/tts-release/tts-update.tar.gz`：代码、网页、音频与同签名Android1.2.0发布包。
- `tools/build_tts_update.py`、`deploy/apply-tts-update.py`：带旧后端指纹校验和回滚备份的首次更新流程；不可直接重复执行覆盖已有备份。

测试：前端16项测试；后端7项TTS回归测试；Android release构建、lint及签名校验。音频已验证WAV非空、PCM非全零，再转为MP3；不将这些结构检查当作逐句音质审核。

线上验收：已登录的回环API将440/440句映射到正确MP3；与线上公开课程导出的指纹集合完全一致，覆盖21门课程。修改文本不命中旧音频；动态未预生成文本约15毫秒返回系统朗读回退信号。网站、MySQL、Nginx均active。公网逐一下载440个音频并比对SHA-256全部通过，合计19,105,295字节；App下载字节数与SHA-256一致。报告 `work/tts-release/public-verification.json`。MP3样本被FFprobe识别为22050Hz单声道、时长约9.87秒；尚未完成真机播放和人工逐句听审。

发布包SHA-256：`5c0d3f317afc673bd65feb028bc3a66b35fe9cd25c5819314c7bb4db981a21d1`。Android1.2.0包919,821字节，SHA-256：`91923799ee6f88d3abeec7dd3c7ca0396d2202af29de1ffaf09c34a9448a0caf`；签名证书SHA-256：`a2ec8bddc267e82e413deaf79a72205651de3f6cae6f8b8d5a18e486879a5609`，与原发布密钥一致。
