# Draft history and recovery

Preset Studio, regional terrain, generation rules and the scenario workbench have **History / recovery** controls. Use Undo and Redo for completed editor actions. A new edit after Undo starts a new branch and clears Redo. Duplicate and move controls are available for regions, rules and scenario runs; Assembly Studio also duplicates and reorders modules and pool choices.

History is bounded. Client editors retain up to 32 checkpoints within an estimated 32 MiB budget and report when a draft cannot be retained. Assembly Studio shares its original 64 MiB estimate across server sessions and evicts older checkpoints when needed; an edit that cannot fit is refused without replacing the active draft. Undo cannot overwrite an immutable saved preset version or world-generation snapshot.

Completed actions are checkpointed; unfinished text in a form is not. Client recovery files live under `config/worldsunwritten/recovery/`. Assembly recovery lives with the world under `data/worldsunwritten/assembly-recovery/`. Recovery files are bounded to 8 MiB each and 32 files per directory. Reopening an interrupted draft offers **Restore recovery** and **Discard recovery**. Review that choice explicitly before applying or saving. A mismatched base revision, malformed file or incompatible document is refused with an explanation. Recovery never silently replaces a newer saved revision.

Nested **Done/Apply** commits a child editor into its parent draft. **Cancel** discards that child change. Explicitly closing an editor clears its recovery after any required discard confirmation. Disconnecting an Assembly Studio author keeps the interrupted draft for that player's next session. Recovery files are local working data: keep them private if drafts contain unpublished pack content.

Assembly operations always check the current player's permissions, session token, edit serial and saved revision. Two authors cannot use history to overwrite each other's newer disk revision. A save advances the revision; subsequent Undo edits a new draft against that saved revision. Generation settings, archived bundles, placed blocks and existing chunks are not changed by draft history.

See [Assembly Studio](Assembly-Studio.md), [Preset Studio](Preset-Studio.md), [Regional Terrain](Regional-Terrain.md) and [Test Workbench](Test-Workbench.md).
