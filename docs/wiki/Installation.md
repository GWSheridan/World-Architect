# Installation

Install NeoForge **26.3.0.103** for Minecraft Java **26.3** and use a 64-bit **Java 25** runtime. Use `Worlds-Unwritten-2.0.0.jar` from the packaged build or [releases page](https://github.com/GWSheridan/Worlds-Unwritten/releases) and place it in the instance's `mods/` directory. Install matching mod and provider versions on clients and servers, then restart.

In **Create New World → World**, choose **Worlds Unwritten · Dimensions** and open **Customize**. [Getting Started](Getting-Started.md) walks through a first world.

| Download | Contents |
| --- | --- |
| `Worlds-Unwritten-2.0.0.jar` | The mod for clients and servers |
| `Worlds-Unwritten-2.0.0-release.zip` | Mod, buildable source, wiki, examples and optional test kit |
| `Worlds-Unwritten-2.0.0-source.zip` | Gradle project, tests, docs and example sources |
| `Worlds-Unwritten-2.0.0-test-kit.zip` | Java-only validation for exported modpack projects |
| `hex-continents-2.0.0.zip`, `hex-skyblock-2.0.0.zip`, `expanding-one-chunk-2.0.0.zip`, `resource-archipelago-2.0.0.zip`, `biome-mosaic-2.0.0.zip`, `underground-start-2.0.0.zip` | Six editable cell-world examples |
| `SHA256SUMS-2.0.0.txt` | SHA-256 checksums for the ten other downloads |

Unchanged examples keep their original bundle versions. They are included in the complete ZIP and remain available with earlier releases. See [Example Presets](Example-Presets.md) for the full collection.

Keep preset bundles zipped. They belong in `config/worldsunwritten/bundles/`, not `mods/`. See [Locks and Server Enforcement](Locks-and-Server-Enforcement.md).

On Windows, compare `Get-FileHash <filename> -Algorithm SHA256` with the checksum file. On Linux, run `sha256sum -c SHA256SUMS-2.0.0.txt` in the download directory.

For an existing world, follow [Backups and Upgrades](Backups-and-Upgrades.md). This build targets the versions listed above; Fabric, Forge and other Minecraft data versions need different builds.

## Compatibility

Use matching Worlds Unwritten versions on clients and servers, together with the Minecraft, NeoForge and Java versions listed above. Keep every mod and datapack that supplies a saved dimension, block, biome, feature or structure. Missing or incompatible providers can stop a world load.

Support for an extension API does not guarantee compatibility with every modpack. Validate a new pack in a disposable world before deployment. Existing worlds retain their saved generation and archived templates; see [Backups and Upgrades](Backups-and-Upgrades.md) before updating.
