"""Build one Windows/Linux installer with the Forge processor installation protocol."""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import subprocess
import zipfile

from installer_payload import ROOT, build, props

VERSION = "0.0.1"
sha = lambda data: hashlib.sha256(data).hexdigest()
sha1 = lambda data: hashlib.sha1(data).hexdigest()


def archive(entries):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2000, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    return buf.getvalue()


def coordinate_path(coordinate):
    group, artifact, version, *classifier = coordinate.split(":")
    return "/".join([group.replace(".", "/"), artifact, version,
                     artifact + "-" + version + ("-" + classifier[0] if classifier else "") + ".jar"])


def descriptor(coordinate, data, url=None):
    artifact = dict(path=coordinate_path(coordinate), sha1=sha1(data), size=len(data))
    if url:
        artifact["url"] = url
    return dict(name=coordinate, downloads=dict(artifact=artifact), checksums=[sha1(data)])


def locked_bytes(item, cache_root):
    path = cache_root / ".work/libraries" / item["path"]
    if path.is_file() and sha(path.read_bytes()) == item["sha256"]:
        return path.read_bytes()
    raise RuntimeError("Missing or changed locked dependency: " + item["path"])


def main(a):
    out = a.output.resolve()
    if out.exists():
        raise RuntimeError("Use a new output directory: " + str(out))
    out.mkdir(parents=True)
    platforms = {}
    for platform in ["windows-x64", "linux-x64"]:
        jar = build(out / "build" / platform, a.javac, platform, a.loader_build.resolve(), VERSION, a.cache_root)
        with zipfile.ZipFile(jar) as z:
            platforms[platform] = {n: z.read(n) for n in z.namelist()}
    entries = {n: b for n, b in platforms["windows-x64"].items() if not n.startswith("releases/")}
    for platform, files in platforms.items():
        for name, data in files.items():
            if name.startswith("releases/"):
                entries["releases/" + platform + "/" + name[len("releases/"):]] = data
            elif name.endswith(".class") or name.startswith("payload/"):
                if entries[name] != data:
                    raise RuntimeError("Platform runtime differs: " + name)

    inputs = json.loads((ROOT / "inputs/l0-inputs.lock.json").read_text())["artifacts"]
    forge = next(x for x in inputs if x["name"].endswith("-universal.jar"))
    forge_path = a.cache_root / ".work/inputs" / forge["name"]
    if sha(forge_path.read_bytes()) != forge["sha256"]:
        raise RuntimeError("Official Forge archive changed")
    member = "deobfuscation_data-1.6.4.lzma"
    with zipfile.ZipFile(forge_path) as z:
        raw = z.read(member)
    raw_file = out / "build/resource-input.lzma"
    raw_file.write_bytes(raw)
    resource_file = out / "build/local-resources.jar"
    java = a.javac.with_name("java.exe" if a.javac.suffix == ".exe" else "java")
    subprocess.run([str(java), "-cp", str(out / "build/windows-x64/classes"),
                    "dev.desiderium.install.Processor", "--pack", str(raw_file), str(resource_file), member], check=True)
    resource_data = resource_file.read_bytes()
    resource_coord = "dev.desiderium:resources:" + VERSION
    processor_config = dict(url=forge["url"], archiveSha256=forge["sha256"], entry=member,
                            entrySha256=sha(raw), output=coordinate_path(resource_coord),
                            outputSha256=sha(resource_data))
    processor = archive({
        "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\nMain-Class: dev.desiderium.install.Processor\r\n\r\n",
        "dev/desiderium/install/Processor.class": entries["dev/desiderium/install/Processor.class"],
        "processor.properties": props(processor_config),
        "LICENSE-Desiderium.txt": entries["LICENSE-Desiderium.txt"],
    })
    bundled = []
    runtime = []
    for artifact, data in [
        ("loader", entries["payload/runtime/loader.jar"]),
        ("mixin", entries["payload/runtime/mixin-runtime.jar"]),
        ("diagnostics", entries["payload/runtime/diagnostics-agent.jar"]),
        ("processor", processor),
    ]:
        coordinate = "dev.desiderium:" + artifact + ":" + VERSION
        lib = descriptor(coordinate, data)
        entries["maven/" + coordinate_path(coordinate)] = data
        bundled.append(lib)
        if artifact != "processor":
            runtime.append(lib)
    runtime.insert(0, descriptor(resource_coord, resource_data))
    locked = json.loads((ROOT / "inputs/l0-libraries.lock.json").read_text())["artifacts"]
    locked = [x for x in locked if "org/ow2/asm/asm-debug-all/" not in x["path"]]
    locked += json.loads((ROOT / "inputs/java8-libraries.lock.json").read_text())["artifacts"]
    natives = {}
    for item in locked:
        path = item["path"]
        if "-sources" in path:
            continue
        parts = path.split("/")
        group, artifact, version = ".".join(parts[:-3]), parts[-3], parts[-2]
        coord = ":".join([group, artifact, version])
        data = locked_bytes(item, a.cache_root)
        if not item["extract"]:
            runtime.append(descriptor(coord, data, item["url"]))
        else:
            os_name = next((os for os in ["windows", "linux"] if "-natives-" + os in path), None)
            if not os_name:
                continue
            native = natives.setdefault(coord, dict(name=coord, downloads=dict(classifiers={}), natives={},
                                                    extract=dict(exclude=["META-INF/"])))
            classifier = "natives-" + os_name
            native["natives"][os_name] = classifier
            native["downloads"]["classifiers"][classifier] = dict(path=path, sha1=sha1(data), size=len(data), url=item["url"])
    runtime.extend(natives.values())
    vanilla = json.loads((a.cache_root / ".work/launcher/version.json").read_text())
    profile = {k: copy.deepcopy(vanilla[k]) for k in ["assets", "assetIndex", "javaVersion"] if k in vanilla}
    profile.update(id="desiderium-" + VERSION, type="release", mainClass="net.minecraft.launchwrapper.Launch",
                   time="2026-11-03T00:00:00Z", releaseTime="2026-11-03T00:00:00Z",
                   javaVersion=dict(component="jre-legacy", majorVersion=8), libraries=runtime,
                   minecraftArguments=vanilla["minecraftArguments"] + " --tweakClass cpw.mods.fml.common.launcher.FMLTweaker",
                   arguments=dict(jvm=["-Djava.library.path=${natives_directory}",
                                       "-Dfml.ignoreInvalidMinecraftCertificates=true", "-cp", "${classpath}",
                                       "-javaagent:${library_directory}/" + coordinate_path("dev.desiderium:diagnostics:" + VERSION)]))
    install = dict(spec=1, profile="Desiderium", version="desiderium-" + VERSION, minecraft="1.6.4",
                   json="/version.json", path="dev.desiderium:loader:" + VERSION, libraries=bundled, data={},
                   processors=[dict(jar="dev.desiderium:processor:" + VERSION, classpath=[], sides=["client"],
                                    args=["--libraries", "{LIBRARY_DIR}"],
                                    outputs={"[" + resource_coord + "]": "'" + sha1(resource_data) + "'"})])
    entries["install_profile.json"] = (json.dumps(install, indent=2) + "\n").encode()
    entries["version.json"] = (json.dumps(profile, indent=2) + "\n").encode()
    result = out / ("desiderium-installer-" + VERSION + ".jar")
    result.write_bytes(archive(entries))
    (out / (result.name + ".sha256")).write_text(sha(result.read_bytes()) + "  " + result.name + "\n")
    for name in ["INSTALL.zh-CN.md", "LAUNCHERS.zh-CN.md", "NOTICES.md"]:
        (out / name).write_bytes(entries[name])
    report = dict(version=VERSION, installer=result.name, sha256=sha(result.read_bytes()),
                  platforms=list(platforms), format="Forge processor spec 1", redistributesMcpData=False,
                  resourceSha1=sha1(resource_data), resourceSha256=sha(resource_data))
    (out / "build-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--cache-root", type=lambda v: Path(v).resolve(), default=ROOT,
                   help="Directory containing prepared .work libraries, Forge input and launcher assets")
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--loader-build", type=Path, required=True)
    p.add_argument("--javac", type=Path, required=True)
    main(p.parse_args())
