#!/usr/bin/env python3
"""Check the IDF resolver output against exact registry pins and the repository-local BSP.

Uses PyYAML already supplied by ESP-IDF's component manager. The resolver lock embeds
an absolute BSP path, so it is regenerated locally rather than committed.
"""

from pathlib import Path
import re
import yaml

ROOT = Path(__file__).resolve().parents[1]
GAUGE = ROOT / "firmware/gauge"


def main():
    declared = yaml.safe_load((GAUGE / "main/idf_component.yml").read_text())["dependencies"]
    lock = yaml.safe_load((GAUGE / "dependencies.lock").read_text())
    resolved = lock["dependencies"]
    if set(resolved) != set(declared) or lock["target"] != "esp32s3":
        raise SystemExit(
            "Dependency graph changed; explicitly pin every registry component in main/idf_component.yml"
        )
    for name, specification in declared.items():
        actual = resolved[name]
        if name == "idf":
            if actual["version"] != "5.4.1":
                raise SystemExit("Use the qualified ESP-IDF 5.4.1 toolchain")
        elif isinstance(specification, str):
            if (
                not re.fullmatch(r"\d+\.\d+\.\d+", specification)
                or actual["version"] != specification
            ):
                raise SystemExit(f"Registry component must resolve to its exact pin: {name}")
            if actual["source"]["type"] != "service" or not re.fullmatch(
                r"[0-9a-f]{64}", actual["component_hash"]
            ):
                raise SystemExit(f"Missing registry integrity metadata: {name}")
        else:
            expected = (GAUGE / "main" / specification["path"]).resolve()
            if (
                actual["source"]["type"] != "local"
                or Path(actual["source"]["path"]).resolve() != expected
            ):
                raise SystemExit(f"BSP must resolve to this checkout: {name}")
    print(
        f"PASS: {len(resolved)} resolved components match pinned toolchain, registry versions and local BSP"
    )


if __name__ == "__main__":
    main()
