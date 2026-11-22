# Procedural pool example

`bundles/pool-demo-1.4.0.zip` embeds a junction, hall and two weighted end caps. A seeded layout contains five to eight modules, with named door channels, safe entry markers and empty named chests. It generates on flat Single Block terrain in the Overworld; the other dimensions retain native generation.

Copy the ZIP to `config/worldsunwritten/bundles/` and select it in `developer.json`:

```json
{"format":1,"bundle":"pool-demo-1.4.0.zip","developer_mode":true}
```

Create a new world. The structure ID is `worldsunwritten_examples:pool_demo`. The adjacent `pool-demo-1.4.0-source/` directory contains readable definitions and embedded templates. Layouts must complete within their budgets before any pieces are placed. See [Procedural Pools](../../docs/wiki/Procedural-Pools.md).
