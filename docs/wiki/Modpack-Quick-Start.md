# Modpack quick start

1. Create a separate authoring instance with the release JAR and every required provider mod.
2. Set `{"format":1,"developer_mode":true}` in `config/worldarchitect/developer.json` and restart.
3. Configure dimensions and open Preset Studio. Choose a unique preset ID, title, author and release.
4. Set defaults, add templates, choose locks, and review dependencies.
5. Save a version and export its ZIP. Keep the source revision for future releases.
6. Install the ZIP in a clean client's and server's `config/worldarchitect/bundles/`. Select the same filename in `developer.json`. Normal installed instances do not need developer mode.
7. Create fresh worlds. Check allowed edits, rejected locked edits, safe spawning and persistence after a restart.
8. Distribute the preset with matching dependencies and installation instructions. Bundle revisions are independent of the mod version.

Start with the supplied [partial and full examples](Example-Presets.md). Read [Preset Studio](Preset-Studio.md), [Preset Reference](Preset-Reference.md) and [Locks and Server Enforcement](Locks-and-Server-Enforcement.md) before changing a production pack.

Existing worlds keep their original embedded templates, rule order, dependency versions and bundle archive. New installed defaults apply to future worlds.
