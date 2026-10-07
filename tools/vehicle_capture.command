#!/bin/bash
TASK_ROOT="$(cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$TASK_ROOT" || exit 1
if [ ! -x "$TASK_ROOT/.venv/bin/python" ]; then
    echo "The capture environment is not installed. See docs/development/vehicle-capture-runbook.md."
    read -r -p "Press Return to close. "
    exit 1
fi
"$TASK_ROOT/.venv/bin/python" "$TASK_ROOT/tools/vehicle_session.py"
read -r -p "Press Return to close. "
