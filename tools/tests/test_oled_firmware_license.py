"""Link the boot verifier with the pinned ESP-IDF Mbed TLS crypto library."""

from pathlib import Path
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from board_license import issue, openssl

GAUGE = Path(__file__).resolve().parents[2] / "firmware/gauge"
MAC = "00:11:22:33:44:55"
STUB_SDK = """#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#define ESP_LOGI(tag, ...) ((void)(tag))
#define ESP_OK 0
#define NVS_READONLY 0
#define ESP_PARTITION_TYPE_DATA 1
#define ESP_PARTITION_SUBTYPE_DATA_NVS 2
typedef struct { uint32_t address, size; bool readonly, encrypted; } esp_partition_t;
typedef int esp_err_t;
typedef unsigned nvs_handle_t;
const esp_partition_t *esp_partition_find_first(int, int, const char *);
int esp_efuse_mac_get_default(uint8_t *);
int nvs_flash_init_partition(const char *);
int nvs_flash_deinit_partition(const char *);
esp_err_t nvs_open_from_partition(const char *, const char *, int, nvs_handle_t *);
esp_err_t nvs_get_blob(nvs_handle_t, const char *, void *, size_t *);
void nvs_close(nvs_handle_t);
"""


@unittest.skipUnless(os.environ.get("IDF_PATH"), "Source pinned ESP-IDF for the boot-verifier test")
class OledFirmwareLicenseTest(unittest.TestCase):
    def test_boot_verifies_host_issued_license(self):
        sdk = Path(os.environ["IDF_PATH"]) / "components/mbedtls/mbedtls"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for header in ("esp_log.h", "esp_mac.h", "esp_partition.h", "nvs.h", "nvs_flash.h"):
                (root / header).write_text('#include "sdk.h"\n')
            (root / "sdk.h").write_text(STUB_SDK)

            def key(name):
                path = root / name
                path.touch(mode=0o600)
                path.write_bytes(openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout"))
                return path

            issuer, other = key("issuer.pem"), key("other.pem")
            public = root / "public.pem"
            public.write_bytes(openssl("pkey", "-in", issuer, "-pubout"))
            asm = root / "public.S"
            asm.write_text(
                "#ifdef __APPLE__\n.section __TEXT,__const\n#else\n.section .rodata\n#endif\n"
                ".globl _binary_oled_license_public_pem_start, _binary_oled_license_public_pem_end\n"
                "_binary_oled_license_public_pem_start:\n"
                f'.incbin "{public}"\n'
                ".byte 0\n"
                "_binary_oled_license_public_pem_end:\n"
                '#ifndef __APPLE__\n.section .note.GNU-stack,"",%progbits\n#endif\n'
            )

            def verify(*args):
                result = subprocess.run(args, capture_output=True, text=True, timeout=120)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

            build = root / "mbedtls"
            verify("cmake", "-S", sdk, "-B", build, "-DENABLE_TESTING=OFF", "-DENABLE_PROGRAMS=OFF")
            verify("cmake", "--build", build, "--target", "mbedcrypto", "--parallel", "4")
            binary = root / "oled_license_test"
            verify(
                "cc",
                "-std=gnu11",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-fsanitize=address,undefined",
                "-DEGAUGE_OLED_LICENSE_REQUIRED=1",
                "-DEGAUGE_FEATURE_DUAL_ADAPTER=1",
                "-DEGAUGE_FEATURE_SMART_CYCLE=1",
                "-I",
                root,
                "-I",
                sdk / "include",
                "-I",
                GAUGE / "main/inc",
                GAUGE / "tests/oled_license_test.c",
                GAUGE / "main/src/oled_license.c",
                asm,
                build / "library/libmbedcrypto.a",
                "-o",
                binary,
            )
            fixture = root / "test.egauge-license"
            for signing_key, flags, expected in ((issuer, 7, 3), (issuer, 1, 1), (other, 7, 0)):
                with self.subTest(flags=flags, expected=expected):
                    fixture.write_bytes(issue(MAC, flags, signing_key))
                    verify(binary, fixture, str(expected))
