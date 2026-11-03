# Runtime metadata schema 1

The installer embeds immutable `releases/<version>-<side>.properties` resources.
Each contains schema, version, minecraft, forge, java, platform, side, count,
contentSha256 and numbered `file.N.{path,sha256,source,role}` entries. Paths are
relative to the installed version; sources are HTTPS or embedded payload resources.
Roles are classpath (ordered), native (Windows DLL/Linux SO archives), resource, agent or asset. Java
Properties escaping applies. The release content digest covers canonical JSON
produced by installer_payload.py; build-report.json also records raw metadata hashes.
Changing any release bytes requires a new release version and installer build.
The Java launcher accepts only versions compiled into its catalog, never arbitrary
external launch commands or JVM arguments from an instance file.

Instance schema: `.desiderium/instance.txt` identifies owned instances; `side`
is fixed per instance, `active` selects an installed catalog release, `versions/`
contains immutable relative runtime trees. Downloads use a SHA-256 object cache.
Every dependency, asset, and extracted native is verified before activation and
before every launch. User mods and worlds remain editable outside the runtime tree.
The active pointer is atomically replaced after verification. Running processes
hold the same instance lock as installation/selection. Switching runtime versions
does not migrate or restore game data. Same-version repair is explicit.

No Minecraft JAR, merged game JAR, generated source or game asset is redistributed.
The installer downloads these directly from the locked Mojang URLs. Its build
inputs are the exact accepted L1/L2 artifacts and existing fixed dependency locks.
The build report and installer SHA-256 form the local distribution identity.

L5 adds `zip:HTTPS_URL|ARCHIVE_SHA256|ENTRY` sources. Both the downloaded archive
and extracted entry are verified; archive reuse is protected by an object lock.
Resource entries live under `resources/`, which is an additional classpath root.
Each distribution targets exactly one platform; installing with a mismatched
platform is rejected. Candidate release IDs are distinct from historical L4 IDs.

Agent entries are verified standalone Java agents enabled only for the child game JVM.
L5 ships a separate instrumentation agent alongside the official LaunchWrapper JAR.

## Standard launcher installation

`install-hmcl` consumes the same embedded, platform-checked client release manifest.
It stages a verified standalone runtime, then exports a complete standard version
JSON with an explicit runtime library list. The launcher directly invokes LaunchWrapper with its own authentication,
game-directory and memory settings. The exported JVM arguments include the original
diagnostics agent.

Libraries use content-qualified coordinates under `dev.desiderium.runtime` in the
target game's `libraries/` directory, with standard SHA-1/size descriptors in addition
to the installer's SHA-256 checks. Original HTTPS URLs are retained where available.
Locally generated/extracted libraries have no remote URL. The installer creates the
MCP resource JAR locally; it is never embedded in the published installer.

Native classifiers and the legacy asset index retain standard launcher semantics.
Assets are installed both as content-addressed objects and the legacy virtual tree.
`launcher/base.properties` and `launcher/legacy.json` carry the official cached
Minecraft/asset-index metadata needed to export the profile; index and client hashes
are checked against those descriptors.

The version directory is atomically committed only after export. Existing versions
are rejected, and existing shared files are verified against the expected hashes.
Failed installs can leave verified shared objects/libraries but no activated version.
The launcher manages exported versions. Use `run`, `select`, and `verify` for
standalone instances. See `LAUNCHERS.zh-CN.md`.

## Single-JAR installation protocol

`scripts/build_installer.py` combines both platform catalogs under
`releases/<platform>/<version>-<side>.properties`; the standalone entry point selects
the current platform. The same JAR carries `install_profile.json`, `version.json`
and four embedded Maven artifacts: Loader, Mixin, diagnostics agent and processor.
HMCL uses its Forge spec-1 installer path and records a Forge patch with the
Desiderium version. Start with an original Minecraft 1.6.4 version.

The processor downloads the pinned official Forge archive, validates its SHA-256,
extracts and verifies the required MCP resource, and writes a deterministic STORED
resource JAR. The install profile declares its expected SHA-1; the processor also
checks SHA-256. Fixed ZIP timestamps and STORED entries keep the output consistent
across Java versions and time zones. The resource JAR is generated locally.

Runtime dependencies retain their upstream Maven coordinates so HMCL can merge
versions correctly. Both Windows/Linux native classifiers are described in the
version JSON. Tests: `tests/test_launchers.py` and
`tests/launchers/SingleJarProbe.java`.
