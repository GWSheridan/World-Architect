# Installation

Install NeoForge **26.3.0.103** for Minecraft Java **26.3** and use a 64-bit **Java 25** runtime. Download `worldarchitect-1.7.0.jar` from the [1.7.0 release](https://github.com/GWSheridan/World-Architect/releases/tag/v1.7.0) and place it in the instance's `mods/` directory. Install matching mod and provider versions on clients and servers, then restart.

In **Create New World → World**, choose **World Architect · Dimensions** and open **Customize**. [Getting Started](Getting-Started.md) walks through a first world.

| Download | Contents |
| --- | --- |
| `worldarchitect-1.7.0.jar` | The mod for clients and servers |
| `worldarchitect-1.7.0-release.zip` | Mod, buildable source, wiki and examples |
| `worldarchitect-1.7.0-source.zip` | Gradle project, tests, docs and example sources |
| `studio-partial-1.7.0.zip` | Partially locked example preset |
| `studio-skyblock-1.7.0.zip` | Fully locked multiplayer Skyblock preset |
| `single-block-1.7.0.zip`, `layered-1.7.0.zip`, `skyblock-1.7.0.zip`, `custom-end-1.7.0.zip` | Unlocked creator starters |
| `assembly-demo-1.7.0.zip` | Unlocked two-module Assembly Studio example |
| `regional-demo-1.7.0.zip` | Unlocked regional terrain example |
| `pool-demo-1.7.0.zip` | Unlocked procedural assembly pool example |
| `shared-regions-1.7.0.zip` | Shared region conditions and strictly filtered writes |
| `constrained-pool-1.7.0.zip` | Required rooms and count/depth limits |
| `polygon-regions-1.7.0.zip` | Convex and concave region outlines |
| `coastal-ruins-1.7.0.zip` | Surface, seabed and fixed-height structure anchors |
| `vertical-tower-1.7.0.zip` | Vertical pool connections and shaft clearance |
| `locked-archipelago-1.7.0.zip` | Fully locked islands, polygon highlands and native End progression |
| `composite-regions-1.7.0.zip` | Union, intersection and subtraction example |
| `foundation-platform-1.7.0.zip`, `themed-platform-1.7.0.zip` | Bounded supports, palettes and captured variants |
| `corridor-regions-1.7.0.zip` | Connected paths, biome choices and strict rule masks |
| `structure-districts-1.7.0.zip` | Weighted houses, exclusion groups and region limits |
| `reusable-skyblock-1.7.0.zip` | Partially locked base with 64 personal islands |
| `inherited-partial-1.7.0.zip`, `inherited-skyblock-1.7.0.zip` | Self-contained partial/full children with pinned parent policies |
| `SHA256SUMS-1.7.0.txt` | SHA-256 checksums for all twenty-six other downloads |

Keep preset bundles zipped. They belong in `config/worldarchitect/bundles/`, not `mods/`. See [Locks and Server Enforcement](Locks-and-Server-Enforcement.md).

On Windows, compare `Get-FileHash <filename> -Algorithm SHA256` with the checksum file. On Linux, run `sha256sum -c SHA256SUMS-1.7.0.txt` in the download directory.

For an existing world, follow [Backups and Upgrades](Backups-and-Upgrades.md). This build targets the versions listed above; Fabric, Forge and other Minecraft data versions need different builds.

## Compatibility

Use matching World Architect versions on clients and servers, together with the Minecraft, NeoForge and Java versions listed above. Keep every mod and datapack that supplies a saved dimension, block, biome, feature or structure. Missing or incompatible providers can stop a world load.

Support for an extension API does not guarantee compatibility with every modpack. Validate a new pack in a disposable world before deployment. Existing worlds retain their saved generation and archived templates; see [Backups and Upgrades](Backups-and-Upgrades.md) before updating.
