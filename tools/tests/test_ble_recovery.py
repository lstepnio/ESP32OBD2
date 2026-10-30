"""Host execution of production connection admission, cancellation and attempt fences."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def function(text, signature):
    start = text.index(signature + "\n{")
    opening = text.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return text[start:end]


class BleRecoveryTests(unittest.TestCase):
    def test_production_admission_cleanup_and_retired_callback_identity(self):
        source = (ROOT / "firmware/gauge/main/src/ble_mgr.c").read_text()
        connect = source[source.index("ble_mgr_status_t ble_mgr_connect_service(") :]
        connect = function(connect, connect[: connect.index("\n{")])
        helpers = "\n".join(
            function(source, signature)
            for signature in (
                "static void *attempt_arg(ble_mgr_ctx_t *ctx)",
                "static ble_mgr_ctx_t *attempt_context(void *arg)",
                "static ble_mgr_ctx_t *gap_attempt_context(struct ble_gap_event *event, void *arg)",
                "static ble_mgr_ctx_t *callback_enter(void *arg)",
                "static void callback_leave(ble_mgr_ctx_t *ctx)",
                "static bool drain_retired_callbacks(uint32_t started_ms, uint32_t timeout_ms)",
                "static bool reap_retired_links(void)",
            )
        )
        callbacks = []
        for name in ("ble_mgr_gatt_chr_discovered_cb", "ble_mgr_gatt_chr_discovered_cb_leased"):
            offset = 0
            while True:
                start = source.index("static int " + name + "(", offset)
                end = source.index(")", start) + 1
                if source[end:].lstrip().startswith("{"):
                    break
                offset = end
            signature = source[start:end]
            callbacks.append(function(source[start:], signature))
        callback = callbacks[1] + callbacks[0]
        prelude = r"""
#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stddef.h>
#include <string.h>
#include "ble_recovery_policy.h"
#include "obd_wait_policy.h"
#define BLE_HS_CONN_HANDLE_NONE 65535
#define BLE_HS_ENOTCONN 7
struct ble_gap_conn_desc { int unused; };
#define BLE_ERR_REM_USER_CONN_TERM 1
#define BLE_DISCOVERY_TIMEOUT_MS 5000
#define pdTRUE 1
#define pdMS_TO_TICKS(x) (x)
#define pdTICKS_TO_MS(x) (x)
#define ESP_NULL_CHECK(x, ...) assert(x)
#define ESP_LOGD(...) ((void)0)
#define BLE_UUID_STR_LEN 20
#define BLE_GAP_EVENT_CONNECT 1
#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))
struct ble_gap_event { int type; struct { int status; uint16_t conn_handle; } connect; };
struct ble_gatt_error { int status; };
struct ble_gatt_chr { struct { int u; } uuid; unsigned def_handle, val_handle, properties; };
static void ble_uuid_to_str(const int *uuid, char *out) { (void)uuid; strcpy(out, "test"); }
typedef enum { BLE_MGR_E_OK, BLE_MGR_E_TIMEOUT, BLE_MGR_E_NOT_CONNECTED,
               BLE_MGR_E_API_LOCK_ERROR, BLE_MGR_E_DISCOVERY_FAILED } ble_mgr_status_t;
