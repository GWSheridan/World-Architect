# Dedicated server setup

Install the NeoForge, Forge or Fabric server for Minecraft 26.3. Put the matching Worlds Unwritten JAR and provider mods in `mods/`. Fabric also needs Fabric API. Use Java 25 and match the loader and mod versions on clients. Keep the server’s usual authentication and access settings for public play. [Installation](Installation.md) lists loader versions and filenames.

For a new built-in world, set these before first launch:

```properties
level-type=worldsunwritten\:single_block
level-seed=194723
generate-structures=true
```

Use `worldsunwritten\:layered` for layered terrain. For a datapack preset, install the pack in the new world's `datapacks/` folder and set its registry ID as `level-type`. Add its file pack ID to `initial-enabled-packs`; each [example](Example-Presets.md) lists its required values.

For a bundle, follow [Locks and Server Enforcement](Locks-and-Server-Enforcement.md). The manifest supplies omitted preset, seed and structure settings. Conflicting locked values stop creation. Permitted dimension edits go in `config/worldsunwritten/world-selection.json` before generation.

Changing server properties or the installed starting preset does not rewrite an existing world's generation. Test updates on a full copy, keeping its archived bundle and provider dependencies. Read [Backups and Upgrades](Backups-and-Upgrades.md).
