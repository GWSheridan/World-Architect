# World Architect 1.0.0

World Architect 1.0 combines dimension editing, custom terrain, structure tools and modpack presets for **Minecraft Java 26.3**, **NeoForge 26.3.0.103** and **Java 25**.

New custom End worlds can keep the native central arena, dragon fight, exit portal, gateways and crystal respawning. The surrounding End follows your terrain settings. Imported presets and existing saves retain their selected policy.

The release includes advanced terrain and layers, block-state search and previews, structure capture and schematic imports, cross-dimension structures, multiplayer islands, spawn and environment controls, generation rules and Preset Studio. Pack authors can bundle templates and apply partial or full locks enforced by clients and dedicated servers.

## Install

Put `worldarchitect-1.0.0.jar` in the instance's `mods/` directory. Use matching versions on clients and servers. The complete ZIP includes the mod, buildable source, wiki and examples. Keep Studio bundle ZIPs zipped in `config/worldarchitect/bundles/`. Verify downloads against `SHA256SUMS-1.0.0.txt`.

## Existing worlds

Back up the full world folder before updating. Keep the original snapshot, archived bundle, templates, island assignments and compatible providers. A new preset changes future worlds; it does not rewrite existing or unexplored terrain in older saves. This release does not convert between Minecraft data formats.

## Compatibility

The arena is available for new managed End profiles with the standard build range. Void and Skyblock authors enable it explicitly when they want the arena and gateway landing islands. Specialised modded generators and custom renderers may need integrations. Native feature adapters control their feature writes; fixed rule-engine actions provide stricter spatial filtering.

Read the [wiki](wiki/Home.md), [changelog](../CHANGELOG.md), [examples](../examples/README.md) and [testing guide](TESTING.md) for details.
