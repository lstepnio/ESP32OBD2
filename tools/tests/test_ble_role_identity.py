"""Fixed read-only role API routing and strict ECM/TCM identity framing."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class BleRoleIdentityTests(unittest.TestCase):
    def test_fixed_role_requests_and_exact_responder(self):
        source = (ROOT / "firmware/gauge/main/src/ble_obd.c").read_text()
        start = source.index("int ble_obd_read_role_identity(")
        end = source.index("\nint ble_obd_read_primary_input(", start)
        api = source[start:end]
        stop_start = source.index("void ble_obd_disconnect(")
        stop_end = source.index("\nble_obd_ctx_t *ble_obd_connect_bound(", stop_start)
        api += source[stop_start:stop_end]
        init_start = source.index("static bool initialize_adapter(")
        init_end = source.index("\n// ---", init_start)
        api += source[init_start:init_end]
        read_start = source.index("static int read_pid(")
        read_end = source.index("\nint ble_obd_read_pid(", read_start)
        api = source[read_start:read_end] + api
        fixture = r"""
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <stdatomic.h>
#include "ble_obd.h"
#include "obd_wait_policy.h"
struct ble_obd_ctx {
    atomic_bool ready, rx_overflow;
    bool awaiting_prompt;
    unsigned consecutive_timeouts;
    uint32_t active_generation;
    void *mutex;
    ble_obd_response_cb_t response_cb;
    void *usr_ctx;
    atomic_uint generation;
    unsigned source_id;
    void *mgr_ctx;
    bool control_dirty;
    uint32_t discovery_ecu;
    uint16_t request_header;
    elm_response_t response;
};
static bool manager_connected;
static uint32_t retired_generation;
static bool ble_mgr_is_connected(void *ctx) { (void)ctx; return manager_connected; }
static void ble_mgr_disconnect(void *ctx) { (void)ctx; manager_connected = false; }
static void adapter_status_event(unsigned source, uint32_t generation, unsigned phase, int status) {
    (void)source; (void)status; if (phase == 1) retired_generation = generation;
}
static elm_response_t response;
static unsigned calls, seen_header, initialization_requests, setup_commands, support_updates, last_request_budget;
static bool sticky_ecm_filter, custom_flow;
static int bootstrap_observed;
#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))
#define ESP_LOGW(...) ((void)0)
static bool adapter_command(ble_obd_ctx_t *obd, const char *command, uint32_t timeout) {
    (void)obd; assert(timeout == 2000); assert(!strncmp(command, "AT", 2));
    if (!strcmp(command, "ATCRA\r")) sticky_ecm_filter = false;
    if (!strcmp(command, "ATFCSM0\r")) custom_flow = false;
    setup_commands++; return true;
}
int ble_obd_rxtx_ecu(ble_obd_ctx_t *obd, uint8_t mode, uint16_t pid, uint32_t ecu, uint32_t timeout) {
    assert(mode == 1 && pid == 0 && ecu == obd->discovery_ecu && timeout == 15000);
    initialization_requests++;
    const char *text = ecu == 0x7e8 ? "7E8 06 41 00 00 00 00 00\r>" : "7E9 06 41 00 00 00 00 00\r>";
    elm_response_reset(&obd->response);
    for (; *text; text++) elm_response_push(&obd->response, *text);
    return 0;
}
static void adapter_status_support(unsigned source, uint32_t generation, uint32_t ecu, unsigned base, const uint8_t *bytes, size_t length) {
    (void)source; (void)generation; (void)ecu; (void)base; (void)bytes; assert(length == 4); support_updates++;
}
typedef uint32_t TickType_t;
#define pdMS_TO_TICKS(x) (x)
#define pdTRUE 1
#define BLE_MGR_E_OK 0
static bool prompt_available = true;
static uint32_t xTaskGetTickCount(void) { return 0; }
static bool xSemaphoreTake(void *mutex, uint32_t budget) { (void)mutex; return budget != 0; }
static void xSemaphoreGive(void *mutex) { (void)mutex; }
static bool wait_for_prompt(ble_obd_ctx_t *obd, uint32_t budget) {
    (void)budget;
    if (prompt_available) obd->awaiting_prompt = false;
    return prompt_available;
}
static bool drain_rx(ble_obd_ctx_t *obd) { (void)obd; return true; }
static bool select_request_header_locked(ble_obd_ctx_t *obd, uint16_t header, TickType_t start, TickType_t budget) {
    (void)start; (void)budget; obd->request_header = header; seen_header = header; return true;
}
static int send_command_budget(ble_obd_ctx_t *obd, const char *command, uint32_t budget) {
    assert(budget && (!strcmp(command, "0904\r") || !strcmp(command, "0906\r") || !strcmp(command, "0100\r") || !strcmp(command, "0120\r")));
    assert(!sticky_ecm_filter && !custom_flow);
    last_request_budget = budget;
    calls++; obd->response = response; return BLE_MGR_E_OK;
}
static void obd_trace_emit(unsigned source, uint32_t generation, const char *event, const void *data, size_t length, int status) {
    (void)source; (void)generation; (void)data; (void)length;
    if (!strcmp(event, "protocol_bootstrap")) bootstrap_observed = status;
}
static uint64_t esp_timer_get_time(void) { return 0; }
static void wire(const char *text) {
    elm_response_reset(&response);
    for (; *text; text++) elm_response_push(&response, *text);
}
"""
        checks = r"""
