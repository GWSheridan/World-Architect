# Campaign examples

**One Chunk Expedition (2.3.0)** branches into stoneworking, timber delivery and discovery. Start in one chunk, then cross to the marked trailhead, visit the Nether and open the portal route to the native End. Editable source lives in `one-chunk-expedition-2.3.0-source/`.

These presets work with Worlds Unwritten **2.1.0** on NeoForge and **2.2.0** on all three supported loaders. Use Minecraft **26.3**, Java **25** and the same mod and loader versions on clients and servers. See [Installation](../../docs/wiki/Installation.md) for loader versions and dependencies.

| Preset | World | Locks |
| --- | --- | --- |
| [Hex Frontier](bundles/hex-frontier-2.1.0.zip) | Nineteen resource islands, reserved paths, procedural ruins, an End portal room and a shared native End | Fully locked, including the seed and runtime policies |
| [One Chunk to the End](bundles/one-chunk-to-the-end-2.1.0.zip) | A starting chunk, advancement rewards, a timber turn-in, underground diamonds and an End portal room | Generation is locked; the new-world seed and runtime protection settings are editable |

Both keep a normal Nether. Starter supplies include obsidian and flint and steel. The End portal frames are unfilled: players still gather Eyes of Ender, activate the portal, fight the dragon and use the native gateways. The shared End awards exploration credits on arrival and once for the verified dragon kill; repeated kills keep normal fight progression without paying that objective again.

## Install

1. Copy one ZIP into `config/worldsunwritten/bundles/` on the client and dedicated server. Keep the ZIP intact.
2. Select its filename in `config/worldsunwritten/developer.json`:

   ```json
   {"format":1,"bundle":"hex-frontier-2.1.0.zip","developer_mode":false}
   ```

3. Create a new world. The server enforces the selected preset and its locks. Never replace an existing world's saved generation snapshot or cell journal.
4. Use `/worldsunwritten cells panel` to see objectives, costs, item turn-ins and generation status. Safe travel home is available through `/worldsunwritten cells home`.

For One Chunk to the End, select `one-chunk-to-the-end-2.1.0.zip` instead. Its permitted seed change applies when creating a new world; an existing world's seed remains saved.

## Edit a campaign

The adjacent `*-2.1.0-source` folders contain the complete editable bundle resources. Import a copy into Preset Studio with developer mode enabled, give it your own preset ID and release, then adjust its locks before authoring. Builder also includes both campaign starters without the distribution locks.

Use World Layout Designer for painted assignments and groups, Progression Designer for dependencies and rewards, and Connections and Adventure Sites for ports and bounded module pools. The Hex Frontier ruin uses an original two-room pool embedded in the preset. Its decorative chest starts empty; starter supplies belong to the starting island.

Export a client/server installation and run the matching standalone test kit in a disposable instance. The kit checks cell activation, reserved gaps and connections, site markers and metadata, native End reservations, snapshots, starter consumption and restart persistence. See the [cell-world guide](../../docs/wiki/Cell-Worlds.md) and [modpack quick start](../../docs/wiki/Modpack-Quick-Start.md).
