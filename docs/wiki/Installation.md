# Installation

Install NeoForge **26.3.0.103** for Minecraft Java **26.3** and use a 64-bit **Java 25** runtime. Download `worldarchitect-1.5.0.jar` from the official release and place it in the instance's `mods/` directory. Install matching mod and provider versions on clients and servers, then restart.

In **Create New World → World**, choose **World Architect · Dimensions** and open **Customize**. [Getting Started](Getting-Started.md) walks through a first world.

| Download | Contents |
| --- | --- |
| `worldarchitect-1.5.0.jar` | The mod for clients and servers |
| `worldarchitect-1.4.0-release.zip` | Mod, buildable source, wiki and examples |
| `worldarchitect-1.4.0-source.zip` | Gradle project, tests, docs and example sources |
| `studio-partial-1.4.0.zip` | Partially locked example preset |
| `studio-skyblock-1.4.0.zip` | Fully locked multiplayer Skyblock preset |
| `single-block-1.4.0.zip`, `layered-1.4.0.zip`, `skyblock-1.4.0.zip`, `custom-end-1.4.0.zip` | Unlocked creator starters |
| `assembly-demo-1.4.0.zip` | Unlocked two-module Assembly Studio example |
| `regional-demo-1.4.0.zip` | Unlocked regional terrain example |
| `pool-demo-1.4.0.zip` | Unlocked procedural assembly pool example |
| `shared-regions-1.4.0.zip` | Shared region conditions and strictly filtered writes |
| `constrained-pool-1.4.0.zip` | Required rooms and count/depth limits |
| `SHA256SUMS-1.4.0.txt` | SHA-256 checksums for all fourteen other downloads |

Keep preset bundles zipped. They belong in `config/worldarchitect/bundles/`, not `mods/`. See [Locks and Server Enforcement](Locks-and-Server-Enforcement.md).

On Windows, compare `Get-FileHash <filename> -Algorithm SHA256` with the checksum file. On Linux, run `sha256sum -c SHA256SUMS-1.4.0.txt` in the download directory.

For an existing world, follow [Backups and Upgrades](Backups-and-Upgrades.md). This build targets the versions listed above; Fabric, Forge and other Minecraft data versions need different builds.
