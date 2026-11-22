# Commands and permissions

Commands are checked by the server. Preset locks can restrict operations that an administrator would otherwise be allowed to perform. Use command suggestions for the full argument tree and replace example IDs with installed resources.

Cell-world players use `/worldsunwritten cells`, `/worldsunwritten cells panel`, `/worldsunwritten cells home` and `/worldsunwritten cells choose <q> <r>`. Unlocking, retrying conflicts, quest signals and reassignment require permission level 3. See the [cell command table](Cell-Worlds.md#player-and-administrator-commands) for arguments and ownership rules.

| Command | Purpose | Access |
| --- | --- | --- |
| `/worldsunwritten inspect [installed] [report]` | Saved generation and preset comparison | Permission level 3 |
| `/worldsunwritten workbench export <name> <release>` | Detached bundle and test scenario | Permission level 4 and developer mode |
| `/worldsunwritten workbench studio <name> <release>` | Edit and export a scenario bound to one saved preset | Permission level 4 and developer mode |
| `/worldsunwritten diagnose report` | Local versions, adapter capabilities and snapshot hashes | Permission level 3 |
| `/wa_admin` or `/worldsunwritten island panel` | Personal island view; confirmed administration controls | Self view; changes require permission level 3 |
| `/worldsunwritten assembly` | Visual assembly drafts and datapack exports | Permission level 2 and library access; preset export also needs level 3 and developer mode |
| `/worldsunwritten structure` | Library, capture, import, placement and markers | Permission level 2; bundle must allow the library |
| `/worldsunwritten island` | Personal assignment; admin subcommands for reassignment, teams and protection | Self inspection; administration needs permission level 3 |
| `/worldsunwritten spawn` | Spawn policies | Permission level 3 |
| `/worldsunwritten rules` | Inspect and explain saved generation rules | Player inspection |
| `/worldsunwritten environment <dimension>` | Inspect settings and capabilities | Player inspection |
| `/worldsunwritten environment <dimension> edit` | Environment designer | Permission level 3 and editable dimension |
| `/worldsunwritten preset` | Preset authoring and export | Permission level 3; authoring also needs developer mode |
| `/worldsunwritten modpack` | Bundle information and export | Export needs administrator permission and developer mode |
| `/worldsunwritten island choose` | Open the saved world's starting-island choices | Unassigned player or team member |
| `/worldsunwritten island studio`, `pos1`, `pos2`, `capture_selected <name> <release> <contents>` | Capture and save an island revision | Permission level 3 and developer mode |
| `worldsunwritten modpack authoring_enable` | Enable authoring after a restart | Dedicated-server console only |

See [Multiplayer and Islands](Multiplayer-and-Islands.md) for island and spawn administration, [Structure Library](Structure-Library.md) for capture and import, [Environment Controls](Environment-Controls.md) for live settings, and [World Generation Rules](World-Generation-Rules.md) for inspection commands.

Generation settings remain fixed for an existing save. Starting-preset edits are applied when creating a new world.

In 1.1, `/worldsunwritten structure stage`, `view3d`, `confirm` and `undo` share the library permission and bundle policy. See [Creator Tools](Creator-Tools.md) for guarded undo and preview lifetime.

Assembly edits use a server session token and serial. Administration changes require a current view token and one-use confirmation. Direct command requests receive the same permission and lock checks as the GUI. See [Assembly Studio](Assembly-Studio.md) and [Administration Panel](Administration-Panel.md).

Workbench scenario exports recheck developer mode, current level-4 permission, the owner’s session token and the saved bundle fingerprint. Losing permission while the editor is open prevents further exports. Draft history retains the same command/session checks as ordinary assembly edits. See [Draft History](Draft-History.md) and [Test Workbench](Test-Workbench.md).
