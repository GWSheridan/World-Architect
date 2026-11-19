# World Architect 1.1.0

World Architect 1.1 adds a starter gallery, a searchable preset library and visual tools for designing terrain and placing saved structures.

- Start with Single Block, Layered, Skyblock or a custom End that keeps the native dragon arena. Other dimension drafts stay independent.
- Search saved presets, load a specific revision, validate changes and review discard prompts before replacing a draft.
- Inspect height and biome maps, a rotatable surface view or a vertical cross-section. Pan, zoom and enter coordinates without generating chunks.
- Orbit a saved template, transform its markers, stage it at a destination and show a temporary world ghost. Confirmation rechecks the current world on the server.
- Undo retained manual placements when their blocks and block-entity data still match. History is bounded, shared across authors and cleared on restart.
- Write a local administrator diagnostic report containing versions, adapter capabilities and generation-file hashes. Reports exclude player identities, addresses, credentials, absolute paths and raw seeds.
- Run portable dedicated-server fixtures and pinned public-mod comparisons on Windows and Linux. Clean Linux checkouts now run the Unix Gradle wrapper correctly.

Requires **Minecraft Java 26.3, NeoForge 26.3.0.103 and Java 25**. Install matching 1.1 versions on clients and servers for placement networking. API 1 and the saved preset, template and generation formats remain compatible.

Existing worlds retain their saved generation settings, templates, archived presets, island assignments and End policies. Starters affect new worlds. Back up an existing world before updating and keep its required provider mods and archived bundles installed. See [Backups and Upgrades](https://github.com/GWSheridan/World-Architect/blob/v1.1.0/docs/wiki/Backups-and-Upgrades.md).

## Downloads

| File | Contents |
| --- | --- |
| `worldarchitect-1.1.0.jar` | Gameplay mod for clients and dedicated servers |
| `worldarchitect-1.1.0-release.zip` | Mod, buildable source, documentation, offline wiki and examples |
| `worldarchitect-1.1.0-source.zip` | Buildable project, tests, validation tools, docs and example sources |
| `single-block-1.1.0.zip`, `layered-1.1.0.zip`, `skyblock-1.1.0.zip`, `custom-end-1.1.0.zip` | Four unlocked creator starter bundles |
| `studio-partial-1.1.0.zip`, `studio-skyblock-1.1.0.zip` | Partially locked preset and fully locked multiplayer Skyblock pack |
| `SHA256SUMS-1.1.0.txt` | SHA-256 checksums for all nine downloads |

Place the JAR in `mods/`. Keep preset bundles zipped and place them in `config/worldarchitect/bundles/`; choose a bundle through the documented developer configuration. Third-party test mods and diagnostic fixture JARs are excluded from the gameplay distribution.

## Verification

The release checks cover 51 unit tests, Windows/Linux dedicated servers, all six clean bundle installations, Terralith and Biomes O' Plenty comparisons, two-real-client placement and restart checks, a thirty-minute multiplayer soak, native End progression and older-save generation. All 711 monitored files in twelve rejected existing saves and all fifteen original-save pins stayed unchanged. The previously published 0.9.0 and 1.0.0 artifacts remain intact.

Three alternating same-host benchmark pairs stayed within the generation-time and memory thresholds for all five profiles. All 864 managed chunk comparisons matched exactly. The report includes the raw measurements and their limits.

The [verification report](https://github.com/GWSheridan/World-Architect/blob/v1.1.0/docs/maintainers/verification/release-1.1/README.md) records binary identities, test scope, platform results and same-host performance comparisons. The source ZIP is rebuilt from a clean extraction and its gameplay JAR is compared with the tested binary before publication.

See the [creator tools guide](https://github.com/GWSheridan/World-Architect/blob/v1.1.0/docs/wiki/Creator-Tools.md) for the new controls and their limits. Terrain previews show base generation; later ores, decoration and generated structures are excluded. Manual undo refuses edited blocks or container contents and does not restore inventories or entities.
