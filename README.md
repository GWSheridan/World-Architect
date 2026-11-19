# World Architect

World Architect is a NeoForge mod for **Minecraft Java Edition 26.3**. Configure each dimension independently before creating a world. Pure Sand creates seeded hills and ridges from `minecraft:sand`. Layered terrain places surface-relative materials over a base block. Void generates empty chunks, and Skyblock adds starting islands from a built-in or imported structure.

**Author, original creator, and developer** — GWSHERIDAN

## Create a World Architect world

1. Install NeoForge **26.3.0.103** for Minecraft **26.3** and put the World Architect JAR in the instance's `mods` directory.
2. In **Create New World → World**, choose **World Architect · Dimensions** and open **Customize**. The world types **World Architect** and **World Architect · Layered** also open the dimension manager with their respective preset terrain.
3. Select a dimension or search by registry ID. Choose **Original Generation**, **Single Block**, **Layered**, or **Void / Skyblock**. Specialized generators need an adapter to offer replacements.
4. For solid terrain, edit the material, layers, and terrain shape. Open **Features** to add generation rules. For Skyblock, select a template and enter its local spawn marker, bottom Y, spacing, and Overworld allocation capacity. Follow the [Void and Skyblock guide](docs/SKYBLOCK.md) to import a structure or configure a server preset.
5. Choose **Done** in the dimension manager to apply the whole draft. **Cancel** discards it. The regular Minecraft seed field controls terrain shape.

Open **Preview map** on a managed dimension to pan and zoom over base terrain height, biomes, and reserved island spawn markers. The map uses the draft settings and seed. In the terrain editors, **Browse blocks** or **Pick…** opens a searchable registry of vanilla and modded blocks with state controls. Favourites and recently used blocks are stored in the client instance’s `config/worldarchitect/block-picker.json`.

Open **Preset Studio** in the dimension manager to save versioned presets, edit locks, inspect dependencies, and import/export bundles with embedded templates. See [Preset Studio](docs/PRESET_STUDIO.md) and the [partial and full examples](examples/preset-studio).

Use **World Generation Rules** for ordered material, ore, decoration, modular template and spawn actions with dimension, biome, height, region, distance, seed and dependency conditions. See the [rule engine guide](docs/WORLDGEN_RULES.md) and [floating Sky City example](examples/worldgen-rules).

Choose a landform in **Terrain Shape** to opt into version 2. Version 2 also enables **Strata Rules** in the Layered editor. See the [advanced terrain guide](docs/ADVANCED_TERRAIN.md) for terrain settings, biome filters, and the dedicated-server example.

Custom terrain in a dimension with a dragon fight disables the fight and the exit portal it creates. Keep **Original Generation** in the End to retain vanilla progression. Dimension types still govern build limits, coordinate scaling, and environmental rules.

## Build and run

Use a 64-bit JDK 25. The Gradle 9.2.1 wrapper and NeoForge version are pinned in the project. ModDevGradle uses JDK 21 for its asset downloader and can provision it through Foojay. On a machine without download access, point Gradle at an installed JDK 21 with `-Porg.gradle.java.installations.paths=<path>`.

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

The mod JAR is written to `build/libs/worldarchitect-1.0.0.jar`. On Linux and macOS, invoke the wrapper with `bash ./gradlew`. Run `gradlew.bat assembleRelease` to build the JAR, release/source ZIPs, example bundles and SHA-256 checksums in `build/release`. See [installation and upgrades](docs/INSTALLING.md) and the [example index](examples/README.md).

For a new dedicated server world, set `level-type=worldarchitect\:single_block` for Pure Sand or `level-type=worldarchitect\:layered` for a sand surface over sandstone and stone. The [universal dimensions datapack](examples/universal-dimensions) demonstrates a single preset with separate Overworld, Nether, and End profiles. See the [server example](examples/pure-sand/server.properties) and the [verification procedure](docs/TESTING.md).

## Saved worlds

Customized worlds store the selected generator profiles and seed in the save's world-generation settings. World Architect writes a separate, immutable snapshot before the first chunks generate. New snapshots also record the effective dimension definitions, managed biomes, feature flags, and generation resources used for Original Generation. On reopen, the server checks those dependencies along with saved build ranges and required adapter versions. Changes to the starting preset apply to future worlds.

Skyblock saves embed their starting templates. Locked bundle worlds also keep the original ZIP in `data/worldarchitect/generation/`, preserving its native structures and lock policy. Personal Overworld islands keep UUID assignments in `data/worldarchitect-islands-v1.dat`. Back up the complete world folder with these files.

The save checks cover Minecraft 26.3 and require World Architect to remain installed. Startup stops if a required dependency is missing or has changed, or the saved generation settings require a different Minecraft data version. World Architect 0.9.0 marks protected saves in `level.dat` during ordinary saves so it can recognise them if both generation files are lost. Existing snapshots retain the dependencies they recorded. When upgrading a locked world from 0.6.0, keep its exact original bundle installed for the first launch so World Architect can archive it in the save.

Toolchain references are the [official NeoForge 26.3 project template](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle), [NeoForge 26.3 migration primer](https://docs.neoforged.net/primer/docs/26.3/), and [Minecraft 26.3 release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3).

- [Development changelog](CHANGELOG.md)
- [Release contents, verification, and compatibility](docs/RELEASE_NOTES.md)
- [Features, structures, and cross-dimension placement](docs/FEATURES.md)
- [Dimension Environment Designer](docs/ENVIRONMENTS.md)
- [Dedicated-server multiplayer verification](docs/TESTING.md#dedicated-multiplayer)
- [Structure Library, template editor, and schematic import](docs/STRUCTURE_LIBRARY.md)
- [Modpack bundles, developer mode, and locks](docs/MODPACKS.md)
- [Modded dimension adapter API](docs/DIMENSION_ADAPTERS.md)
- [Public extension API 1 and external fixture](docs/EXTENSION_API.md)
- [Complete feature specification and milestone order](docs/SPECIFICATION.md)
