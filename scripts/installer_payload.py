"""Build a platform payload from the current Loader and Mixin build reports.

Build tooling only. The generated JAR needs neither Python, Gradle nor a JDK.
Minecraft binaries/assets and external libraries are downloaded, never embedded.
"""
import argparse
import hashlib
import json
import subprocess
import zipfile
import io
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()


def props(values):
    def escape(s):
        return str(s).replace("\\", "\\\\").replace(":", "\\:").replace("=", "\\=")
    return "".join(escape(k) + "=" + escape(v) + "\n" for k, v in values.items()).encode("ascii")


def build(output, javac, platform, loader_build, public_version, cache_root=None):
    cache_root = cache_root or ROOT
    output.mkdir(parents=True, exist_ok=True)
    classes = output / "classes"
    classes.mkdir(exist_ok=True)
    subprocess.run([str(javac), "--release", "8", "-encoding", "UTF-8", "-d", str(classes),
                    *map(str, sorted((ROOT / "installer/src/dev/desiderium/install").glob("*.java")))], check=True)
    entries = {"META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\nMain-Class: dev.desiderium.install.Main\r\n\r\n"}
    entries.update({p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob("*.class")})
    agent_classes = output / "agent-classes"
    agent_classes.mkdir(exist_ok=True)
    agent_cp = [cache_root / ".work/libraries/net/minecraft/launchwrapper/1.8/launchwrapper-1.8.jar",
                cache_root / ".work/libraries/org/ow2/asm/asm-debug-all/4.1/asm-debug-all-4.1.jar"]
    import os
    subprocess.run([str(javac), "--release", "8", "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, agent_cp)),
                    "-d", str(agent_classes), str(ROOT / "runtime/diagnostics/src/dev/desiderium/diagnostics/DiagnosticsAgent.java"),
                    str(ROOT / "runtime/diagnostics/src/net/minecraft/launchwrapper/DesideriumDiagnostics.java")], check=True)
    buf = io.BytesIO()
    agent_entries = {p.relative_to(agent_classes).as_posix(): p.read_bytes() for p in agent_classes.rglob("*.class")}
    agent_entries["META-INF/MANIFEST.MF"] = b"Manifest-Version: 1.0\r\nPremain-Class: dev.desiderium.diagnostics.DiagnosticsAgent\r\n\r\n"
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(agent_entries.items()):
            info = zipfile.ZipInfo(name, (2000, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    entries["payload/runtime/diagnostics-agent.jar"] = buf.getvalue()
    agent_hash = hashlib.sha256(buf.getvalue()).hexdigest()
    report = json.loads((loader_build / "build-report.json").read_text())
    payloads = {
        "loader.jar": (loader_build / "desideriumloader.jar", report["loaderSha256"]),
        "mixin-runtime.jar": (loader_build / "mixin-runtime.jar", report["runtimeSha256"]),
    }
    payload_hashes = {}
    for name, (path, expected) in payloads.items():
        if sha(path) != expected:
            raise RuntimeError("Build artifact changed: " + str(path))
        data = path.read_bytes()
        if name == "loader.jar":
            buf = io.BytesIO()
            with zipfile.ZipFile(path) as src, zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as dest:
                for member in sorted(src.namelist()):
                    if member == "deobfuscation_data-1.6.4.lzma":
                        continue
                    info = zipfile.ZipInfo(member, (2000, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    dest.writestr(info, src.read(member))
            data = buf.getvalue()
        entries["payload/runtime/" + name] = data
        payload_hashes[name] = hashlib.sha256(data).hexdigest()
    libraries = json.loads((ROOT / "inputs/l0-libraries.lock.json").read_text())["artifacts"]
    libraries = [x for x in libraries if "org/ow2/asm/asm-debug-all/" not in x["path"]]
    libraries += json.loads((ROOT / "inputs/java8-libraries.lock.json").read_text())["artifacts"]
    mc = json.loads((ROOT / "inputs/l0-inputs.lock.json").read_text())["artifacts"]
    assets_base = cache_root / ".work/assets"
    asset_index = json.loads((cache_root / ".work/launcher/legacy.json").read_text())["objects"]
    vanilla = json.loads((cache_root / ".work/launcher/version.json").read_text())
    index_bytes = (cache_root / ".work/launcher/legacy.json").read_bytes()
    if hashlib.sha1(index_bytes).hexdigest() != vanilla["assetIndex"]["sha1"]:
        raise RuntimeError("Launcher asset index differs from official metadata")
    entries["launcher/legacy.json"] = index_bytes
    entries["launcher/base.properties"] = props({
        **{"assetIndex." + k: v for k, v in vanilla["assetIndex"].items()},
        **{"client." + k: v for k, v in vanilla["downloads"]["client"].items()},
    })
    assets = []
    for name, obj in sorted(asset_index.items()):
        path = assets_base / name
        assert hashlib.sha1(path.read_bytes()).hexdigest() == obj["hash"], name
        assets.append(dict(path="assets/" + name, sha256=sha(path), source="https://resources.download.minecraft.net/" + obj["hash"][:2] + "/" + obj["hash"], role="asset"))
    reports = {}
    for side in ["client", "server"]:
        files = []
        def add(path, digest, source, role="classpath"):
            files.append(dict(path=path, sha256=digest, source=source, role=role))
        add("diagnostics-agent.jar", agent_hash, "embedded:payload/runtime/diagnostics-agent.jar", "agent")
        for name in ["loader.jar", "mixin-runtime.jar"]:
            add(name, payload_hashes[name], "embedded:payload/runtime/" + name)
        upstream = next(x for x in mc if x["name"].endswith("-universal.jar"))
        member = "deobfuscation_data-1.6.4.lzma"
        with zipfile.ZipFile(cache_root / ".work/inputs" / upstream["name"]) as z:
            data = z.read(member)
        with zipfile.ZipFile(payloads["loader.jar"][0]) as z:
            if z.read(member) != data:
                raise RuntimeError("Runtime MCP data differs from official Forge")
        add("resources/" + member, hashlib.sha256(data).hexdigest(),
            "zip:" + upstream["url"] + "|" + upstream["sha256"] + "|" + member, "resource")
        game = next(x for x in mc if x["name"] == "minecraft-" + side + ".jar")
        add("minecraft.jar", game["sha256"], game["url"])
        for lib in libraries:
            name = lib["path"]
            if "-sources" in name:
                continue
            if lib["extract"]:
                if side != "client" or not name.endswith("-natives-" + platform.split("-")[0] + ".jar"):
                    continue
            add("libraries/" + name, lib["sha256"], lib["url"], "native" if lib["extract"] else "classpath")
        if side == "client":
            files.extend(assets)
        content = hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()
        release_version = public_version
        values = dict(schema=1, minecraft="1.6.4", forge="9.11.1.1345", version=release_version,
                      side=side, platform=platform, java="8", count=len(files), contentSha256=content)
        for i, item in enumerate(files):
            values.update({f"file.{i}.{k}": v for k, v in item.items()})
        name = release_version + "-" + side
        data = props(values)
        entries["releases/" + name + ".properties"] = data
        reports[name] = dict(files=len(files), metadataSha256=hashlib.sha256(data).hexdigest(), contentSha256=content)
    for name in ["INSTALL.zh-CN.md", "LAUNCHERS.zh-CN.md", "METADATA.md", "NOTICES.md"]:
        entries[name] = (ROOT / "installer" / name).read_bytes()
    entries["LICENSE-Desiderium.txt"] = (ROOT / "LICENSE").read_bytes()
    entries["LICENSE-SCOPE.md"] = (ROOT / "NOTICE.md").read_bytes()
    for path in sorted((ROOT / "LICENSES").rglob("*")):
        if path.is_file():
            entries["licenses/" + path.relative_to(ROOT / "LICENSES").as_posix()] = path.read_bytes()
    entries["licenses/ASM-5.2.txt"] = (ROOT / "LICENSES/ASM-5.2.txt").read_bytes()
    entries["java8-libraries.lock.json"] = (ROOT / "inputs/java8-libraries.lock.json").read_bytes()
    entries["NOTICES.md"] = entries["NOTICES.md"].replace(b"Global Forge ASM is unchanged.", b"The Java 8 variant uses global ASM 5.2; the isolated Mixin ASM remains separate.")
    entries["NOTICES.md"] += b"\nJava 8 variant: FML-owned visitors use ASM5. Public ASM is 5.2 (BSD-3-Clause); game runtime is Java 8. Legacy Java 6/7 mods and new Java 8 mods are tested together. See licenses/ASM-5.2.txt and java8-libraries.lock.json.\n"
    jar = output / ("desiderium-installer-" + public_version + ".jar")
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2000, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    for name in ["INSTALL.zh-CN.md", "LAUNCHERS.zh-CN.md", "NOTICES.md"]:
        (output / name).write_bytes(entries[name])
    report = dict(installer=jar.name, sha256=sha(jar), releases=reports, embedsMinecraft=False, compilerTarget=8)
    (output / "build-report.json").write_text(json.dumps(report, indent=2) + "\n")
    (output / (jar.name + ".sha256")).write_text(sha(jar) + "  " + jar.name + "\n")
    print(json.dumps(report, indent=2))
    return jar