int main(void) {
    ble_obd_ctx_t ctx = {0};
    manager_connected = true;
    elm_payload_t p;
    elm_result_t status;
    wire("7E9 07 49 06 01 82 40 DA E8\r>");
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 6, 1200, &status, &p) == 0);
    assert(seen_header == 0x7e1 && status == ELM_OK && p.length == 4);
    assert(p.has_responder && p.responder == 0x7e9 && p.bytes[0] == 0x82);
    assert(ble_obd_read_role_identity(&ctx, 0x7e8, 6, 1200, &status, &p) != 0);
    assert(seen_header == 0x7e0 && p.length == 0);
    assert(elm_response_decode_identity(&response, 6, &p) != ELM_OK);
    wire("7E8 07 49 06 01 CA 36 37 8C\r>");
    assert(ble_obd_read_role_identity(&ctx, 0x7e8, 6, 1200, &status, &p) == 0);
    assert(p.responder == 0x7e8 && p.bytes[0] == 0xca);
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 6, 1200, &status, &p) != 0);
    wire("7E9 10 13 49 04 01 36 38 32\r7E9 21 37 34 38 36 37 41 45\r7E9 22 00 00 00 00 00 00\r>");
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 4, 1200, &status, &p) == 0);
    assert(p.length == 16 && !memcmp(p.bytes, "68274867AE", 10));
    assert(ble_obd_read_role_identity(&ctx, 0x7e8, 4, 1200, &status, &p) != 0);
    wire("7E9 07 49 06 01 82 40 DA E8\r7E8 07 49 06 01 CA 36 37 8C\r>");
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 6, 1200, &status, &p) != 0);
    wire("49 06 01 82 40 DA E8\r>");
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 6, 1200, &status, &p) != 0);
    wire("7E9 07 49 06 02 82 40 DA E8\r>");
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 6, 1200, &status, &p) != 0);
    unsigned before = calls;
    assert(ble_obd_read_role_identity(&ctx, 0x7df, 6, 1200, &status, &p) != 0);
    assert(status == ELM_ADAPTER_ERROR && p.length == 0);
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 0x22, 1200, &status, &p) != 0);
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 4, 0, &status, &p) != 0);
    assert(ble_obd_read_role_identity(NULL, 0x7e9, 4, 1200, &status, &p) != 0);
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 4, 1200, &status, NULL) != 0);
    assert(calls == before);

    ctx.discovery_ecu = 0x7e9;
    bool more = false;
    wire("7E9 06 41 00 00 00 00 01\r>");
    assert(ble_obd_read_support_map(&ctx, 0, 1200, &more) == 0 && more);
    assert(seen_header == 0x7e1 && support_updates == 1);
    wire("7E8 06 41 00 00 00 00 01\r>");
    assert(ble_obd_read_support_map(&ctx, 0, 1200, &more) != 0 && !more);
    assert(support_updates == 1);
    before = calls;
    assert(ble_obd_read_support_map(&ctx, 1, 1200, &more) != 0 && calls == before);
    /* A late prior response must drain before any new request is sent. */
    ctx.awaiting_prompt = true; ctx.active_generation = ctx.generation;
    prompt_available = false; before = calls;
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 4, 1200, &status, &p) != 0);
    assert(calls == before && ctx.awaiting_prompt && p.length == 0);
    prompt_available = true;
    wire("7E9 07 49 06 01 82 40 DA E8\r>");
    assert(ble_obd_read_role_identity(&ctx, 0x7e9, 6, 1200, &status, &p) == 0);
    assert(calls == before + 1 && !ctx.awaiting_prompt);
    ctx.ready = true; ctx.generation = 7; ctx.rx_overflow = true;
    ctx.mgr_ctx = (void *)1; manager_connected = true;
    ble_obd_disconnect(&ctx);
    assert(!ctx.ready && !ctx.rx_overflow && ctx.generation == 8 && retired_generation == 8);
    assert(!manager_connected);
    ble_obd_disconnect(&ctx);
    assert(ctx.generation == 8);
    ble_obd_disconnect(NULL);
    ctx.discovery_ecu = 0x7e9;
    sticky_ecm_filter = custom_flow = true;
    manager_connected = true;
    wire("7E9 06 41 00 98 18 00 01\r>");
    assert(initialize_adapter(&ctx, true));
    assert(ctx.ready && ctx.discovery_ecu == 0x7e9 && setup_commands == 9);
    assert(!sticky_ecm_filter && !custom_flow && seen_header == 0x7df);
    assert(last_request_budget == 15000 && bootstrap_observed == ELM_OK);
    assert(initialization_requests == 0 && support_updates == 1);
    /* TCM bus bootstrap is not ECM calibration/identity evidence. */
    assert(elm_response_decode_identity_for_ecu(&response, 4, 0x7e8, &p) != ELM_OK);
    ctx.ready = false; prompt_available = false;
    before = calls;
    assert(!initialize_adapter(&ctx, true));
    assert(ctx.awaiting_prompt && !ctx.ready && calls == before + 1);
    before = calls;
    assert(ble_obd_read_role_identity(&ctx, 0x7e8, 4, 1200, &status, &p) != 0 && calls == before);
    prompt_available = true; ctx.awaiting_prompt = false;
    ctx.discovery_ecu = 0x7e8;
    assert(initialize_adapter(&ctx, false));
    assert(initialization_requests == 1 && support_updates == 2);
    ctx.discovery_ecu = 0x7e9;
    assert(initialize_adapter(&ctx, false));
    assert(initialization_requests == 2 && support_updates == 3 && ctx.request_header == 0x7e1);
}
"""
        with tempfile.TemporaryDirectory() as folder:
            cfile = Path(folder) / "role.c"
            binary = Path(folder) / "role"
            cfile.write_text(fixture + api + checks)
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
                    str(ROOT / "firmware/gauge/main/src/elm_response.c"),
                    "-o",
                    str(binary),
                ],
                check=True,
                capture_output=True,
                timeout=30,
            )
            subprocess.run([str(binary)], check=True, capture_output=True, timeout=10)
