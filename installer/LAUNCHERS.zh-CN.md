# 使用 HMCL

从 [Release 0.0.1](https://github.com/DesideriumMC/DesideriumLoader-internal/releases/tag/v0.0.1) 下载通用安装器 JAR。

## 拖入安装

使用支持 Forge processor 安装格式的单 JAR：`desiderium-installer-0.0.1.jar`。
同一文件支持 Windows / Linux x64，游戏需要 Java 8；首次安装需要联网。

1. 在 HMCL 中安装原版 Minecraft 1.6.4，建议创建独立版本。
2. 打开该版本的安装管理页面，将安装器 JAR 拖入页面，或通过离线安装入口选择它。
3. 等待安装完成，将游戏 Java 设置为 x64 Java 8，然后启动。

当前 HMCL 通过 Forge 兼容通道管理此组件，显示版本为 `desiderium-0.0.1`。
更新运行环境时使用 Desiderium 安装器。HMCL 程序自身的 Java 环境按其版本要求配置。

## 命令行安装

也可由安装器直接生成标准版本目录。`--minecraft` 指向 HMCL 配置的 Minecraft 游戏根目录：

```sh
java -jar desiderium-installer-0.0.1.jar install-hmcl --minecraft "D:/Games/Minecraft/.minecraft" --version 0.0.1
```

安装完成后，在 HMCL 中选择该游戏目录并刷新版本列表，选择 `desiderium-0.0.1`。
Linux 使用相同 JAR 和实际目录路径。

HMCL 管理账号、内存、窗口大小和游戏目录。模组放入 HMCL 为该版本配置的 `mods/` 目录；
`config/` 和 `saves/` 的位置同样取决于 HMCL 的目录隔离设置。
Loader 已提供共享 Mixin，模组开发说明见 [DesideriumLoom](https://github.com/DesideriumMC/DesideriumLoom)。

## 缓存与重试

拖入安装由 HMCL 管理运行库下载。资源处理器在 `libraries/.desiderium-cache/` 中缓存官方 Forge 归档，
校验归档与提取结果后生成 MCP 运行资源 JAR。失败时可按 HMCL 日志检查网络或对应缓存。

以下缓存选项适用于命令行安装：

默认下载缓存位于游戏根目录的 `.desiderium-cache/`。可通过 `--cache DIR` 指定共享缓存，
或使用 `--offline` 从已有完整缓存安装。

安装器校验下载文件和已有共享资源，完成后提交新版本目录。同名版本或文件内容冲突会终止安装，
请按错误信息检查对应路径。已校验的缓存可供重试使用。
安装期间请关闭其他安装或修复任务，避免同时写入共享库。

## 版本目录

安装器生成标准 Minecraft 版本目录：

```text
.minecraft/
  versions/
    desiderium-0.0.1/
      desiderium-0.0.1.json
      desiderium-0.0.1.jar
  libraries/
  assets/
```

版本 JSON 配置 LaunchWrapper、FML Tweaker、共享 Mixin、ASM 和诊断 Agent。
库文件包含标准 SHA-1、大小及可用的上游下载地址，安装器使用 SHA-256 校验原始工件。
MCP 运行资源从固定官方 Forge 归档在本机提取。完整元数据说明见 [METADATA.md](METADATA.md)。

## 已验证环境

单 JAR 的拖入安装通道使用 HMCL 3.16.4 的本地安装任务验证：

| 环境 | 验证内容 |
| --- | --- |
| Windows x64 / Java 8 | 安装器识别、工件解包、processor 执行、运行库合并、账号/Agent 参数、客户端 FML/OpenAL 初始化 |
| Ubuntu WSL2 Linux x64 / Java 8 | 同一 JAR 的平台选择、独立服务端安装/校验、processor 资源生成 |

资源生成已检查 Java 8/17 的一致性、重复执行、损坏缓存拒绝和跨时区一致性。
人工世界操作、Linux 拖入安装/图形客户端和在线账号认证仍待验证。
