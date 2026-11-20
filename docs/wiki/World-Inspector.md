# World Inspector

Use `/wa_inspect` with permission level 3 to inspect the original generation snapshot. The screen lists saved dimension generators, their versions, required adapters, pinned resources and the archived preset fingerprint. It reads saved files without generating chunks or changing generation settings.

**Compare preset** chooses a saved Preset Studio revision. `/wa_inspect installed` compares the installed defaults instead. Comparisons report changed paths and different world options. They describe changes for a new world; they do not migrate the current save. Embedded resources are compared by fingerprint. Array order matters, including rule order.

**Export report** writes a local JSON file under `config/worldarchitect/diagnostics/`. The command equivalent is `/wa_inspect report`, `/wa_inspect installed report`, or `/wa_inspect compare <name> <release> report`. Reports contain bounded summaries and changed paths, without player identities, template metadata or field values. Nothing is uploaded.

The Inspector verifies snapshot/settings consistency and the archived ZIP's size and SHA-256. A healthy result does not certify another modpack's providers. Dependency lists show at most 24 IDs per section and 64 saved dimensions. Comparisons stop after 65,536 visited nodes, 256 changes or 48 nested levels and report truncation.

If a snapshot, settings file or archived bundle is missing or inconsistent, restore the matching files from a complete backup. Loading may remain blocked before commands become available. Never replace an original snapshot with current defaults. See [Backups and Upgrades](Backups-and-Upgrades.md).
