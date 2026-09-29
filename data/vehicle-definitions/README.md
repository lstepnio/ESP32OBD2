# Diagnostic definition research archive

`manifest.json` describes 34 compressed JSONL shards containing 30,092 decoded resource files and 13,711,439 top-level records. Each shard is independently hashed and below GitHub's per-file Git limit. Each JSONL line has the original resource name, resource category, optional module folder, and complete decoded definitions.

The [Jeep Wrangler JK report](vehicles/jeep/wrangler-jk.json) provides the first vehicle-organized view. It includes all referenced adaptation records and candidate module routes, with an explicit static-research status. Shared definitions in that report are not automatically supported by every JK.

The [JK handler inventory](vehicles/jeep/wrangler-jk-handler-inventory.json) preserves static method and command-literal findings that are outside the embedded JSON definitions. The [analysis notes](../../docs/vehicles/jeep/jk-handler-analysis.md) distinguish those fragments from verified vehicle procedures.

The manifest retains the package provenance, input hashes, and the repository owner's statement that public redistribution is permitted. The underlying license documentation has not been independently reviewed. These records are candidate research data and do not establish compatibility with any vehicle. In particular, activation and write records are fragments of diagnostic behavior, not safe standalone procedures. Nothing in this archive is enabled by eGauge.

To inspect one shard locally:

```sh
zstd -dc data/vehicle-definitions/vehicleadaptations-032.jsonl.zst | less
```

The [catalog research guide](../../docs/vehicles/catalog-research.md) explains the searchable local SQLite index and vehicle-specific audit process. The SQLite index is generated from the same decoded resources and stays outside Git because it is roughly 3.2 GB.

To rebuild the local index from these shards, run `python3 tools/research/import_vehicle_archive.py`. It verifies every shard hash and refuses to overwrite an existing database.

The exact APK files used for this extraction are preserved locally under the Git-ignored `references/vehicle-catalog/source-package/` directory. Their sizes and SHA-256 hashes are recorded in the manifest. The APK binaries are not included in Git; the definitions and derived handler inventory are.
