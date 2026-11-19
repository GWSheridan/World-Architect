# Preset Studio and modpack authoring

Preset Studio is a local, versioned library for new-world generation presets. It captures complete dimension profiles, world-generation options, rule-engine spawn policies, native starting islands and selected modular structure definitions. Runtime administration settings are configured in the destination world.

## Open Studio

Enable authoring in `config/worldarchitect/developer.json`, then restart:

```json
{"format":1,"developer_mode":true}
```

In **Create New World → Customize**, configure the dimensions, then choose **Preset Studio**. Studio is available from the dimension manager in an authoring instance; an installed locked bundle retains its own editors. **Help** explains authoring, installation, lock modes and dependencies in game. A fully locked preset also has **Preset help**.

## Author a version

1. Open **Metadata**. Choose a simple library name, a non-Minecraft preset registry ID, title, author, release and description. Add required mod IDs, separated by commas. Capturing a preset includes installed mod namespaces referenced by its generator JSON in the initial dependency list; review the dependency report for other resources.
2. Set **World defaults**: seed, structure generation and bonus chest.
3. Open **Locks** and choose `unlocked`, `partial` or `full`. A partial lock must grant at least one explicit edit. For each dimension, permit its whole profile or selected feature groups. Feature groups require managed solid terrain. Whole-dimension permission covers its rule engine. Individual feature permissions cover the existing Features groups and preserve locked rule-engine settings.
4. Review **Dependencies**. Validation checks codecs, dimension bounds, adapters, rule budgets, template cells and metadata, spawn references and lock applicability. The report lists required mods and installed versions, resource namespaces, adapter versions, rule-condition dependencies and embedded files. Registry namespaces can belong to datapacks rather than mods; required external biomes and dimension types must still be supplied.
5. Choose **Save version**. Studio writes `config/worldarchitect/studio/<name>/<release>.zip`. Versions are immutable. Saving over an existing version fails. Use a new release to retain another version, or **Duplicate** to start a separate preset ID and library name.
6. Choose **Export ZIP** to copy the saved version into `config/worldarchitect/exports/<name>-<release>.zip`. Existing exports are preserved. Export uses the saved version; save changes under a new release first.

In 1.1, use **Browse saved presets** to search by name, title, version, author or lock status, then **Load selected** to restore a revision into the Studio draft. A changed draft prompts before replacement. **Apply to world** updates the new-world dimension-manager draft. Choose **Done** there to apply dimensions and world defaults. **Cancel** in the manager discards those changes. Explicitly saved library versions remain on disk.

Applying a Studio draft transfers its generation settings for authoring and testing. Install its exported bundle to activate the manifest's lock and Structure Library policies.

Imported modular definitions that are absent from the active registries can be validated and exported using an isolated registry overlay. Install their bundle and restart to load those definitions before **Apply to world** becomes available. Validation never registers definitions in a running world.

## Import

Copy an incoming bundle ZIP into `config/worldarchitect/imports`, choose **Import ZIP**, and enter its local filename. Import validates dependencies, profiles, locks and templates before loading the draft. Use **Save version** to retain it. Unsupported registry files, malformed templates, missing blocks/mods, invalid markers and oversized resources produce errors. Archive entry paths and filenames are restricted; archives are read directly rather than extracted into arbitrary directories.

Bundles retain manifest format 1 and the existing saved-world archive contract. Alongside native `data/<namespace>/structure/<path>.nbt`, bundles support embedded `worldarchitect:modular` definitions at `data/<namespace>/worldgen/structure/<path>.json`. Other registry file types are outside this bundle format. The limits remain 128 combined template files, 4 MiB per template, 8 MiB per JSON document and 32 MiB per archive/template set. A Studio library holds up to 256 revisions.

## Install and enforce

Copy an exported ZIP into a clean client and dedicated server's `config/worldarchitect/bundles` directory. Set the selected filename in each instance and restart:

```json
{"format":1,"bundle":"studio-skyblock-1.2.0.zip"}
```

Use the same World Architect version and required mods. A new client world selects the bundle automatically. A dedicated server obtains omitted preset, seed and structure-generation properties from the manifest. Editable server dimension selections go in `config/worldarchitect/world-selection.json` before creating the world. See [the bundle guide](Templates-and-Bundling.md) for lock semantics and installation.

Client controls and final creation validation enforce the same policy as the dedicated server. Full locks fix generation, including island layouts. Partial locks permit only listed dimensions, feature groups and world options. World name, difficulty, game mode and gameplay rules remain available. Developer authoring commands require both administrator permission and developer mode; normal installed instances expose no authoring operations.

Existing saves retain their original generation snapshots, rule sets, templates and archived bundle fingerprint. Installing a later release selects defaults for future worlds. Restore the original archived ZIP if a protected world's archive is lost; editing a library version cannot repair or rewrite an existing save.

## Dedicated authoring commands

Administrators can use these local-file workflows:

```text
/wa_preset help
/wa_preset list
/wa_preset capture <name> <release>
/wa_preset import <filename.zip> <name>
/wa_preset duplicate <name> <release> <copy> <new_release>
/wa_preset validate <name> <release>
/wa_preset export <name> <release>
```

`capture` snapshots the authoring world's generation settings into a new library version. `validate` prints its dependency report. Authoring operations do not alter the running world's generation. Existing JSON drafts and `/wa_modpack export` remain supported and now share Studio validation and modular-template packaging.

The [ready-to-install examples](https://github.com/GWSheridan/World-Architect/tree/v1.2.0/examples/preset-studio) include a partial preset with a bundled modular build and a fully locked multiplayer Skyblock preset.
