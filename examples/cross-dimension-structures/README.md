# Cross-dimension structure generation

This datapack generates native End Cities on a flat End Stone Overworld and three-module template assemblies in `wa_example:workshop` and `wa_example:fortress`. The Nether and End use empty managed profiles.

Copy this folder into a new world's `datapacks` directory. Set these properties before the first dedicated-server launch.

```properties
level-type=wa_example\:cross_dimension
level-seed=194723
generate-structures=true
initial-enabled-packs=vanilla,file/cross-dimension-structures
```

The generation rules use 12-chunk spacing and 11-chunk separation. Candidate starts occur at multiples of 192 blocks. Force-load an area around the origin to check the first assemblies.

```text
forceload add -64 -64 64 64
execute in wa_example:workshop run forceload add -16 -16 32 32
execute in wa_example:fortress run forceload add -16 -16 32 32
locate structure minecraft:end_city
execute in wa_example:workshop run locate structure wa_example:village
execute in wa_example:workshop run wa_structure generated_markers wa_example:village 1 81 1
```

Each assembly embeds its templates and placement rules. Its three named modules each retain an `entry` spawn marker. Use the marker inspection command to find their seeded, rotated coordinates. An operator player can travel with `wa_structure generated_spawn wa_example:village 1 81 1 main entry` in the workshop dimension.

After stopping the server, verify the saved chunks with `gradlew.bat auditCrossDimensionWorld -PterrainWorld=<world-folder>`. The audit checks native End City pieces, template blocks, block states, decorative metadata, and marker clearance. [Generation and authoring guide](../../docs/FEATURES.md#modular-template-generation)
