# Preset Studio and modpack authoring

Preset Studio is a local, versioned library for new-world generation presets. It captures complete dimension profiles, world-generation options, rule-engine spawn policies, native starting islands and selected modular structure definitions. Runtime administration settings are configured in the destination world.

## Open Studio

Enable authoring in `config/worldarchitect/developer.json`, then restart:

```json
{"format":1,"developer_mode":true}
```

In **Create New World → Customize**, configure the dimensions, then choose **Preset Studio**. Studio is available from the dimension manager in an authoring instance; an installed locked bundle retains its own editors. **Tools / help → Help** explains authoring, installation, lock modes and dependencies in game. A fully locked preset also has **Preset help**.

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

Ordinary bundles retain manifest format 1 and the existing saved-world archive contract. Inherited presets use format 2, described below. Alongside native `data/<namespace>/structure/<path>.nbt`, bundles support embedded `worldarchitect:modular` definitions at `data/<namespace>/worldgen/structure/<path>.json`. Other registry file types are outside this bundle format. The limits remain 128 combined template files, 4 MiB per template, 8 MiB per JSON document and 32 MiB per archive/template set. A Studio library holds up to 256 revisions.

## Install and enforce

Copy an exported ZIP into a clean client and dedicated server's `config/worldarchitect/bundles` directory. Set the selected filename in each instance and restart:

```json
{"format":1,"bundle":"studio-skyblock-1.4.0.zip"}
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

The [ready-to-install examples](https://github.com/GWSheridan/World-Architect/tree/v1.7.0/examples/preset-studio) include a partial preset with a bundled modular build and a fully locked multiplayer Skyblock preset.

## Draft history and workbench export

Use **History / recovery** for completed draft actions and interrupted-session recovery. Immutable saved releases remain separate from history. Save and validate the current draft before choosing **Workbench** to author a fingerprinted scenario for that exact revision. See [Draft History](Draft-History.md) and [Test Workbench](Test-Workbench.md).

## Reusable base presets in 1.7

Save and validate a parent revision, then choose **Tools / help → Derive from saved preset**. Select its saved version and choose a child library name and release. Studio pins the parent's preset ID, release and exact archive SHA-256. The child receives all effective generation settings and embedded resources. A changed or missing parent archive is refused while deriving; no existing revision is overwritten.

Use **Inherited preset report** to inspect the pinned ancestry, policy and effective override paths. **Edit inherited override** accepts a JSON pointer, a JSON value and an optional remove operation. A pointer such as `/dimensions/minecraft:overworld/generator/profile/material` addresses one field. Escape a literal `~` as `~0` and `/` as `~1`. Object paths must already exist up to their final field; array edits address existing numeric indexes. Overrides validate before changing the draft. Normal dimension editors remain available within the inherited policy.

A child may tighten its parent's locks but cannot broaden an edit scope, remove an inherited mod dependency or re-enable a disabled Structure Library. Locked world options remain fixed. Every embedded resource inherited from a locked parent is byte-pinned, including resources used by editable dimensions; add new resources for new structures instead of rewriting a pinned file. An unlocked parent permits generation changes and records its ancestry without imposing generation locks.

Exports are self-contained. Clients and servers need the child ZIP and its external mods or registry dependencies; they do not need the parent ZIP. Deleting, replacing or releasing a later parent cannot change an exported child or an existing world's archived child. Creating an updated child requires deriving another immutable revision. Cycles, repeated ancestor identities and chains beyond eight parent revisions are refused. The embedded policy baseline is limited to 4 MiB and 64 levels. Format 2 is required exactly when `inheritance` is present; older releases refuse it.

Dedicated authoring commands use the same validation:

```text
/wa_preset derive <parent> <release> <child> <child_release>
/wa_preset override <child> <release> <new_release> "<json_pointer>" <json_value>
```

Both require administrator permission and developer mode. `override` saves a new immutable child version. See the [reusable Skyblock examples](https://github.com/GWSheridan/World-Architect/tree/v1.7.0/examples/reusable-presets) for a base, a partially locked child and a fully locked child with bundled island templates and native End progression.
