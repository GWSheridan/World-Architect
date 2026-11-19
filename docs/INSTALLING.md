# Installing World Architect 1.0

World Architect 1.0.0 targets **Minecraft Java 26.3** and was built for **NeoForge 26.3.0.103** with **Java 25**. Install the same mod/provider versions on clients and the dedicated server. Fabric, Forge, other Minecraft versions and arbitrary third-party world generators are not certified by this release.

## New client or server

1. Create a Minecraft 26.3 NeoForge instance and install `worldarchitect-1.0.0.jar` in `mods/`.
2. On a client, choose **Create New World → World → World Architect · Dimensions → Customize**. Configure the dimensions, then use **Done** to apply the draft. **Cancel** discards unsaved changes.
3. On a dedicated server, use `level-type=worldarchitect\:single_block` or `worldarchitect\:layered` for the built-in terrain. For another preset, install its datapack before first generation and use the preset ID from the [example index](../examples/README.md).
4. For a locked modpack, install one tested bundle ZIP in `config/worldarchitect/bundles/` and select its filename in `config/worldarchitect/developer.json`. The bundle guide below describes its defaults and allowed edits.

Install only the production JAR for normal play. The API fixture and acceptance probes are diagnostic mods, not gameplay dependencies. Server operators should set normal authentication, access and network settings for their deployment; the local acceptance harness uses loopback test instances.

## Upgrade an existing world

Stop the server and copy the **complete world directory** before changing mods. Replace the World Architect JAR while keeping compatible Minecraft, NeoForge, provider mods and datapacks. Reopen the copied world first. Existing saves retain their original generation profiles; editing a starting preset changes future worlds.

Keep these files with the world:

| File or directory | Purpose |
| --- | --- |
| `level.dat` and its backup | Native world metadata and the protected-world marker |
| `data/minecraft/world_gen_settings.dat` | Saved seed and dimension generation settings |
| `data/worldarchitect-generation-v1.dat` | Immutable generation snapshot and dependency pins |
| `data/worldarchitect/generation/` | Archived original bundle and its embedded resources |
| `data/worldarchitect-islands-v1.dat` | Personal/team island assignments |
| `data/worldarchitect-administration-v1.dat` | Spawn, protection and environment overrides, when configured |
| The world's generated structures/library files | Captured templates, rules and markers |

Missing generation files, changed pinned resources, unsupported data versions or missing/mismatched adapters stop loading. Restore the matching files or compatible provider version; do not delete or edit the snapshot to force a load. Keep the exact original bundle available for the first upgrade of a 0.6.0 locked world, as described in [Modpack Developer Tools](MODPACKS.md).

Supported acceptance paths include the earlier 0.9.0, 0.17.0 and 0.18.0 fixtures on Minecraft 26.3. This does not promise an automatic upgrade from another Minecraft data format. Native Original Generation retains Minecraft's run-to-run decoration/ticking variability; exact managed-generation comparisons and the native control are documented in [Milestone 19](MILESTONE_19_VERIFICATION.md).

## Build and verify an artifact

Use JDK 25, then run `gradlew.bat assembleRelease` (Windows) or `bash ./gradlew assembleRelease`. First-time dependency setup requires network access. ModDevGradle's asset downloader also needs JDK 21; point it at an installed runtime with `-Porg.gradle.java.installations.paths=<JDK21 path>` if needed.

The build writes the production JAR, source ZIP, complete release ZIP, two example bundle ZIPs and `SHA256SUMS-1.0.0.txt` to `build/release/`. On Windows compare `Get-FileHash <artifact> -Algorithm SHA256` with the matching checksum line. On Linux use `sha256sum -c SHA256SUMS-1.0.0.txt` from the artifact directory.

See [release notes](RELEASE_NOTES.md), [testing](TESTING.md), [modpack installation](MODPACKS.md), [Skyblock](SKYBLOCK.md) and [extension API 1](EXTENSION_API.md).
