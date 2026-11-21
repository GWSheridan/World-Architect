# Modpack Builder examples

These four self-contained presets use the rich island generator and format-3 new-world policy. Install matching World Architect 1.8.0 and NeoForge 26.3.0.103 on clients and servers. Each preset requires `minecraft` and `worldarchitect`.

| Archive | New-world layout | Lock |
| --- | --- | --- |
| `modpack-classic-1.8.0.zip` | One shared island with a chest, saplings, ice, food, a lava bucket and a contained water source | Full |
| `modpack-choice-1.8.0.zip` | 64 personal slots; players choose a pre-generated classic or custom orchard island | Full |
| `modpack-teams-1.8.0.zip` | 64 slots; first successful scoreboard-team choice reserves its shared island | Full |
| `modpack-mixed-1.8.0.zip` | Personal Overworld islands, a shared Nether island and the native End | Partial; protection settings remain editable |

Place the selected archive from `bundles/` in `config/worldarchitect/bundles/` and select its filename in `config/worldarchitect/developer.json` with developer mode disabled. Each `*-source` folder contains the corresponding manifest, preset and embedded resources. Keep generation properties omitted in a clean server's `server.properties` so the bundle supplies its seed and preset defaults.

Create scoreboard teams before players join the team example. A player without a team receives an individual assignment; joining a team later retains that reserved personal slot. Choice layouts fix every slot's type before generation and show the remaining capacity for each type. Starter inventories do not refill.

Use the [guided authoring workflow](../../docs/wiki/Modpack-Quick-Start.md) to create and export your own version. Existing worlds retain their archived templates, assignments, settings and generation rules when you install a revised bundle.
