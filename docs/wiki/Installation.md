# Installation

Install NeoForge **26.3.0.103** for Minecraft Java **26.3** and use a 64-bit **Java 25** runtime. Download `worldarchitect-1.8.0.jar` from the [1.8.0 release](https://github.com/GWSheridan/World-Architect/releases/tag/v1.8.0) and place it in the instance's `mods/` directory. Install matching mod and provider versions on clients and servers, then restart.

In **Create New World → World**, choose **World Architect · Dimensions** and open **Customize**. [Getting Started](Getting-Started.md) walks through a first world.

| Download | Contents |
| --- | --- |
| `worldarchitect-1.8.0.jar` | The mod for clients and servers |
| `worldarchitect-1.8.0-release.zip` | Mod, buildable source, wiki, examples and optional test kit |
| `worldarchitect-1.8.0-source.zip` | Gradle project, tests, docs and example sources |
| `worldarchitect-1.8.0-test-kit.zip` | Java-only validation for exported modpack projects |
| `modpack-classic-1.8.0.zip`, `modpack-choice-1.8.0.zip`, `modpack-teams-1.8.0.zip`, `modpack-mixed-1.8.0.zip` | Shared, personal, team and mixed-dimension modpack examples |
| `SHA256SUMS-1.8.0.txt` | SHA-256 checksums for the eight other downloads |

Unchanged examples keep their original bundle versions. They are included in the complete ZIP and remain available with earlier releases. See [Example Presets](Example-Presets.md) for the full collection.

Keep preset bundles zipped. They belong in `config/worldarchitect/bundles/`, not `mods/`. See [Locks and Server Enforcement](Locks-and-Server-Enforcement.md).

On Windows, compare `Get-FileHash <filename> -Algorithm SHA256` with the checksum file. On Linux, run `sha256sum -c SHA256SUMS-1.8.0.txt` in the download directory.

For an existing world, follow [Backups and Upgrades](Backups-and-Upgrades.md). This build targets the versions listed above; Fabric, Forge and other Minecraft data versions need different builds.

## Compatibility

Use matching World Architect versions on clients and servers, together with the Minecraft, NeoForge and Java versions listed above. Keep every mod and datapack that supplies a saved dimension, block, biome, feature or structure. Missing or incompatible providers can stop a world load.

Support for an extension API does not guarantee compatibility with every modpack. Validate a new pack in a disposable world before deployment. Existing worlds retain their saved generation and archived templates; see [Backups and Upgrades](Backups-and-Upgrades.md) before updating.
