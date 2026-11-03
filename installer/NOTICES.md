# Desiderium distribution notices

Minecraft belongs to Mojang/Microsoft. No Minecraft binaries, game assets,
merged JARs or generated Minecraft sources are included. They are downloaded
from locked official sources for the user's local installation.

Forge 9.11.1.1345 uses the Minecraft Forge Public Licence 1.0; FML uses LGPL
2.1 or later with the exceptions and third-party notices in LICENSE-fml.txt.
These terms continue to apply to their modifications. The corresponding
upstream source files, local modifications, build scripts and provenance are
provided with the source distribution. Existing credits and notices remain
inside Loader archives. No additional rights to MCP data are granted.

L5 candidate packages remove deobfuscation_data-1.6.4.lzma from embedded Loader
archives. Installation extracts the identical data locally from the hash-pinned
official Forge universal archive. The original Forge archive and the extracted
mapping are not redistributed in the candidate package.

The isolated shared runtime contains SpongePowered Mixin 0.8.7 (MIT), ASM 9.7.1
(BSD-3-Clause), Guava 21.0, Gson 2.8.9 and Log4j 2.24.3 (Apache-2.0). Collected
upstream notices are supplied separately so shading duplicate file names does
not hide notices from another component. Global Forge ASM is unchanged.

Loom is based on Voldeloom da68a85cc6b5b6e336edd6f37db37f5b9657441e (MIT).
Its embedded source notices, LZMA SDK and Stitch redistribution notices remain.
Runtime and build tooling are separate; development inputs are not player files.

See license-inventory.json and licenses/ for exact artifact versions, SHA-256,
upstream POMs, inherited license declarations and preserved license text.
Download-only entries are not embedded in the installer. A missing declaration
is recorded as unresolved, rather than treated as permission to redistribute.
L5 no longer embeds the adapted LaunchWrapper binary. It downloads the original
upstream 1.8 artifact and uses an independently authored diagnostic Java agent
that modifies the local in-memory class at launch. No Mojang launcher classes
are included in that agent. The agent and installer source are supplied.
