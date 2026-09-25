# Contributing

Design status must remain explicit. Mark proposed behavior, simulation, hardware-observed evidence, and implemented behavior separately. Link every feature to a requirement and acceptance gate. Keep diagrams, contracts, examples, and release notes in the same change as behavior.

Before a pull request: run `python tools/validate.py` in the documented environment; run the relevant firmware or Android build when that implementation changes; add focused tests for parser boundaries, schema changes, migration, security decisions and lifecycle state. Do not treat a screenshot, a firmware compilation, or a simulator result as vehicle compatibility evidence.

Contract changes need a version/compatibility note and examples. New PID definitions need source/license, exact vehicle scope, ECU routing, unit, decoder, sample vector and support evidence. Never copy a third-party catalog without checking its license. Document a reproducible bug using redacted logs and device/app/protocol versions.

Use short-lived feature branches and reviewed PRs after repository bootstrap. Require documentation/schema checks and relevant build jobs. Keep signing keys and personal vehicle identifiers out of Git. Product-wide license remains an owner decision; third-party notices must stay intact.
