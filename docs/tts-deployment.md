# 独立语音服务与分句播放

## 仅录音与设备朗读模式

`TTS_MODE=system` 不会调用 ChatTTS 或在线 TTS。默认音色可播放已关联的录音；缺少录音或显式请求其他音色时，接口会说明服务器未启用合成服务的原因。共享默认录音不会作为男声、女声或方言返回。前端保留原因提示，并尝试设备默认朗读。

此模式下 `/api/tts/voices` 与 `/api/tts/prewarm/progress` 返回 `generationAvailable=false` 和 `unavailableReason`；一键预热直接拒绝并说明配置原因，启动时也不执行合成任务。启用合成服务后再运行预热。预热界面的“已处理”包含失败项，100% 表示处理结束，不代表全部生成成功。

## 已实现的行为

- Python 服务启动时加载模型并试合成，成功后才监听 `/ping`、`/generate`；默认不因空闲退出。由 systemd 管理后不依赖网站进程拉起。
- 网站启动后异步试合成，并按系统默认音色预生成固定内容。`TTS_STARTUP_WARMUP=false`、`TTS_PREWARM_ON_STARTUP=false` 可分别关闭。后台任务不阻塞网站启动，启动初期应等预热完成后再验收语音。
- HTTP 模式将音频持久化到 `upload.dir/tts`。缓存包含清洗后的文本、音色、实际发送的语速/音量/音调、服务地址与 `cache-version`；同 JVM 内相同请求合并，临时文件写完后原子替换。识别 WAV、MP3、OGG 文件签名。空响应、失败与不支持的音频不会进入缓存。
- `/api/tts/segments` 仅规划分句；`TtsPlayer` 和点读使用该规则逐段请求 `/generate`，首段就绪即可播放，播放时预取下一段。首段最多60个 Unicode 字符，后续最多120个，优先句末分割。原生播放条显示当前段的进度。
- 停止、切换文本/音色、卸载会取消浏览器侧请求并使旧响应失效。已在服务器执行的推理可能继续完成并写入缓存，客户端取消不等于中止 GPU 推理。
- 默认预生成全部启用课程正文、简介及其他固定入口的文本分段；三档语速分别准备。3、4、15号课程的教学步骤使用 `shared/course-tutorials.json`，前端与后端读取同一份内容。修改后需要重新构建前后端。
- 后台“音频资源→开始预热”可重跑；课程编辑发布或默认音色/参数变更后应重跑。启动自动预热仅处理系统默认音色，其他音色可在管理端选择生成。已有内容命中缓存，不再推理。

## 部署

1. 在 Linux 重建 ChatTTS-Enhanced 环境，保留与本机匹配的版本、`processors`、`modules`、模型和 `data` 音色文件；Windows Python 环境不能直接复制使用。仓库当前没有这些外部模块和模型，需要从原运行环境准备。
2. 将 Enhanced 应用放到 `/opt/chattts`，创建 `.venv` 并安装匹配的 PyTorch、依赖和 FFmpeg。`TTS_APP_ROOT` 指向 Enhanced 根目录，脚本以该目录查找相对资源；`HF_HOME` 指向可写模型缓存目录。
3. 按实际目录修改 `deploy/chattts.service`，创建专用 `tts` 用户并授予模型目录访问权限，再安装服务到 systemd。`TTS_DEVICE=cpu` 或 `cuda` 可指定设备；模型加载仍保留原先的 CPU 回退。
4. 初次运行时观察日志，等“首次推理预热完成”后验证 `GET /ping`。用 `POST /generate` 发送 `{"text":"欢迎学习。","voice":"female"}`，确认返回真实 `audio/wav`。
5. 同机后端使用 `http://127.0.0.1:5050/generate`。跨机器时通过受限私网代理访问；跨容器直接连接需设置 `TTS_BIND_HOST=0.0.0.0` 并限制网络入口。本脚本没有鉴权，且有 `/shutdown`，不要直接开放公网；代理只放行需要的接口。
6. 按 `deploy/application-tts-http.yml` 覆盖后端配置，并持久化 uploads 目录。网站服务器的普通话合成不再需要 Python；`edge:` 音色仍走原有 edge-tts 分支，需要网站侧 Python 和联网。
7. 重启后在管理端查询预热进度（`GET /api/tts/prewarm/progress`，管理员权限），等待任务结束。默认音色的课程可以直接命中缓存；失败项会记录原因，可修复后重跑。

## 参数与限制

- 更换模型或 Python 内部推理参数后递增 `TTS_CACHE_VERSION`，否则历史音频会继续复用；旧缓存可通过后台按需清理。缓存没有自动淘汰，应监控磁盘容量。
- 本 Python ChatTTS 服务不应用请求中的 speed/rate/volume/pitch，保持原有行为；非 edge 音色由播放器应用倍速。接入其他会服务端变速的 TTS 时需同步调整播放器，避免重复变速。
- HTTP 的旧 `audio_url` JSON 响应保持兼容，但不会将远程链接永久缓存；本部署使用 WAV 字节流，以保证可持久缓存。
- 预取能减少等待，但不保证完全无停顿。模型生成比播放慢或用户多时，段间仍可能等待；需要在目标服务器测量排队、首段耗时及显存占用。
- 目前运行验证使用本地模拟 HTTP 音频服务，不代表目标服务器 GPU 性能或真实音质验收。

## 验证

```powershell
mvn -f backend/pom.xml '-Dtest=TtsSegmentsTest,ChatTtsHttpTest,TtsPrewarmServiceTest' test
npm --prefix frontend run test:tts
npm --prefix frontend run build
python -m py_compile tools/tts_server.py
```

浏览器验收：长回答第一段就绪后开始播放；下一段自动接续；停止后不突然恢复；换音色不会播放旧结果；暂停后从当前段继续；完成后可重播。浏览器禁止自动播放时可再次点击播放。
