# Local project instructions

- 所有已授权功能持续集成在当前源码中，统一构建同一个软件。
- 每次功能更新构建时，同步构建 Windows 和 macOS，默认包含 Apple Silicon（arm64）与 Intel（x64）两个 macOS 版本，使用同一份源码与功能变更。
- macOS 安装包使用现有 `.github/workflows/build.yml` 的 macOS runner 构建，交付时分别报告各平台的构建结果与可下载产物；遇到权限、签名或运行环境阻塞时明确说明。
- 使用 `:composeApp:createDistributable` 的标准输出目录。
- 固定软件入口为 `D:\churchpre\composeApp\build\compose\binaries\main\app\ChurchPresenter\ChurchPresenter.exe`，后续功能更新同一入口。
- 禁止为各个功能另建 `test-app*` 等独立软件输出目录。
- 遵循项目现有 `AGENT.md`，文件读取先定位再局部读取，忽略依赖、编译产物和日志。
- 终端构建、测试及包管理输出必须保持安静或截断，最多返回 50 行。
