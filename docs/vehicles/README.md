# Vehicle knowledge

This section collects vehicle-specific OBD-II and module-diagnostic research. It is organized by make and model so a candidate definition for one ECU or model year is not mistaken for universal OBD support.

| Vehicle | Coverage | Status |
| --- | --- | --- |
| [Jeep Wrangler JK](jeep/wrangler-jk.md) | 2007-2018 family, with an initial focus on a 2007 vehicle | Research notes; no live Jeep response has been validated by eGauge |

## How to read these notes

- **Observed in vehicle:** an actual ECU response or physical behavior recorded with the vehicle and adapter identified.
- **Static analysis:** a definition or behavior visible in a diagnostic application package, with no claim that it works on the target vehicle.
- **Candidate:** a request or feature that needs a matching ECU response and decoder test.
- **Implemented:** functionality in this repository. Implementation does not establish vehicle compatibility by itself.

Keep model year, market, engine, transmission, ECU identity, diagnostic protocol, and response route together. A matching identifier alone does not select a decoder. Before a definition becomes a distributable vehicle profile, verify its use rights, the exact vehicle scope, raw response fixtures, units, and behavior on a vehicle.

This is a research catalog, not an executable command catalog. Vehicle actions require separately reviewed procedures and physical validation. See [PID discovery](../protocol/pid-discovery.md) and the [current implementation state](../current-state.md).

The [catalog research workflow](catalog-research.md) inventories all decoded diagnostic resources and explains how to search them. A compressed archive is prepared under `data/vehicle-definitions/`; the searchable SQLite database remains Git-ignored.
