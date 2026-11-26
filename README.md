# Worlds Unwritten

Design each Minecraft dimension before creating a world. Choose terrain and layers, control resources and structures, build Skyblock islands, or package the setup as a locked modpack preset.

**Minecraft Java 26.3 · NeoForge, Forge and Fabric · Java 25**
Created and maintained by **GWSHERIDAN**.

Licensed under the [MIT License](LICENSE).

See the [creator tools guide](docs/wiki/Creator-Tools.md) for the current authoring workflow.

Create hex and square cell worlds, advancement-based chunk expansion, saved player and team territories, and eight cell-world starters. Build and export them through [Cell World Studio](docs/wiki/Cell-Worlds.md). Find downloads on the [releases page](https://github.com/GWSheridan/Worlds-Unwritten/releases) or read the [changelog](CHANGELOG.md).


## Get started

Put the Worlds Unwritten JAR for your loader in `mods/`. Use the same Minecraft, loader, mod and provider versions on clients and servers. Fabric also requires Fabric API. See [Installation](docs/wiki/Installation.md) for compatible versions and filenames.

In **Create New World → World**, choose **Worlds Unwritten · Dimensions** and open **Customize**. Select a dimension, choose its terrain, review the preview, and press **Done**. Each dimension can have different settings.

For modpacks, open **Builder** to choose a starter, edit island types, set server policies and export an installation for each loader you target. [Island Studio and Island Workshop](docs/wiki/Void-and-Skyblock.md) support imported builds, side-by-side previews, custom spawn markers, starter supplies and playable previews.

- **World Inspector:** saved profiles, dependency pins and new-world preset comparisons.
- **Regional terrain:** rectangles, circles, polygon outlines and named compositions, with priority, biome choices and feathered heights.
- **Procedural pools:** seeded modules, named connections, branches and end caps.
- **Test workbench:** isolated preset scenarios and local reports from the source tools.
- **Draft history:** bounded Undo/Redo, duplication, ordering and explicit interrupted-draft recovery.
- **Shared regions and constrained pools:** reuse terrain masks in rules and require specific rooms within count/depth limits.
- **Batch comparisons:** fingerprinted version/provider/seed matrices with deadlines and local reports.
- **Single Block and Layered terrain:** block states, surface layers, landforms and biome-aware strata.
- **Features and structures:** biomes, ores, decoration, caves, fluids, native structures and modular assemblies.
- **Void and Skyblock:** empty terrain or template islands with persistent multiplayer assignments.
- **Cell worlds:** visual square and hex layouts, custom islands, resource cells and saved expansion.
- **Campaign authoring:** dependency graphs, checkpoint rehearsals, visit and discovery objectives, item turn-ins, cell costs and procedural ruins.
- **End progression:** a native central arena and dragon fight, with optional shared outer-cell expansion and safe gateway landings.
- **Assembly Studio:** visual modules, transformed markers, saved drafts and datapack or preset exports.
- **Rule Inspector:** condition maps, coordinate explanations and rule order without generating chunks.
- **Preset Studio:** versioned presets, embedded templates, validation and server-enforced locks.
- **Server administration:** confirmed island, team, spawn and protection changes, plus environment settings.

The [full wiki](https://github.com/GWSheridan/Worlds-Unwritten/wiki) has guides for players, server owners and modpack developers. An [offline Markdown copy](docs/wiki/Home.md) is included in the source and complete release ZIP.

## Downloads and guides

- [Latest release](https://github.com/GWSheridan/Worlds-Unwritten/releases/latest)
- [Installation](docs/wiki/Installation.md) and [first world](docs/wiki/Getting-Started.md)
- [Server setup](docs/wiki/Server-Setup.md)
- [Modpack authoring](docs/wiki/Modpack-Quick-Start.md) and [preset reference](docs/wiki/Preset-Reference.md)
- [Example presets](examples/README.md)
- [Changelog](CHANGELOG.md)

## Existing worlds

Version 2.0.0 introduces the Worlds Unwritten identity and namespace. Replace the previous mod JAR; install one version at a time. Existing saves and API 1 add-ons use the included compatibility bridge. Follow [Backups and Upgrades](docs/wiki/Backups-and-Upgrades.md) for legacy data paths and configuration import.

Worlds keep their original generation settings, templates and required dependencies. Changing an installed preset supplies defaults for future worlds. End progression is chosen before creation; older custom End saves keep their previous behaviour.

Back up the complete world folder before updating. Keep its snapshot, archived bundle, island assignments, administration data and captured structures. Read [Backups and Upgrades](docs/wiki/Backups-and-Upgrades.md) for file locations and recovery advice.

## Build from source

Use a 64-bit JDK 25:

```powershell
.\gradlew.bat :build :forge:build :fabric:build --configure-on-demand
.\gradlew.bat multiLoaderJars
.\gradlew.bat assembleRelease
```

On Linux or macOS, use `sh ./gradlew` instead. `common` holds the gameplay, world data and resources. Each loader has its own entry points, registration and networking. Run `:runClient` or `:runServer` for NeoForge, and prefix the task with `:forge:` or `:fabric:` for the other two loaders. The NeoForge asset downloader also uses JDK 21. Set `-Porg.gradle.java.installations.paths=<JDK21 path>` when needed.

`assembleRelease` writes the three playable JARs, the source and complete ZIPs, the optional pack test kit and checksums to `build/release/`. See the [validation runners](tools/validation/README.md) and [Public API](docs/wiki/Public-API.md) for development instructions.
