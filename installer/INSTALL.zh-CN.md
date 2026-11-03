# Desiderium 安装与启动

适用：Windows / Linux x64，Minecraft 1.6.4 / Forge 9.11.1.1345，Java 8。当前单 JAR 同时支持两个平台。
玩家只需 Java 8 和本安装 JAR，不需要 Gradle、Python、JDK 或开发目录。
默认 `install` / `run` 使用独立实例；该模式客户端采用本地 session，不提供微软账号登录或在线服认证。
在 HMCL 的 Minecraft 1.6.4 版本安装页面拖入单 JAR，即可安装；
也可使用 `install-hmcl --minecraft DIR --version 0.0.1` 生成标准版本目录。账号与游戏启动由 HMCL 管理。详见 [HMCL 安装说明](LAUNCHERS.zh-CN.md)。请使用你有权使用的游戏。

在 PowerShell 中令 `$java` 指向 Java 8 的 java.exe，例如：

```powershell
$java = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot\bin\java.exe'
& $java -jar .\desiderium-installer-0.0.1.jar list
& $java -jar .\desiderium-installer-0.0.1.jar install --instance 'D:\Games\Desiderium Client' --side client --version 0.0.1
& $java -jar .\desiderium-installer-0.0.1.jar run --instance 'D:\Games\Desiderium Client' --username Player
```

首次联网下载原版游戏、官方资源和运行库，并逐项校验 SHA-256。安装目标必须不存在或为空；不接受符号链接、目录联接或无关非空实例。仅写指定实例和显式指定的缓存目录。安装后可移动整个实例，安装 JAR 也可移到其他位置。

服务端使用另一个空目录：

```powershell
& $java -jar .\desiderium-installer-0.0.1.jar install --instance 'D:\Games\Desiderium Server' --side server --version 0.0.1
& $java -jar .\desiderium-installer-0.0.1.jar run --instance 'D:\Games\Desiderium Server'
```

默认保留游戏原本的服务器设置；按自己的用途编辑实例中的 server.properties。停止时输入 `stop`，等待保存完成。不要强制关窗。

## 首次公开发行 0.0.1

本安装包内嵌运行版本 `0.0.1`；使用 `list` 查看。Windows / Linux x64 请下载各自的平台包。
安装器内嵌 Loader、Mixin 和固定版本清单；玩家不需要开发目录。MCP 数据从锁定官方 Forge 归档在本地提取。

使用 `verify --instance DIR` 检查实例。缓存默认为实例的 `.desiderium/cache`，可用 `--cache DIR` 指定。
`--offline` 仅使用内嵌制品与已有校验通过的缓存，首次离线安装须事先准备完整缓存。
失败时按错误中列出的文件、来源和校验值修复缓存后重试。不要删除存档或模组。

模组放入实例的 `mods` 目录，Loader 已提供共享 Mixin。Linux 客户端需要图形和音频环境。
安装器与 SHA256SUMS 应来自可信发行渠道。许可证、对应源码及原有运行验收记录随包提供。
首次公开发行版本为 0.0.1，沿用已完成内部验收的运行产物。
