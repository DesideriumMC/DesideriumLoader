# DesideriumLoader

面向 Minecraft 1.6.4 / Forge 9.11.1.1345 的运行环境，支持 Java 8 模组、共享 Mixin 0.8.7 和独立安装器。

[English](README.md)

## Installation

下载 [Windows/Linux 通用安装器 JAR](https://github.com/DesideriumMC/DesideriumLoader-internal/releases/download/v0.0.1/desiderium-installer-0.0.1.jar)，拖入 HMCL 的 Minecraft 1.6.4 版本安装页面，游戏选择 Java 8。
详见 [HMCL 安装说明](installer/LAUNCHERS.zh-CN.md)。同一 JAR 也支持以下独立安装与启动命令。

支持 Windows / Linux x64，需要 Java 8。从 [Release 0.0.1](https://github.com/DesideriumMC/DesideriumLoader-internal/releases/tag/v0.0.1) 下载安装器 JAR。首次安装需要联网。

### Windows

将 Java 8 加入 `PATH`，在安装器目录运行：

```powershell
java -jar desiderium-installer-0.0.1.jar install --instance './Desiderium' --side client --version 0.0.1
java -jar desiderium-installer-0.0.1.jar run --instance './Desiderium' --username Player
```

### Linux

将 Java 8 加入 `PATH`，在安装器目录运行：

```bash
java -jar desiderium-installer-0.0.1.jar install --instance './Desiderium' --side client --version 0.0.1
java -jar desiderium-installer-0.0.1.jar run --instance './Desiderium' --username Player
```

实例目录须为空。专用服务端使用另一目录，以 `--side server` 安装，启动时不传 `--username`，停止时输入 `stop`。Linux 客户端需要图形及音频环境。

将模组放入实例的 `mods` 目录。支持 Java 6、7、8 模组字节码，具体模组和 coremod 可能有额外兼容要求。Loader 已提供 Mixin。

独立客户端使用本地 session，不提供微软账号登录和在线服务器认证。

## Development

当前源码构建支持同键多绑定：按下一个键会同时激活所有使用该键的绑定，
在控制设置或 options.txt 中改键后同样生效，无需另装按键模组。
此变更不会更新已发布的安装器。

开发模组请使用 [DesideriumModTemplate](https://github.com/DesideriumMC/DesideriumModTemplate)，构建工具见 [DesideriumLoom](https://github.com/DesideriumMC/DesideriumLoom)。

本仓库仅跟踪核心运行时与安装器源码、源码补丁和产品说明。
`patches/fml/common` 保存共享 Mixin 接入、发现耗时诊断和 Java 8 visitor 修改；
`patches/minecraft` 保存游戏功能修改。FML 补丁基于原始 Forge 9.11.1.1345
源码，以 `-p1` 应用；Minecraft 补丁在 Forge/MCP 源码准备后以 `-p2` 应用。

完整源码构建、测试、缓存与发布脚手架保留在
[DesideriumLoader-internal](https://github.com/DesideriumMC/DesideriumLoader-internal)。
核心仓库的干净克隆不包含完整构建环境。

HMCL 拖放兼容的单 JAR 安装器生成代码在两端保留，包括 Java 安装器源码、
两个打包模块及其必需的输入清单。使用 internal 准备好的 Loader 和校验缓存生成安装器：

```powershell
python scripts/build_installer.py --loader-build ../DesideriumLoader-internal/.work/build/loader --cache-root ../DesideriumLoader-internal --output .work/installer --javac C:/jdks/jdk17/bin/javac.exe
```

其他脚手架和生成文件由核心仓库的白名单忽略规则排除。

## License

见[许可证范围](NOTICE.md)和[上游声明](LICENSES/)。Forge/FML 等上游组件保留各自许可证。
