# 三平台一键构建

在 Windows 本机双击根目录的 `Build-All.cmd`，或在 PowerShell 执行：

```powershell
.\Build-All.ps1
```

- 需要 Python 3、Git、GitHub CLI 和 JDK 21。先执行 `gh auth login`，登录具有仓库写入及 Actions 权限的账号。
- 构建前退出 ChurchPresenter，并提交当前源码修改。脚本不自动暂存、提交或上传未跟踪文件。
- 本机使用 `:composeApp:createDistributable` 更新固定软件入口。
- 将已提交源码通过 GitHub API 上传到独立的 `codex/build-*` 分支，校验 Git 源码树一致后，触发现有 `build.yml`，构建 Windows MSI/MSIX、macOS arm64 DMG 和 macOS x64 DMG。
- 脚本等待构建结束，每个平台遇到失败或 runner 容量不足时自动重试一次，并输出各安装包的下载链接。下载 GitHub Actions 产物需要登录 GitHub。
- macOS 使用 GitHub 的 Mac runner，本机无需安装 Xcode。没有配置 Apple 签名和公证凭据时，产物未签名、未公证。

只构建云端三个版本：`.\Build-All.ps1 -CloudOnly`。
触发后不等待云端结果：`.\Build-All.ps1 -NoWait`。

本机如需额外的 Gradle 仓库配置，可设置 `CHURCHPRESENTER_GRADLE_INIT` 为本机 init script 路径；脚本不会将该配置上传。
