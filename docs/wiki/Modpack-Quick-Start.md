# Modpack quick start

1. Create a separate authoring instance with Worlds Unwritten and every required provider mod.
2. Choose **Worlds Unwritten · Dimensions → Customize → Builder** when creating a world. In **Review / export**, choose **Authoring setup** and restart the instance. The interrupted project remains available through draft recovery. On a dedicated authoring server, its console can run `worldsunwritten modpack authoring_enable` before restarting.
3. In **Project → Choose starter**, select Skyblock, void, mixed dimensions, or one of the six [cell-world recipes](Cell-Worlds.md), including Hex Continents and Expanding One Chunk. Set a unique project name, preset ID, title, author and release in **Project details**.
4. Use **Dimensions** for terrain and features. Use **Rehearse campaign** to try a progression checkpoint in a fresh save. In **Islands**, open Island Workshop to compare custom islands, inspect their supplies and test their spawn.
5. Set new-world spawn, portal and protection defaults in **Server policies**. Use **Locks** to select generation permissions and the independent runtime permissions.
6. Under **Project → Loader targets**, keep the current loader or choose other targets. Enter the version and any extra required or optional mod IDs for each selected loader.
7. In **Review / export**, inspect validation and dependencies, save a version, then export a client/server installation for your chosen loader. To ship more loaders, repeat the export for each one. Both sides receive the same preset archive with developer mode disabled. Keep each saved revision for future releases.
8. Install matching mods in clean client and server instances. Copy the corresponding exported `config` folder into each. The exported `server.properties` is a new-server example. Create fresh worlds and check spawning, allowed edits, rejected edits and persistence after a restart.
9. Distribute the configuration, required-mod list and installation instructions. Bundle revisions are independent of the mod version. Use a new release when changing a saved revision.

![Modpack Builder project screen](images/modpack-builder.png)

## Test an exported project

The optional test kit runs with Java 25, the server runtime for your selected loader and version, the exported project and any required provider JARs. It uses the target saved in the export. For Fabric, point the kit at the dedicated launcher JAR and include Fabric API with the providers. Extract the complete ZIP and run `run.cmd --help` or `sh run.sh --help`.

Choose a new output folder and accept the Minecraft EULA using the documented flag. The kit copies the selected runtime libraries into that folder and checks a disposable server's startup, required mods, islands, markers, starter contents, configured runtime locks and restart snapshots. Cell worlds also check reserved-cell activation and void gaps. Read `results.json` for the loader, test stages and any failed check. Follow with multiplayer and progression playtests before distributing the pack.

Start with the supplied [partial and full examples](Example-Presets.md). Read [Preset Studio](Preset-Studio.md), [Preset Reference](Preset-Reference.md) and [Locks and Server Enforcement](Locks-and-Server-Enforcement.md) before changing a production pack.

Existing worlds keep their original embedded templates, rule order, dependency versions and bundle archive. New installed defaults apply to future worlds.
