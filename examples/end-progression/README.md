# End progression example

This datapack keeps the native End arena and generates flat purpur terrain with End City candidates outside it. The Overworld and Nether have separate managed profiles.

Copy `pack.mcmeta` and `data/` into a new world's `datapacks/end-progression/`. Before first server launch, set:

```properties
level-type=wa_example\:end_progression
level-seed=194723
generate-structures=true
initial-enabled-packs=vanilla,file/end-progression
```

Use matching mod/provider versions on clients and the server. The reservation spans blocks −256 through 255 on both horizontal axes. Custom structures cannot overlap it; native gateways may create landing islands outside it. Existing worlds keep their original generation.

Read [End progression](../../docs/wiki/End-Progression.md) for the full guide.
