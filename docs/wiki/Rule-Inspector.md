# Rule Inspector

Open **World Generation Rules → Inspect rules** while customizing a managed dimension. The terrain preview also has a **Rules** button. Inspection works on the new-world draft and does not load chunks or change a saved world.

The map samples a fixed 48 × 48 grid. Choose a rule, pan or zoom, and use **Coordinates** to enter a centre and height. Fixed-height mode checks that Y. Surface mode checks the first air block above base terrain; empty columns use the fixed height.

| Overlay | Meaning |
| --- | --- |
| All conditions | The existing evaluator's combined dimension, biome, height, region, distance, seed, mod and dependency decision |
| Biome filter | Only the selected rule's biome condition |
| Height filter | Only its inclusive Y range |
| Region filter | Only its inclusive rectangular region |
| Distance filter | Only its inclusive radial distance range |

Green means the displayed condition passes; red means it fails. Cyan marks the native End arena, where custom generation actions are excluded. Separate-filter views intentionally omit the other conditions. Biomes are sampled at the inspected Y.

Choose **Explain** for the centre coordinate. The report lists every rule in evaluation order, its phase and priority, and the pass/fail result for each condition and dependency. An eligible rule can still fail its action's target, support, collision, spacing, marker or other placement checks. Coverage does not promise that a structure, ore or decoration will appear.

**Order & warnings** shows the material, feature, template and spawn phases. Higher priorities run first; ties retain editor order. Dependencies must refer to earlier rules and require their conditions to match at the same candidate. The report includes the existing per-chunk limits: 32,768 feature steps and eight template candidates.

Shadow warnings are conservative: they identify a later material rule only when an earlier rule proves it cannot select terrain under the same conditions and targets. Rules with different targets or conditions may interact without producing a warning. Cross-dimension spawn rules are not reported as impossible merely because their destination differs from the generating dimension.

The inspector samples at most 64 cells per tick with a soft 3 ms time budget. Sampling pauses when another screen is open. Invalid graphs and excessive work budgets remain reviewable, but combined coverage and coordinate explanations are disabled until the draft validates.

See [World Generation Rules](World-Generation-Rules.md) for authoring and [End Progression](End-Progression.md) for the reserved arena.
