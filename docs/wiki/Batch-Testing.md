# Batch comparisons

Use `tools/workbench/batch.mjs` from the source distribution to compare reviewed scenarios across Worlds Unwritten versions, provider sets and seeds. It runs jobs sequentially in new disposable directories. Build the separate fixture first; it is not part of the gameplay JAR. Read [Test Workbench](Test-Workbench.md) for installation, EULA, probe and scenario details.

```text
node tools/workbench/batch.mjs --matrix path/to/matrix.json --output build/pack-matrix --network online
```

Matrix format 1 uses relative input paths, explicit SHA-256 fingerprints and a total deadline. For example:

```json
{
  "format": 1,
  "budgets": {"max_jobs": 8, "timeout_seconds": 1800},
  "versions": [{
    "name": "candidate", "jar": "worldsunwritten.jar", "sha256": "REPLACE_WITH_64_LOWERCASE_HEX_DIGITS",
    "fixture": "validation.jar", "fixture_sha256": "REPLACE_WITH_64_LOWERCASE_HEX_DIGITS"
  }],
  "scenarios": [{"name": "pack", "file": "pack.scenario.json", "sha256": "REPLACE_WITH_64_LOWERCASE_HEX_DIGITS"}],
  "seeds": ["-9223372036854775808", "9223372036854775807"]
}
```

Paths are relative to the matrix file. Each scenario's bundle remains beside that scenario and has its own fingerprint. Add optional `providers` entries with `name`, `mods` (a directory) and `files` (an exact JAR filename-to-SHA-256 map). An empty map selects no extra mods. Unexpected JARs are refused. A scenario entry can limit `versions` to named inputs, so new format-2 rules and pools need not be sent to an older reader.

Optional `seeds` override every run's seed while preserving exact signed 64-bit strings. Without it, each scenario retains its original seeds. The expansion is versions × selected scenarios × providers × seeds. Limits are four versions, eight scenarios, four provider sets, eight seed overrides, at most 32 jobs and 8,192 requested chunks. The total deadline is 30–14,400 seconds. Input bytes are frozen and verified before any test instance is created, with a 512 MiB shared input bound.

An intentionally failing runtime scenario can declare `expected_status: "failed"` and a specific `expected_error` substring of 8–200 characters. Its nonzero exit, failed result and matching evidence are all required. A timeout or interrupted job cannot count as an expected failure. Startup-rejection cases inside a scenario retain the scenario's own acceptance contract.

The batch writes local `results.json` and an escaped `report.html`, plus individual job results and logs. Reports distinguish expected and actual outcomes and record fingerprints, provider inputs and generation pins. Durations include startup and validation and must not be treated as generation benchmarks. Interrupting the batch requests orderly server shutdown before the bounded fallback stops its owned process tree. Test directories are retained for investigation and never reused or deleted by the runner.

Run one workbench or batch process per source checkout: the Gradle server task's generated launch arguments are shared. Stop other game/build workloads before using the separate [performance tools](Installation.md#compatibility).

Failed jobs record a `failure_kind`: `assertion`, `error`, `timeout` or `interrupted`. The HTML report shows that type beside the outcome. An expected runtime failure needs an explicit assertion or error result and its specific evidence; stage deadlines and total-batch deadlines both remain failed tests.
