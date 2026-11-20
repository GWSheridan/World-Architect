# Assembly Studio example

`bundles/assembly-demo-1.3.0.zip` is an unlocked preset made from the in-game Assembly Studio workflow. Its source resources are in `assembly-demo-1.3.0-source/`.

The Overworld is flat stone with two modules. Each module has stone, stairs, an empty chest and an `entry` marker. The first module is rotated and mirrored; the second is offset eight blocks. Random whole-assembly rotation is disabled, so the example makes the individual transforms easy to inspect. The Nether and End retain Original Generation.

Install the ZIP in `config/worldarchitect/bundles/` and select it in `developer.json`:

```json
{"format":1,"bundle":"assembly-demo-1.3.0.zip"}
```

Restart and create a new world. The preset ID is `worldarchitect_examples:assembly_demo`, the seed is 18, and the structure uses a random-spread placement with spacing 16 and separation 3 chunks. Existing worlds keep their saved generation.

This small example is intended for learning offsets, transformations and markers. Capture your own templates and follow [Assembly Studio](../../docs/wiki/Assembly-Studio.md) to build a larger structure. The [cross-dimension example](../cross-dimension-structures/) contains larger hand-authored modular definitions.
