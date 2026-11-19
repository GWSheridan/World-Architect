# Commands and permissions

Commands are checked by the server. Preset locks can restrict operations that an administrator would otherwise be allowed to perform. Use command suggestions for the full argument tree and replace example IDs with installed resources.

| Command | Purpose | Access |
| --- | --- | --- |
| `/wa_structure` | Library, capture, import, placement and markers | Permission level 2; bundle must allow the library |
| `/wa_island` | Personal assignment; admin subcommands for reassignment, teams and protection | Self inspection; administration needs permission level 3 |
| `/wa_spawn` | Spawn policies | Permission level 3 |
| `/wa_rules` | Inspect and explain saved generation rules | Player inspection |
| `/wa_environment <dimension>` | Inspect settings and capabilities | Player inspection |
| `/wa_environment <dimension> edit` | Environment designer | Permission level 3 and editable dimension |
| `/wa_preset` | Preset authoring and export | Permission level 3; authoring also needs developer mode |
| `/wa_modpack` | Bundle information and export | Export needs administrator permission and developer mode |

See [Multiplayer and Islands](Multiplayer-and-Islands.md) for island and spawn administration, [Structure Library](Structure-Library.md) for capture and import, [Environment Controls](Environment-Controls.md) for live settings, and [World Generation Rules](World-Generation-Rules.md) for inspection commands.

Generation settings remain fixed for an existing save. Starting-preset edits are applied when creating a new world.
