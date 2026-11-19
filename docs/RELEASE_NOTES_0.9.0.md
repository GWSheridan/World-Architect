# World Architect 0.9.0

This release targets Minecraft Java Edition 26.3 and was tested with NeoForge 26.3.0.103 and Java 25. Use the same World Architect version on the server and clients in a multiplayer instance.

## Package contents

| Path | Use |
| --- | --- |
| `mods/worldarchitect-0.9.0.jar` | Place this JAR in the NeoForge instance's `mods` folder. |
| `source/worldarchitect-0.9.0-source.zip` | Extract to build or inspect the source. It includes the Gradle wrapper, tests, documentation, and examples. |
| `examples/pure-sand/` | Server properties for the built-in Pure Sand preset. |
| `examples/universal-dimensions/` | Datapack with independent Overworld, Nether, and End profiles. |
| `examples/features-and-structures/` | Datapack with feature filters, fluids, and a desert pyramid in the End. |
| `examples/skyblock/` | Datapack with a pure-void world and a multiplayer Skyblock preset. |
| `examples/locked-skyblock/` | Authoring draft with a native starter structure. |
| `examples/bundles/locked-skyblock.zip` | Exported full-lock bundle ready to install using the bundle guide. |

Copy an example datapack directory into the new world's `datapacks` directory. See [Void and Skyblock](SKYBLOCK.md), [Features and Structures](FEATURES.md), and [Modpack Developer Tools](MODPACKS.md) for setup and preset IDs.

Build from the extracted source with `gradlew.bat build` on Windows or `bash ./gradlew build` on Linux and macOS. A first build needs network access to resolve build dependencies and prepare Minecraft artifacts. The `packageRelease` task builds the distribution and source archives. Set `-PreleaseLockedBundle=path/to/locked-skyblock.zip` to include an exported bundle in a newly assembled distribution.

## Save compatibility

Back up the complete world folder before upgrading a server. Include the world's generation snapshot, bundle archives, templates, and island ledger. Version 0.9.0 keeps the previous snapshot and generation codec formats. It records a flag in `level.dat` during ordinary saves to identify managed worlds when both generation files are missing. An older unmarked world needs its original generation files or another saved record of its World Architect configuration for the server to identify it as protected.

Keep the exact original bundle available for the first upgraded launch of a locked 0.6.0 world. Unlocked 0.6.0 bundle worlds require that release and their original ZIP. Read the [bundle upgrade instructions](MODPACKS.md#install-a-bundle).

## Release verification

| Area | Result |
| --- | --- |
| Build | 11 unit tests passed. The extracted source rebuilt a byte-identical JAR with cached Minecraft build artifacts. |
| Compatibility | Dedicated servers reopened legacy sand, a world with a third-party terrain block, a save with both original and adapted modded dimensions, and an archived locked Skyblock bundle. A changed native resource, a missing third-party block, and a missing archive each stopped startup. |
| Generation and gameplay | Audited 458,752 positions across the feature world and 458,752 positions across the three pure-void dimensions. The feature audit confirmed biome filters, cave generation, a Nether lava layer, and the End pyramid. A saved Structure Library template retained its transformed block states and sign metadata. |
| Multiplayer | Two clients received different islands and loaded fresh Overworld and Nether chunks requested in the same tick. Each resumed at their saved position after restart and respawned on their original island. The server checked permissions at the command entry point, refused a third new player at island capacity, and enforced the freshly exported full lock. |
| Save protection | Existing and freshly explored sand chunks matched the saved rules after a default preset change. Generation settings, snapshots, and sampled allocator and archive files kept their hashes. A copy missing both generation files stopped before fallback. |
| Integrated client | Validated full-lock world creation at GUI scales 1 and 2. A world missing its bundle archive displayed a recovery dialog and released the save lock. A valid archived save opened at its original island marker. |

### Dedicated server performance

Each profile ran on the same local Windows host with four active JVM processors and a maximum Java heap of 1,300 MiB. After a 16-chunk warmup, two separate 36-chunk batches were timed until all requested chunks were ready. The working set is the highest sampled server JVM value during either batch. Timings include tick scheduling and neighboring chunk work. [Method](TESTING.md#performance-comparison)

| Overworld profile | Seconds per 36-chunk batch | Peak sampled working set |
| --- | ---: | ---: |
| Original Generation | 3.51–3.68 | 886 MiB |
| Pure Void | 0.67–1.78 | 687 MiB |
| Pure Sand | 1.10–1.96 | 708 MiB |
| Layered | 1.08–2.16 | 733 MiB |
| Dense pyramids, caves, ores, and decorations | 1.30–1.31 | 735 MiB |

## Feature coverage

The release includes the built-in terrain and Skyblock generators, per-dimension controls, native structure templates, and a dimension adapter API. Native cross-dimension generation supports desert pyramids, jungle temples, and swamp huts. Broader schematic support, general-purpose generation rules, additional environment controls, and expanded structure adapters remain in the [development specification](SPECIFICATION.md).
