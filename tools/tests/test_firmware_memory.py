"""The actual linked-image admission guard and bounded Wi-Fi allocation lifecycle."""

from pathlib import Path
import importlib.util
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "firmware_memory", ROOT / "tools/check_firmware_memory.py"
)
memory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(memory)


class FirmwareMemoryTests(unittest.TestCase):
    def test_static_budget_counts_iram_and_dram_but_not_external_arena(self):
        sections = {
            ".dram0.data": 20000,
            ".dram0.bss": 20000,
            ".iram0.text": 120000,
            ".iram0.vectors": 1000,
            ".ext_ram.bss": 85000,
        }
        self.assertEqual(memory.validate_internal_budget(sections), 161000)
        with self.assertRaises(ValueError):
            memory.validate_internal_budget(dict(sections, **{".dram0.bss": 100000}))

    def test_allocation_failure_hook_does_not_allocate_log_or_query_heap(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "freertos").mkdir()
            (folder / "esp_attr.h").write_text("#define IRAM_ATTR\n")
            (folder / "freertos/FreeRTOS.h").write_text("""
typedef unsigned portMUX_TYPE;
#define portMUX_INITIALIZER_UNLOCKED 0
void test_memory_enter(void *);
void test_memory_exit(void *);
#define portENTER_CRITICAL_SAFE(p) test_memory_enter(p)
#define portEXIT_CRITICAL_SAFE(p) test_memory_exit(p)
""")
            (folder / "esp_heap_caps.h").write_text("""
#pragma once
#include <stddef.h>
#include <stdint.h>
typedef int esp_err_t;
#define ESP_OK 0
#define MALLOC_CAP_INTERNAL 1
#define MALLOC_CAP_8BIT 2
#define MALLOC_CAP_DMA 4
#define MALLOC_CAP_SPIRAM 8
typedef void (*esp_alloc_failed_hook_t)(size_t,uint32_t,const char*);
esp_err_t heap_caps_register_failed_alloc_callback(esp_alloc_failed_hook_t);
size_t heap_caps_get_free_size(unsigned);
size_t heap_caps_get_largest_free_block(unsigned);
size_t heap_caps_get_minimum_free_size(unsigned);
""")
            (folder / "esp_log.h").write_text("""
void test_memory_log(const char*,const char*,...);
#define ESP_LOGI(...) test_memory_log(__VA_ARGS__)
#define ESP_LOGW(...) test_memory_log(__VA_ARGS__)
""")
            binary = folder / "memory-health"
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-I" + str(folder),
                    "-I" + str(ROOT / "firmware/gauge/main/inc"),
                    str(ROOT / "firmware/gauge/main/src/memory_health.c"),
                    str(ROOT / "firmware/gauge/tests/memory_health_test.c"),
                    "-o",
                    str(binary),
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run([str(binary)], check=True, capture_output=True, text=True)

    def test_link_guard_rejects_internal_missing_duplicate_and_oversized_arenas(self):
        arena = {"address": 0x3C030000, "bytes": 65536, "section": ".ext_ram.bss"}
        memory.validate_arena([arena])
        for invalid in (
            [],
            [arena, arena],
            [dict(arena, section=".dram0.bss", address=0x3FCB0000)],
            [dict(arena, bytes=131072)],
            [dict(arena, address=0x3DFF8000)],
        ):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                memory.validate_arena(invalid)

    def test_wifi_service_allocation_failures_leave_no_partial_tasks_or_buffers(self):
        source = (ROOT / "firmware/gauge/main/src/wifi_bulk.c").read_text()
        start = source.index("esp_err_t wifi_bulk_init(void)")
        end = source.index("bool wifi_bulk_command", start)
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "wifi_service_init.inc").write_text(source[start:end])
            binary = folder / "wifi-init"
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-I" + str(folder),
                    str(ROOT / "firmware/gauge/tests/wifi_service_init_test.c"),
                    "-o",
                    str(binary),
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run([str(binary)], check=True, capture_output=True, text=True)

    def test_wifi_snapshot_stays_readable_during_worker_activity_and_scrubs_expired_keys(self):
        source = (ROOT / "firmware/gauge/main/src/wifi_bulk.c").read_text()
        start = source.index("size_t wifi_bulk_status(")
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "wifi_status.inc").write_text(source[start:])
            binary = folder / "wifi-status"
            subprocess.run(
                [
                    "cc",
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-fsanitize=address,undefined",
                    "-I" + str(folder),
                    str(ROOT / "firmware/gauge/tests/wifi_status_test.c"),
                    "-o",
                    str(binary),
                ],
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run([str(binary)], check=True, capture_output=True, text=True)
