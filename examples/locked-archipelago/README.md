# Locked archipelago

Install `bundles/locked-archipelago-1.5.0.zip` through the [modpack configuration](../../docs/MODPACKS.md), then create a new world. Preset ID: `worldsunwritten_examples:locked_archipelago`. Use Worlds Unwritten 1.5 on clients and server. No additional provider mod is required.

The Overworld supplies personal Skyblock islands. The Nether and End retain their original profiles and native dragon progression. A separate `worldsunwritten_examples:highlands` dimension combines polygon rules with vertical towers. It uses the registered Overworld dimension type; it does not add a portal system. Administrators can enter it with:

```text
/execute in worldsunwritten_examples:highlands run tp @s 16 80 16
```

This bundle is fully locked and disables structure-library authoring. Clients and dedicated servers must reject generation overrides, including a changed seed. Gameplay commands retain their normal permissions.

For a partial variant, duplicate the source as a new preset version, select a partial lock and allow only the highlands' ore editor. Save/export that new version and test allowed ore edits alongside refused terrain, anchor, pool and seed changes. Do not replace the archive of an existing world. See [Locks and Server Enforcement](../../docs/wiki/Locks-and-Server-Enforcement.md) and [Batch Testing](../../docs/wiki/Batch-Testing.md).
