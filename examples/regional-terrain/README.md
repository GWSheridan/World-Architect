# Regional terrain example

`bundles/regional-demo-1.4.0.zip` is an unlocked preset with flat Layered terrain at elevation 64, a forest rectangle at 120 and a higher-priority desert circle at 180. Region edges feather inward. The other dimensions retain native generation. Structures are disabled in this terrain-focused example.

Copy the ZIP to `config/worldsunwritten/bundles/` and select it in `developer.json`:

```json
{"format":1,"bundle":"regional-demo-1.4.0.zip","developer_mode":true}
```

Create a new world. Keep the ZIP intact. The adjacent `regional-demo-1.4.0-source/` directory shows its manifest and full preset JSON. See [Regional Terrain](../../docs/wiki/Regional-Terrain.md) for editing, priorities, biome boundaries and limits.
