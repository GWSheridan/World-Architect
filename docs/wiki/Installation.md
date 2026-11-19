# Installation

Install NeoForge **26.3.0.103** for Minecraft Java **26.3** and use a 64-bit **Java 25** runtime. Download `worldarchitect-1.0.0.jar` from the official release and place it in the instance's `mods/` directory. Install matching mod and provider versions on clients and servers, then restart.

In **Create New World → World**, choose **World Architect · Dimensions** and open **Customize**. [Getting Started](Getting-Started.md) walks through a first world.

| Download | Contents |
| --- | --- |
| `worldarchitect-1.0.0.jar` | The mod for clients and servers |
| `worldarchitect-1.0.0-release.zip` | Mod, buildable source, wiki and examples |
| `worldarchitect-1.0.0-source.zip` | Gradle project, tests, docs and example sources |
| `studio-partial-1.0.0.zip` | Partially locked example preset |
| `studio-skyblock-1.0.0.zip` | Fully locked multiplayer Skyblock preset |
| `SHA256SUMS-1.0.0.txt` | SHA-256 checksums for the five downloads above |

Keep preset bundles zipped. They belong in `config/worldarchitect/bundles/`, not `mods/`. See [Locks and Server Enforcement](Locks-and-Server-Enforcement.md).

On Windows, compare `Get-FileHash <filename> -Algorithm SHA256` with the checksum file. On Linux, run `sha256sum -c SHA256SUMS-1.0.0.txt` in the download directory.

For an existing world, follow [Backups and Upgrades](Backups-and-Upgrades.md). This build targets the versions listed above; Fabric, Forge and other Minecraft data versions need different builds.
