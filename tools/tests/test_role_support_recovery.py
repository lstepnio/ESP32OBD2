"""Execute production deferred capability recovery and background probe admission."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def function(source, signature):
    start = source.index(signature + "\n{")
    opening = source.index("{", start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


class RoleSupportRecoveryTests(unittest.TestCase):
    def test_production_retry_completion_priority_and_one_probe_per_loop(self):
        source = (ROOT / "firmware/gauge/main/main.c").read_text()
        structure_end = source.index("} source_worker_t;") + len("} source_worker_t;")
        structure_start = source.rindex("typedef struct", 0, structure_end)
        worker = source[structure_start:structure_end]
        probe = function(
            source,
            "static bool role_probe(source_worker_t *worker, ble_obd_ctx_t *obd, uint32_t now)",
        )
        support = function(
            source,
            "static void role_support_probe(source_worker_t *worker, ble_obd_ctx_t *obd, uint32_t now)",
        )
        tick_start = source.index("        bool role_io = role_probe(")
        tick_end = source.index("        ui_set_source_state(", tick_start)
        tick = source[tick_start:tick_end]
        prelude = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <string.h>
#include "adapter_role_policy.h"
#include "elm_response.h"
typedef int ui_t;
typedef int poll_scheduler_t;
typedef int ble_obd_ctx_t;
#define POLL_SCHEDULER_MAX_PIDS 32
#define portENTER_CRITICAL(x) ((void)(x))
#define portEXIT_CRITICAL(x) ((void)(x))
static int adapter_role_mux;
static adapter_role_policy_t adapter_roles;
static bool adapter_role_enabled = true, pending, connected = true;
static uint32_t clock_ms, duration = 1000;
static unsigned calls, identity_calls;
static bool identity_success;
static uint16_t last_identity_pid;
static uint8_t last_base;
static int result = -1;
static bool response_more;
static int64_t esp_timer_get_time(void) { return (int64_t)clock_ms * 1000; }
static bool vehicle_commands_runtime_pending(void) { return pending; }
static bool ble_obd_is_connected(ble_obd_ctx_t *obd) { (void)obd; return connected; }
static uint32_t adapter_status_generation(unsigned source) { return source ? 20 : 10; }
static int ble_obd_read_support_map(ble_obd_ctx_t *obd, uint8_t base, uint32_t timeout, bool *more)
{
    (void)obd;
    assert(timeout == 1000);
    calls++;
    last_base = base;
    clock_ms += duration;
    *more = response_more;
    return result;
}
static int ble_obd_read_role_identity(ble_obd_ctx_t *obd, uint32_t ecu, uint16_t pid,
                                      uint32_t timeout, elm_result_t *status, elm_payload_t *payload)
{
    (void)obd; (void)ecu; (void)pid; (void)payload;
    assert(timeout == 1000 || timeout == 15000);
    identity_calls++;
    last_identity_pid = pid;
    clock_ms += 10;
    *status = ELM_ADAPTER_ERROR;
    if (identity_success && ecu == 0x7e8)
    {
        payload->has_responder = true;
        payload->responder = ecu;
        payload->length = pid == 4 ? 16 : 4;
        memset(payload->bytes, 0, payload->length);
        if (pid == 4)
            memcpy(payload->bytes, "68059434AD", 10);
        else
            memcpy(payload->bytes, "\xca\x36\x37\x8c", 4);
        *status = ELM_OK;
        return 0;
    }
    return -1;
}
"""
        harness = r"""
static bool role_barrier(source_worker_t *worker, ble_obd_ctx_t **obd)
{ (void)worker; (void)obd; return false; }
static void run(source_worker_t *worker, uint32_t now)
{
    ble_obd_ctx_t obd = 0;
    clock_ms = now;
    role_support_probe(worker, &obd, now);
}
"""
        checks = r"""
int main(void)
{
    source_worker_t w = {.probe_due = 100000};
    run(&w, 1000); /* First timeout must remain retryable on this BLE epoch. */
    assert(calls == 1 && !w.support_done && w.support_base == 0);
    assert(w.support_due == 3000 && w.support_retry_ms == 2000);
    run(&w, 2000); run(&w, 2999);
    assert(calls == 1); /* Backoff starts after request completion. */
    result = 0; response_more = true; duration = 10;
    run(&w, 3000);
    assert(calls == 2 && last_base == 0 && w.support_base == 32 && !w.support_done);
    assert(w.support_due == 4010 && w.support_retry_ms == 1000);
    result = -1; duration = 1000;
    run(&w, 4010);
    assert(calls == 3 && last_base == 32 && w.support_base == 32 && !w.support_done);
    result = 0; response_more = false; duration = 10;
    run(&w, 6010);
    assert(calls == 4 && last_base == 32 && w.support_base == 32 && w.support_done);
    run(&w, 9000);
    assert(calls == 4); /* Only the successfully read terminal page finishes. */

    w = (source_worker_t){.probe_due = 1000000, .support_base = 64};
    result = -1; duration = 1000;
    uint32_t now = 0;
    for (unsigned i = 0; i < 8; ++i)
    {
        unsigned before = calls;
        uint32_t delay = i < 3 ? (1000U << i) : 8000;
        run(&w, now);
        assert(calls == before + 1 && last_base == 64 && !w.support_done && w.support_base == 64);
        assert(w.support_due == now + duration + delay && w.support_retry_ms <= 8000);
        run(&w, w.support_due - 1);
        assert(calls == before + 1);
        now = w.support_due;
    }
    /* Deferred reads yield to identity work, user intent, and source loss. */
    unsigned before = calls;
    pending = true; run(&w, now); pending = false;
    w.probe_step = 1; run(&w, now); w.probe_step = 0;
    w.probe_due = now; run(&w, now); w.probe_due = now + 30000;
    connected = false; run(&w, now); connected = true;
    adapter_role_enabled = false; run(&w, now); adapter_role_enabled = true;
    assert(calls == before);
    result = 0; response_more = false;
    run(&w, now);
    assert(calls == before + 1 && w.support_done);

    /* Deadline comparisons continue through the monotonic uint32 rollover. */
    w = (source_worker_t){.probe_due = 10000, .support_due = UINT32_MAX - 500};
    result = -1; duration = 1000;
    run(&w, UINT32_MAX - 500);
    assert(w.support_due == 1499 && !w.support_done);
    before = calls;
    run(&w, 1498); assert(calls == before);
    result = 0; response_more = false;
    run(&w, 1499); assert(calls == before + 1 && w.support_done);

    /* Execute the production worker's actual background dispatch: completing
     * the fourth identity phase cannot also issue a support read that loop. */
    adapter_role_policy_init(&adapter_roles);
    w = (source_worker_t){.probe_generation = 10, .probe_step = 3, .probe_started = 100,
                          .probe_protocol_ready = true};
    ble_obd_ctx_t obd = 0;
    clock_ms = 100; before = calls;
    background_tick(&w, &obd);
    assert(identity_calls == 1 && calls == before && !w.probe_step);
    clock_ms += 20;
    result = 0; response_more = false;
    background_tick(&w, &obd);
    assert(identity_calls == 1 && calls == before + 1 && w.support_done);

    /* A correctly matched transport completes once per BLE generation. */
    identity_success = true;
    w = (source_worker_t){.probe_generation = 10, .probe_protocol_ready = true};
    for (unsigned phase = 0; phase < 4; ++phase)
    {
        clock_ms = 1000 + phase * 20;
        assert(role_probe(&w, &obd, clock_ms));
    }
    assert(w.probe_complete && !w.probe_step);
    unsigned identities_before = identity_calls;
    clock_ms = 40000;
    assert(!role_probe(&w, &obd, clock_ms) && identity_calls == identities_before);
    /* Completed identity proof must not suppress a deferred support retry
     * merely because the old periodic identity deadline has passed. */
    before = calls;
    w.support_done = false; w.support_due = clock_ms;
    result = 0; response_more = false;
    role_support_probe(&w, &obd, clock_ms);
    assert(calls == before + 1 && w.support_done);

    /* A stale opposite pair can explicitly wake an already completed proof. */
    adapter_role_policy_init(&adapter_roles);
    adapter_role_policy_scope(&adapter_roles, 0, 10);
    adapter_role_policy_scope(&adapter_roles, 1, 20);
    adapter_role_policy_observe(&adapter_roles, 0, 10, ADAPTER_ROLE_TCM, 0, 1000);
    adapter_role_policy_observe(&adapter_roles, 1, 20, ADAPTER_ROLE_ECM, 0, 17000);
    clock_ms = 17020;
    assert(role_probe(&w, &obd, clock_ms));
    assert(!w.probe_complete && w.probe_step == 1 && last_identity_pid == 4);
    adapter_roles.refresh_requested |= 1; /* A queued refresh during a phase. */
    clock_ms += 20;
    assert(role_probe(&w, &obd, clock_ms));
    assert(w.probe_step == 2 && last_identity_pid == 6 && adapter_roles.refresh_requested == 1);
    /* Source generation retirement re-arms the once-per-link cycle. */
    w.probe_complete = true; w.probe_generation = 9;
    clock_ms += 20;
    assert(role_probe(&w, &obd, clock_ms));
    assert(!w.probe_complete && w.probe_generation == 10 && w.probe_step == 1);
    return 0;
}
"""
        with tempfile.TemporaryDirectory() as directory:
            test = Path(directory) / "role_support_recovery.c"
            binary = Path(directory) / "role_support_recovery"
            test.write_text(
                prelude
                + worker
                + "\n"
                + probe
                + "\n"
                + support
                + harness
                + "static void background_tick(source_worker_t *worker, ble_obd_ctx_t *obd)\n{\n do {\n"
                + tick
                + "\n } while (0);\n}\n"
                + checks
            )
            subprocess.run(
                [
                    os.environ.get("CC", "cc"),
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-Ifirmware/gauge/main/inc",
                    str(test),
                    "firmware/gauge/main/src/adapter_role_policy.c",
                    "-o",
                    str(binary),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    unittest.main()