typedef struct { unsigned def_handle, handle, cccd_handle, properties; const char *uuid; } characteristic;
typedef struct { unsigned num_chars; characteristic *chars; } service;
struct ble_mgr_ctx;
typedef struct { void *dev_filter_cb; service *svc_def; void (*disconnected_cb)(struct ble_mgr_ctx *, void *); } ble_mgr_disc_cfg_t;
typedef struct ble_mgr_ctx {
    atomic_bool is_connected, connecting, scanning, write_inflight;
    atomic_uintptr_t attempt_token;
    atomic_uint callback_readers;
    atomic_bool callback_retiring;
    atomic_uint_least16_t conn_handle;
    const ble_mgr_disc_cfg_t *disc_cfg;
    void *usr_ctx;
    struct { void *lock_mtx, *result_que; } api;
    struct { bool chr_disc_completed; } svc_disc_ctx;
} ble_mgr_ctx_t;
static ble_mgr_ctx_t BLE_MGR_CTX[2];
static atomic_bool central_paused, user_scan_active;
static void *scan_lock = (void *)1;
static int disc_params;
static uint32_t now, admission_delay, observed_wait, cleanup_until;
static bool pending, stalled_cancel;
static unsigned terminate_count, cancel_count;
static ble_mgr_ctx_t *held_callback;
static uintptr_t held_token;
static uint32_t release_at;
static uint32_t xTaskGetTickCount(void) { return now; }
static bool xSemaphoreTake(void *lock, uint32_t timeout) {
    if (lock == scan_lock) { assert(timeout == BLE_RECOVERY_ADMISSION_MS); now += admission_delay; }
    return true;
}
static void xSemaphoreGive(void *lock) { (void)lock; }
static void xQueueReset(void *queue) { (void)queue; }
static void retire_write(ble_mgr_ctx_t *ctx) { (void)ctx; }
static bool ble_gap_conn_active(void) { return pending; }
static int ble_gap_conn_find(uint16_t handle, struct ble_gap_conn_desc *desc) { (void)handle; (void)desc; return BLE_HS_ENOTCONN; }
static void ble_mgr_gap_event_cb(void) {}
static int ble_gap_disc(int a, unsigned b, void *c, void (*d)(void), void *e) {
    (void)a; (void)b; (void)c; (void)d; (void)e; pending = true; return 0;
}
static bool API_QUEUE_WAIT(ble_mgr_ctx_t *ctx, ble_mgr_status_t *status, unsigned timeout) {
    (void)ctx; (void)status; observed_wait = timeout; now += timeout; return false;
}
static void ble_gap_disc_cancel(void) {}
static void ble_gap_conn_cancel(void) { cleanup_until = now + 200; cancel_count++; }
static void ble_gap_terminate(unsigned handle, unsigned reason) { (void)handle; (void)reason; terminate_count++; }
static void vTaskDelay(unsigned delay) {
    now += delay;
    if (held_callback) {
        assert(atomic_load(&held_callback->attempt_token) == held_token);
        if (now >= release_at) {
            atomic_fetch_sub(&held_callback->callback_readers, 1);
            held_callback = NULL;
        }
    }
    if (!stalled_cancel && now >= cleanup_until) pending = false;
}
static void ble_companion_start(void) {}
static void ble_mgr_gatt_svc_chr_disc_completed_check(ble_mgr_ctx_t *ctx, const struct ble_gatt_error *error, void *arg) {
    (void)ctx; (void)error; (void)arg;
}
"""
        checks = r"""
