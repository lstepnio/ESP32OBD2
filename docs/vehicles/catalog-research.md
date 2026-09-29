# Local diagnostic catalog research

The complete decoded resource inventory is available locally in `references/vehicle-catalog/catalog.sqlite`. That directory is Git-ignored. The database contains every decoded JSON resource and an index of top-level records. A compact [definition archive](../../data/vehicle-definitions/README.md) in the working tree contains the same decoded resources in 34 compressed shards. The [export tool](../../tools/research/export_vehicle_catalog.py) rebuilds the database from locally held managed assemblies; the [query tool](../../tools/research/query_vehicle_catalog.py) searches it and assembles a vehicle inventory. The extracted definitions are research inputs, not verified eGauge profiles or vehicle commands.

## Current inventory

The 2026-09-28 extraction decoded 30,092 of 30,092 resources with no parse failures. It contains 13,711,439 indexed top-level records. Counts below are resource files, not unique PIDs or compatible vehicle features.

| Resource family | Files | Diagnostic use |
| --- | ---: | --- |
| Live data | 15,360 | Candidate service/identifier, value position, conversion, and unit definitions |
| Activations | 5,697 | Candidate temporary or routine-style module operations |
| Writes | 4,324 | Candidate configuration or data-write records |
| DTC | 4,332 | Module-specific fault definitions |
| Descriptions | 231 | Labels and diagnostic descriptions |
| Vehicle profiles | 1 | 67 vehicle family entries |
| Vehicle adaptations | 123 | Vehicle and shared configuration/maintenance groups |
| Module indices and other settings | 24 | Diagnostic routes, module metadata, and supporting settings |

The catalog is roughly 3.2 GB. It retains the complete decoded JSON in `resources.payload_json`; `entries` indexes module, mode, PID, DTC, name, route, and a representative command field. A record may contain additional command fields, parameters, and stop conditions in its full JSON payload. Use `--raw` when inspecting one. Resource names and record identifiers are useful join keys but do not establish vehicle compatibility on their own.

This is a complete inventory of decoded embedded JSON resources, not a complete decompilation of executable handlers. Some features, including timed actions and safety-module controls, have logic outside those resources. Their final wire sequence and recovery behavior still require separate analysis and vehicle validation.

## Rebuild and inspect

Install `dnfile` and `json5` in a research Python environment, then run the exporter with the local data assemblies:

```sh
python3 -m pip install dnfile json5
python3 tools/research/export_vehicle_catalog.py /path/to/Common.dll /path/to/DataStorage.dll
```

The exporter refuses to overwrite an existing catalog. A completed catalog also records SHA-256 hashes of the input files and all parse errors in the `inputs` and `errors` tables. A partial run remains marked `.partial` and is not treated as complete.

The [packaging tool](../../tools/research/package_vehicle_catalog.py) creates compressed JSONL shards and a hash manifest from the local database. It refuses to overwrite a nonempty output directory. The current archive is 34 MB on disk, with every shard below 7 MB.

To reconstruct the SQLite index from the compressed archive without the original assemblies, run `python3 tools/research/import_vehicle_archive.py`. It verifies shard sizes and hashes before importing and refuses to overwrite an existing database.

```sh
python3 tools/research/query_vehicle_catalog.py vehicle JK --output references/vehicle-catalog/jeep-wrangler-jk.json
python3 tools/research/query_vehicle_catalog.py search --category LiveData --module PCM --mode 22 --pid B010 --raw
python3 tools/research/query_vehicle_catalog.py search --category VehicleAdaptations --name 'Engine Idle' --raw
```

The [JK definition report](../../data/vehicle-definitions/vehicles/jeep/wrangler-jk.json) contains its vehicle profile, 17 candidate module routes, and all 332 top-level entries across its 21 referenced adaptation files. These include group headings and shared entries that may not apply to a particular JK. The report does not automatically attach the broader live-data, DTC, activation, or write resources to a JK. That requires module identification and matching diagnostic variants.

## Promotion to a vehicle profile

For reads, use the vehicle profile and module route to identify the ECU, then match its reported variant and diagnostic version to a definition. Save a raw request/response fixture and independently test the decoder, unit, response length, and error behavior. Only then add an eGauge read definition for that exact compatibility scope.

For an action, distinguish a command fragment from the full procedure. Establish the diagnostic session, any access requirements, preconditions, duration, heartbeat or tester-present behavior, readback, cancellation, disconnect, and restoration. Keep action implementation separate from the custom PID editor. The [Wrangler JK notes](jeep/wrangler-jk.md) identify the first vehicle-specific questions. No action in this inventory is enabled in eGauge.

The SQLite catalog remains Git-ignored. The compressed archive and JK definition report are versioned in Git. The archive manifest retains the package provenance, input hashes, and the repository owner's statement that public redistribution is permitted. The underlying license documentation has not been independently reviewed.
