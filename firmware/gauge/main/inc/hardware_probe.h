#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define HARDWARE_PROBE_STATUS_SIZE 56U

enum {
    HARDWARE_FEATURE_WIFI = 1U << 0,
    HARDWARE_FEATURE_BLE = 1U << 1,
    HARDWARE_FEATURE_PSRAM = 1U << 2,
    HARDWARE_FEATURE_DISPLAY = 1U << 3,
    HARDWARE_FEATURE_TOUCH = 1U << 4,
    HARDWARE_FEATURE_BACKLIGHT = 1U << 5,
    HARDWARE_FEATURE_IMU = 1U << 6,
    HARDWARE_FEATURE_BATTERY_ADC = 1U << 7,
    HARDWARE_FEATURE_EXPANSION = 1U << 8,
    HARDWARE_FEATURE_USB_UART = 1U << 9,
};

/* Marks subsystems that completed initialization in this boot. Declared board
 * features and initialized features remain separate in the wire snapshot. */
void hardware_probe_set_ready(uint32_t features, bool ready);

/* Writes one bounded, little-endian protocol-0 snapshot. */
size_t hardware_probe_status(uint8_t out[HARDWARE_PROBE_STATUS_SIZE]);