int main(void) {
    characteristic chars[1] = {{9, 9, 9, 9, "test"}};
    service svc = {1, chars};
    ble_mgr_disc_cfg_t cfg = {(void *)1, &svc, NULL};
    BLE_MGR_CTX[0].conn_handle = BLE_HS_CONN_HANDLE_NONE;
    BLE_MGR_CTX[0].api.lock_mtx = (void *)2;
    BLE_MGR_CTX[1].conn_handle = 42;
    BLE_MGR_CTX[1].is_connected = true;
    BLE_MGR_CTX[1].attempt_token = 3;
    /* Previously a sibling's 12 s attempt consumed the entire caller budget. */
    admission_delay = 12000;
    assert(ble_mgr_connect_service(&BLE_MGR_CTX[0], &cfg, BLE_RECOVERY_TOTAL_MS, NULL) == BLE_MGR_E_TIMEOUT);
    assert(observed_wait == 12000);
    assert(now == 24200 && !pending);
    assert(now <= BLE_RECOVERY_TOTAL_MS);
    assert(BLE_MGR_CTX[1].is_connected && BLE_MGR_CTX[1].conn_handle == 42);
    assert(terminate_count == 0);
    /* Host ignores cancellation: cleanup expires, sibling remains live. */
    now = 0; admission_delay = 0; stalled_cancel = true;
    assert(ble_mgr_connect_service(&BLE_MGR_CTX[0], &cfg, BLE_RECOVERY_TOTAL_MS, NULL) == BLE_MGR_E_TIMEOUT);
    assert(now == 13000 && pending);
    unsigned previous_cancels = cancel_count;
    now = 0;
    assert(ble_mgr_connect_service(&BLE_MGR_CTX[0], &cfg, BLE_RECOVERY_TOTAL_MS, NULL) == BLE_MGR_E_TIMEOUT);
    assert(now == 1000 && cancel_count == previous_cancels + 1);
    assert(BLE_MGR_CTX[1].is_connected && BLE_MGR_CTX[1].conn_handle == 42);
    /* A subsequent cancellation acknowledgement admits the queued source. */
    stalled_cancel = false; now = 0;
    assert(ble_mgr_connect_service(&BLE_MGR_CTX[0], &cfg, BLE_RECOVERY_TOTAL_MS, NULL) == BLE_MGR_E_TIMEOUT);
    assert(observed_wait == 12000 && !pending && now == 12400);
    void *retired = attempt_arg(&BLE_MGR_CTX[0]);
    assert(attempt_context(retired) == &BLE_MGR_CTX[0]);
    BLE_MGR_CTX[0].attempt_token = ble_recovery_next_token((uintptr_t)retired, 0);
    assert(attempt_context(retired) == NULL);
    /* Even reuse of the same host connection handle cannot admit old GATT data. */
    BLE_MGR_CTX[0].conn_handle = 10; BLE_MGR_CTX[0].connecting = true;
    struct ble_gatt_error success = {0};
    struct ble_gatt_chr discovered = {{0}, 77, 78, 16};
    ble_mgr_gatt_chr_discovered_cb(10, &success, &discovered, retired);
    assert(chars[0].handle == 0);
    ble_mgr_gatt_chr_discovered_cb(10, &success, &discovered, attempt_arg(&BLE_MGR_CTX[0]));
    assert(chars[0].handle == 78);
    /* Retired successful CONNECT cannot terminate a sibling's reused handle. */
    struct ble_gap_event late = {BLE_GAP_EVENT_CONNECT, {0, 42}};
    unsigned previous_terminations = terminate_count;
    assert(gap_attempt_context(&late, retired) == NULL);
    assert(terminate_count == previous_terminations);
    assert(BLE_MGR_CTX[1].is_connected && BLE_MGR_CTX[1].conn_handle == 42);
    /* The same rule protects a newer session in the retired source's slot. */
    late.connect.conn_handle = 10;
    assert(gap_attempt_context(&late, retired) == NULL);
    assert(terminate_count == previous_terminations);
    assert(BLE_MGR_CTX[0].conn_handle == 10 && BLE_MGR_CTX[0].connecting);
    /* Only an unowned successful retired connection is cleaned up. */
    late.connect.conn_handle = 99;
    assert(gap_attempt_context(&late, retired) == NULL);
    assert(terminate_count == previous_terminations + 1);
    late.connect.status = 1;
    assert(gap_attempt_context(&late, retired) == NULL);
    assert(terminate_count == previous_terminations + 1);
    assert(gap_attempt_context(&late, attempt_arg(&BLE_MGR_CTX[0])) == &BLE_MGR_CTX[0]);
    assert(attempt_context(attempt_arg(&BLE_MGR_CTX[0])) == &BLE_MGR_CTX[0]);
    assert(attempt_context((void *)3) == &BLE_MGR_CTX[1]);
    assert(ble_recovery_next_token(UINTPTR_MAX - 1, 0) == 2);
    assert(ble_recovery_next_token(UINTPTR_MAX, 1) == 3);
    assert(ble_recovery_operation_ms(1000) == 0);
    assert(ble_recovery_operation_ms(1001) == 1);
    assert(ble_recovery_operation_ms(13000) == 12000);
    assert(obd_wait_remaining(UINT32_MAX - 2, 2, 5) == 0);
    /* A worker retry cannot change token/context beneath an admitted callback. */
    BLE_MGR_CTX[0].conn_handle = BLE_HS_CONN_HANDLE_NONE;
    BLE_MGR_CTX[0].connecting = false;
    void *before_retry = attempt_arg(&BLE_MGR_CTX[0]);
    held_callback = callback_enter(before_retry);
    assert(held_callback == &BLE_MGR_CTX[0]);
    held_token = (uintptr_t)before_retry;
    release_at = 200; now = 0;
    assert(ble_mgr_connect_service(&BLE_MGR_CTX[0], &cfg, BLE_RECOVERY_TOTAL_MS, NULL) == BLE_MGR_E_TIMEOUT);
    assert(held_callback == NULL && attempt_arg(&BLE_MGR_CTX[0]) != before_retry);
    assert(now == 12400);
    /* A non-draining reader times out without publishing a new attempt. */
    before_retry = attempt_arg(&BLE_MGR_CTX[0]);
    held_callback = callback_enter(before_retry);
    assert(held_callback);
    held_token = (uintptr_t)before_retry;
    release_at = UINT32_MAX; now = 0;
    assert(ble_mgr_connect_service(&BLE_MGR_CTX[0], &cfg, BLE_RECOVERY_TOTAL_MS, NULL) == BLE_MGR_E_TIMEOUT);
    assert(now == 1000 && attempt_arg(&BLE_MGR_CTX[0]) == before_retry);
    assert(BLE_MGR_CTX[0].disc_cfg == &cfg);
    assert(callback_enter(before_retry) == NULL);
    callback_leave(held_callback); held_callback = NULL;
    now = 0;
    assert(ble_mgr_connect_service(&BLE_MGR_CTX[0], &cfg, BLE_RECOVERY_TOTAL_MS, NULL) == BLE_MGR_E_TIMEOUT);
    assert(attempt_arg(&BLE_MGR_CTX[0]) != before_retry);
    assert(BLE_MGR_CTX[1].is_connected && BLE_MGR_CTX[1].conn_handle == 42);

}
"""
        with tempfile.TemporaryDirectory() as folder:
            cfile = Path(folder) / "recovery.c"
            binary = Path(folder) / "recovery"
            cfile.write_text(prelude + helpers + connect + callback + checks)
            subprocess.run(
                [
                    os.environ.get("CC", "cc"),
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-I",
                    str(ROOT / "firmware/gauge/main/inc"),
                    str(cfile),
                    "-o",
                    str(binary),
                ],
                check=True,
                capture_output=True,
                timeout=30,
            )
            subprocess.run([str(binary)], check=True, capture_output=True, timeout=10)
