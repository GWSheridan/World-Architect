# Installation

Use Minecraft Java **26.3** and a 64-bit **Java 25** runtime. Pick one loader and put its Worlds Unwritten JAR in `mods/`. Install matching loader, mod and provider versions on clients and servers, then restart. Get the JARs from the [releases page](https://github.com/GWSheridan/Worlds-Unwritten/releases).

| Loader | Version | JAR |
| --- | --- | --- |
| NeoForge | 26.3.0.118 | `Worlds-Unwritten-2.3.0-mc26.3-neoforge.jar` |
| Forge | 66.1.9 | `Worlds-Unwritten-2.3.0-mc26.3-forge.jar` |
| Fabric | Loader 0.20.1 with Fabric API 0.167.0+26.3 | `Worlds-Unwritten-2.3.0-mc26.3-fabric.jar` |

In **Create New World → World**, choose **Worlds Unwritten · Dimensions** and open **Customize**. [Getting Started](Getting-Started.md) walks through a first world.

| Download | Contents |
| --- | --- |
| `Worlds-Unwritten-2.3.0-release.zip` | Three loader JARs, buildable source, guides, examples and optional test kit |
| `Worlds-Unwritten-2.3.0-source.zip` | Gradle project, tests, docs and example sources |
| `Worlds-Unwritten-2.3.0-test-kit.zip` | Java-only validation for exported modpack projects |
| `SHA256SUMS-2.3.0.txt` | SHA-256 checksums for the downloads |

Unchanged examples keep their original bundle versions. They are included in the complete ZIP and remain available with earlier releases. See [Example Presets](Example-Presets.md) for the full collection.

Keep preset bundles zipped. They belong in `config/worldsunwritten/bundles/`, not `mods/`. See [Locks and Server Enforcement](Locks-and-Server-Enforcement.md).

On Windows, compare `Get-FileHash <filename> -Algorithm SHA256` with the checksum file. On Linux, run `sha256sum -c SHA256SUMS-2.3.0.txt` in the download directory.

For an existing world, follow [Backups and Upgrades](Backups-and-Upgrades.md). Keep your existing loader for upgrades and test the update on a copy first.

## Compatibility

Keep every mod and datapack that supplies a saved dimension, block, biome, feature or structure. Missing or incompatible providers can stop a world load. Use a client and server on the same loader. Moving a save between loaders requires separate validation of its mod dependencies and saved data.

Support for an extension API does not guarantee compatibility with every modpack. Validate a new pack in a disposable world before deployment. Existing worlds retain their saved generation and archived templates; see [Backups and Upgrades](Backups-and-Upgrades.md) before updating.
