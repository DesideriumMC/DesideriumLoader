# DesideriumLoader

面向 Minecraft 1.6.4 / Forge 9.11.1.1345，支持 Java 8 模组、共享 Mixin 0.8.7
和同键多绑定。

[English](README.md)

## 模组开发

从 [DesideriumModTemplate](https://github.com/DesideriumMC/DesideriumModTemplate)
开始，构建工具使用 [DesideriumLoom](https://github.com/DesideriumMC/DesideriumLoom)。
使用 JDK 17 运行 Gradle 和编译，目标字节码设为 Java 8，游戏使用 Java 8。

在本仓库的 Releases 页面下载并解压 SDK，在模板的 `gradle.properties` 中设置：

```properties
loaderJar=D:/sdk/desideriumloader-0.0.1.jar
loaderMixinJar=D:/sdk/desiderium-loader-mixin-0.0.1.jar
```

Windows 执行 `./build.ps1 build`，Linux 执行 `./build.sh build`。
模板负责依赖、开发运行配置、Mixin 注解处理和 refmap 生成。
Loader 已提供 Mixin，玩家无需另装 Mixin 引导模组。

支持 Java 6、7、8 模组字节码。Forge 全局 ASM 为 5.2；Mixin 使用隔离的现代
ASM 命名空间。具体 coremod 仍需单独验证兼容性。同一按键上的所有绑定会同时
激活，支持在控制设置或 options.txt 中直接改键，也支持鼠标按键绑定。

## 安装

使用 Releases 中的 `desiderium-installer-0.0.1.jar`，拖入 HMCL 的
Minecraft 1.6.4 版本安装页面，游戏选择 Java 8。
详见 [HMCL 安装说明](installer/LAUNCHERS.zh-CN.md)。

```sh
java -jar desiderium-installer-0.0.1.jar install --instance ./Desiderium --side client --version 0.0.1
java -jar desiderium-installer-0.0.1.jar run --instance ./Desiderium --username Player
```

支持 Windows / Linux x64，需要 Java 8，首次安装需要联网。
使用空实例目录，将模组放入实例的 `mods` 目录。
服务端以 `--side server` 安装，启动时不传 `--username`，停止时输入 `stop`。
账号登录由 HMCL 管理；独立客户端使用本地 session。

## 源码

`runtime/` 包含 Mixin 桥接和诊断源码，`installer/` 包含 Java 安装器源码。
FML 补丁基于原始 Forge 9.11.1.1345 源码，以 `-p1` 应用；
Minecraft 补丁在 Forge/MCP 源码准备后以 `-p2` 应用。
单 JAR 打包入口为 `scripts/build_installer.py`。

## 许可证

见[许可证范围](NOTICE.md)和[上游声明](LICENSES/)。
Minecraft、Forge/FML 等上游组件保留各自条款。
