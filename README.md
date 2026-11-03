# DesideriumLoader

Minecraft 1.6.4 / Forge 9.11.1.1345 with Java 8 mod support, shared Mixin 0.8.7
and shared key bindings.

[中文](README.zh-CN.md)

## Mod development

Start with [DesideriumModTemplate](https://github.com/DesideriumMC/DesideriumModTemplate)
and [DesideriumLoom](https://github.com/DesideriumMC/DesideriumLoom).
Use JDK 17 for Gradle and compilation, target Java 8, and run the game on Java 8.

Download and extract the SDK archive from this repository's Releases page.
Set the template's `gradle.properties`:

```properties
loaderJar=D:/sdk/desideriumloader-0.0.1.jar
loaderMixinJar=D:/sdk/desiderium-loader-mixin-0.0.1.jar
```

Run `./build.ps1 build` on Windows or `./build.sh build` on Linux.
The template configures dependencies, development runs, Mixin annotation
processing and refmaps. Players do not need a separate Mixin bootstrap mod.

Java 6, 7 and 8 mod bytecode is supported. Global Forge ASM is 5.2; Mixin uses
an isolated modern ASM namespace. Individual coremods may require compatibility
testing. Bindings assigned to the same key activate together, including after
rebinding in Controls or options.txt and when using mouse buttons.

## Installation

Use `desiderium-installer-0.0.1.jar` from Releases. In HMCL, drag it into the
installation page for Minecraft 1.6.4, then select Java 8 for the game.
See the [HMCL guide](installer/LAUNCHERS.zh-CN.md).

```sh
java -jar desiderium-installer-0.0.1.jar install --instance ./Desiderium --side client --version 0.0.1
java -jar desiderium-installer-0.0.1.jar run --instance ./Desiderium --username Player
```

Requires Windows/Linux x64 and Java 8. First installation needs internet access.
Use an empty instance directory; put mods in its `mods` folder.
For a server, use `--side server` and omit `--username` when running; stop it with
`stop`. HMCL manages sign-in; the standalone client uses a local session.

## Sources

`runtime/` contains the Mixin bridge and diagnostics; `installer/` contains the
Java installer. FML patches apply to pristine Forge 9.11.1.1345 sources with
`-p1`; Minecraft patches apply after Forge/MCP setup with `-p2`.
The single-JAR packaging entry point is `scripts/build_installer.py`.

## License

See [license scope](NOTICE.md) and [upstream notices](LICENSES/).
Minecraft, Forge/FML and other upstream components retain their own terms.
