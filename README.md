# World Architect

Design each Minecraft dimension before creating a world. Choose terrain and layers, control resources and structures, build Skyblock islands, or package the setup as a locked modpack preset.

**Minecraft Java 26.3 · NeoForge 26.3.0.103 · Java 25**
Created and maintained by **GWSHERIDAN**.

See the [creator tools guide](docs/wiki/Creator-Tools.md) for the current authoring workflow.

World Architect 1.8.0 adds a guided Modpack Builder, custom island editing, persistent island choices and matching client/server installation exports. Find downloads on the [releases page](https://github.com/GWSheridan/World-Architect/releases) or read the [changelog](CHANGELOG.md).


## Get started

Install `worldarchitect-1.8.0.jar` in your NeoForge instance's `mods/` folder. Use matching versions on clients and servers.

In **Create New World → World**, choose **World Architect · Dimensions** and open **Customize**. Select a dimension, choose its terrain, review the preview, and press **Done**. Each dimension can have different settings.

For modpacks, open **Builder** to choose a starter, edit island types, set server policies and export an installation. [Island Studio](docs/wiki/Void-and-Skyblock.md) supports captured or imported builds, native model previews, markers and starter containers.

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
- **End progression:** a native central arena and dragon fight surrounded by custom terrain.
- **Assembly Studio:** visual modules, transformed markers, saved drafts and datapack or preset exports.
- **Rule Inspector:** condition maps, coordinate explanations and rule order without generating chunks.
- **Preset Studio:** versioned presets, embedded templates, validation and server-enforced locks.
- **Server administration:** confirmed island, team, spawn and protection changes, plus environment settings.

The [full wiki](https://github.com/GWSheridan/World-Architect/wiki) has guides for players, server owners and modpack developers. An [offline Markdown copy](docs/wiki/Home.md) is included in the source and complete release ZIP.

## Downloads and guides

- [Latest release](https://github.com/GWSheridan/World-Architect/releases/latest)
- [Installation](docs/wiki/Installation.md) and [first world](docs/wiki/Getting-Started.md)
- [Server setup](docs/wiki/Server-Setup.md)
- [Modpack authoring](docs/wiki/Modpack-Quick-Start.md) and [preset reference](docs/wiki/Preset-Reference.md)
- [Example presets](examples/README.md)
- [Changelog](CHANGELOG.md)

## Existing worlds

Worlds keep their original generation settings, templates and required dependencies. Changing an installed preset supplies defaults for future worlds. End progression is chosen before creation; older custom End saves keep their previous behaviour.

Back up the complete world folder before updating. Keep its snapshot, archived bundle, island assignments, administration data and captured structures. Read [Backups and Upgrades](docs/wiki/Backups-and-Upgrades.md) for file locations and recovery advice.

## Build from source

Use a 64-bit JDK 25:

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat assembleRelease
```

On Linux or macOS, use `bash ./gradlew` instead. The build uses the pinned Gradle wrapper and NeoForge dependency. The asset downloader also needs JDK 21; select an installed runtime with `-Porg.gradle.java.installations.paths=<JDK21 path>` when needed.

`assembleRelease` writes the mod, source and complete ZIPs, example bundles and checksums to `build/release/`. See [validation runners](tools/validation/README.md) and [Public API](docs/wiki/Public-API.md) for development instructions.
