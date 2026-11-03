# DesideriumLoader

A Minecraft 1.6.4 / Forge 9.11.1.1345 runtime with Java 8 mod support, shared Mixin 0.8.7 and a standalone installer.

[中文](README.zh-CN.md)

## Installation

Download the [installer JAR](https://github.com/DesideriumMC/DesideriumLoader-internal/releases/download/v0.0.1/desiderium-installer-0.0.1.jar) for Windows/Linux x64.
Drag it into the installation page of a Minecraft 1.6.4 version in HMCL, then select Java 8 for the game. See the [HMCL installation guide](installer/LAUNCHERS.zh-CN.md).
The same JAR also supports the standalone commands below.

Requires Java 8 on Windows or Linux x64. Download the installer from [Release 0.0.1](https://github.com/DesideriumMC/DesideriumLoader-internal/releases/tag/v0.0.1). The first installation requires internet access.

### Windows

With Java 8 on `PATH`, run in the installer directory:

```powershell
java -jar desiderium-installer-0.0.1.jar install --instance './Desiderium' --side client --version 0.0.1
java -jar desiderium-installer-0.0.1.jar run --instance './Desiderium' --username Player
```

### Linux

With Java 8 on `PATH`, run in the installer directory:

```bash
java -jar desiderium-installer-0.0.1.jar install --instance './Desiderium' --side client --version 0.0.1
java -jar desiderium-installer-0.0.1.jar run --instance './Desiderium' --username Player
```

Use an empty instance directory. For a dedicated server, install with `--side server` into a separate directory, then run it without `--username`. Stop the server with `stop`. Linux clients require a working graphical and audio environment.

Place mods in the instance's `mods` directory. Java 6, 7 and 8 mod bytecode is supported; individual mods and coremods may have additional compatibility requirements. Mixin is provided by the Loader.

The standalone client uses a local session and does not provide Microsoft account sign-in or authenticated online-server access.

## Development

Current source builds activate every binding assigned to a pressed key, including
after rebinding in Controls or options.txt. No additional key-binding mod is needed.
This does not update the previously published installer.

To create a mod, use [DesideriumModTemplate](https://github.com/DesideriumMC/DesideriumModTemplate). Build tooling is maintained in [DesideriumLoom](https://github.com/DesideriumMC/DesideriumLoom).

This repository tracks core runtime and installer sources, source patches and
product documentation. `patches/fml/common` contains shared-Mixin integration,
discovery timing and Java 8 visitor changes; `patches/minecraft` contains game
changes. FML patches use pristine Forge 9.11.1.1345 sources with `-p1`; Minecraft
patches apply after Forge/MCP setup with `-p2`.

The full source build, tests, caches and release scaffolding live in
[DesideriumLoader-internal](https://github.com/DesideriumMC/DesideriumLoader-internal).
A clean clone of this core repository does not contain that build environment.

The HMCL-compatible single-JAR installer generator is retained in both
repositories, with the Java installer sources and its required input manifests.
To package a prepared Loader build using internal's verified caches:

```powershell
python scripts/build_installer.py --loader-build ../DesideriumLoader-internal/.work/build/loader --cache-root ../DesideriumLoader-internal --output .work/installer --javac C:/jdks/jdk17/bin/javac.exe
```

Other scaffolding and generated outputs are ignored by the core allowlist.

## License

See [license scope](NOTICE.md) and [upstream notices](LICENSES/). Upstream Forge/FML and other components retain their respective licenses.
