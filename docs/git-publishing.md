# Git 发布前检查

源码、测试、数据库迁移和部署模板可以提交。开发配置、真实密钥、日志、构建产物、用户上传、音频缓存、个人简历和文档导出产物不提交。2026-11-03 维护者确认现有课程图片与试听音频没有自制记录或转载授权，默认排除；`assets/seed` 并不代表已授权。详见 [素材核验](media-rights.md)。

`deploy/seed-assets.json` 只是预置媒体路径清单，不是授权清单。部署工具默认排除媒体；只有全部素材取得来源与许可证据后，才可显式使用 `--include-verified-media`。公开源码工具始终排除这些待核验媒体。

文档媒体也按 `docs/public-media.json` 的路径和文件哈希审核，不再整体放行 screenshots 目录。管理员新增/重置密码需明确提供至少12字符的新密码，不再存在固定默认口令。Android 构建与联网验收必须显式传入自己的服务地址；公开副本中的文档 IP 仅为示例。

## 当前源码导出目录（没有 .git）

```powershell
python -m unittest discover -s tools -p "test_*.py"
python tools/audit_git.py --source-tree
python tools/prepare_public_release.py
```

公开包：`work/public/elderly-study-source.zip`，附带 `.sha256`。原文件和旧目录不变；包内不含旧 Git 历史、私有配置或无授权媒体。先检查压缩包文件清单，再解压至新的独立目录，在该目录执行 `git init`、`git add .` 并执行下述索引审计。不要直接将带旧历史的原目录整包上传。

## 实际 Git 仓库

提交前在项目根目录运行：

```powershell
python tools/audit_git.py --worktree
python tools/audit_git.py
```

第一个检查可见的工作目录文件；第二个检查实际 Git 索引（下一次提交的快照），同时检查是否仍在跟踪已忽略的文件和是否包含大于20MiB的文件。审计输出仅包含路径和规则，不打印匹配到的凭据。可通过 `--json-output work/git-audit.json` 保存详细报告。它采用规则扫描，不能保证发现所有形式的敏感数据。

如果要检查本地所有引用可达的历史文件（包括已删除文件）：

```powershell
python tools/audit_git.py --history --json-output work/git-history-audit.json
```

当前待提交快照干净，不代表已有历史干净。旧的主配置历史曾包含真实凭据，`.gitignore`、删除文件或新增脱敏提交都不会撤销它们。必须在服务端实际轮换旧凭据；公开旧仓库前还需决定清理历史或从审查后的源码创建独立的干净仓库。历史重写会影响协作者和远程分支，不应在普通清理中自动执行。

本次工具不自动创建提交、不推送远程、不改写旧历史。凭据轮换状态仍须按 [安全说明](../SECURITY.md) 由维护者核实；当前规则扫描通过不能替代这些工作。
