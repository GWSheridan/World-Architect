# Shared terrain regions

`bundles/shared-regions-1.4.0.zip` is an unlocked new-world example for World Architect 1.4.0. Install it, still zipped, under `config/worldarchitect/bundles/` and select it in `developer.json`:

```json
{"format":1,"bundle":"shared-regions-1.4.0.zip","developer_mode":true}
```

Start a new world with the manifest's defaults. This requires only Minecraft 26.3, NeoForge 26.3.0.103 and World Architect. The preset ID is `worldarchitect_examples:shared_regions`. The adjacent source directory contains its manifest, preset and embedded modular structure.

The Overworld is flat stone with a `meadow` rectangle from −256 to 256 and a higher-priority `circle` of radius 64. Version-2 rules share those names: diamonds replace stone at Y 60–64 inside the circle; gold ore at Y 20–24 and amethyst decorations use the winning meadow area; a brick structure and spawn policies use the circle. Geometric masks include their boundaries and do not inherit height feathering.

Open the generation rules and choose **Named terrain region** or inspect **Region filter** to compare the decisions. Template writes are filtered per cell. The embedded `worldarchitect_examples:regional_marker` contains an empty named chest and an entry marker. Do not replace its immutable archive in an existing world. See [shared-region rules](../../docs/wiki/World-Generation-Rules.md) and [save protection](../../docs/wiki/Backups-and-Upgrades.md).
