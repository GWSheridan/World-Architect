# Dedicated server setup

Install the matching NeoForge server and put `worldarchitect-1.1.0.jar` and required provider mods in `mods/`. Use Java 25 and matching versions on clients. Keep the server's usual authentication and access settings for public play.

For a new built-in world, set these before first launch:

```properties
level-type=worldarchitect\:single_block
level-seed=194723
generate-structures=true
```

Use `worldarchitect\:layered` for layered terrain. For a datapack preset, install the pack in the new world's `datapacks/` folder and set its registry ID as `level-type`. Add its file pack ID to `initial-enabled-packs`; each [example](Example-Presets.md) lists its required values.

For a bundle, follow [Locks and Server Enforcement](Locks-and-Server-Enforcement.md). The manifest supplies omitted preset, seed and structure settings. Conflicting locked values stop creation. Permitted dimension edits go in `config/worldarchitect/world-selection.json` before generation.

Changing server properties or the installed starting preset does not rewrite an existing world's generation. Test updates on a full copy, keeping its archived bundle and provider dependencies. Read [Backups and Upgrades](Backups-and-Upgrades.md).
