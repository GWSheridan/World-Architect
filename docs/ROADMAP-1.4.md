# World Architect 1.4

Milestones 38–42 connect regional terrain, procedural assemblies and the test workbench while retaining existing saved generation.

| Milestone | Deliverable | Acceptance |
| --- | --- | --- |
| 38 · Authoring history | Bounded undo/redo, duplication/reordering and explicit recovery of interrupted drafts | Nested Done/Cancel, stale revisions, disconnects, locks and no snapshot replacement |
| 39 · Shared regions | Named terrain masks in material, ore, decoration, structure and spawn conditions; matching Inspector overlays | Negative coordinates, edges, priority, strict writes, versioned decisions and unchanged old chunks |
| 40 · Pool constraints | Module count/depth requirements, rejected-layout explanations and repeatable seed previews | Deterministic bounded planning, safe markers, restart persistence and no partial placement |
| 41 · Workbench authoring | In-game scenario editing/exports and bounded CLI batch comparisons | Shared validation, permission checks, provider/lock/spawn rejection and isolated Windows/Linux jobs |
| 42 · Release integration | Exact-JAR acceptance, multiplayer, End progression, upgrade copies, benchmarks, guides and artifacts | Four real clients, strict save/chunk audits, 1.3 comparisons and an exact source rebuild |

API 1 and existing generation algorithms remain supported. New rule/pool behaviour uses explicit format versions. Generation snapshots and saved preset versions remain immutable. Tests use disposable instances or copies of saves. Logs and reports stay local by default; publication is a separate request.

Implementation and acceptance are in progress. Published main, the live wiki and latest release remain on 1.3.0.
