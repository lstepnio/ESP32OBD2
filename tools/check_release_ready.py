#!/usr/bin/env python3
"""Refuse publication unless the latest quality run passed for this exact source SHA."""

import json
import os
import re
import subprocess


def require_success(runs, sha):
    matches = [run for run in runs if run.get("head_sha") == sha]
    if not matches:
        raise ValueError("No quality run found for the release SHA")
    latest = max(matches, key=lambda run: (run["run_number"], run.get("run_attempt", 1)))
    if latest.get("status") != "completed" or latest.get("conclusion") != "success":
        raise ValueError("Latest quality run for the release SHA has not passed")
    return latest["html_url"]


def main():
    sha = os.environ["GITHUB_SHA"]
    repository = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise SystemExit("Expected full source SHA")
    result = subprocess.check_output(
        [
            "gh",
            "api",
            "--paginate",
            "--slurp",
            f"repos/{repository}/actions/workflows/quality.yml/runs?head_sha={sha}&per_page=100",
        ],
        timeout=120,
    )
    runs = [run for page in json.loads(result) for run in page["workflow_runs"]]
    print("Quality prerequisite: " + require_success(runs, sha))


if __name__ == "__main__":
    main()
