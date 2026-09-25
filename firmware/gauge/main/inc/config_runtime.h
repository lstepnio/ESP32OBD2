#pragma once

#include <stdbool.h>
#include <stdint.h>
#include "esp_err.h"
#include "esp_partition.h"
#include "obd.h"

#define EGAUGE_RUNTIME_PIDS 32
#define EGAUGE_RUNTIME_PAGES 8
#define EGAUGE_RUNTIME_ALERTS 32

typedef struct {
    char id[65];
    char name[65];
    char unit[17];
    obd_pid_cfg_t obd;
    uint32_t poll_ms;
    uint32_t stale_ms;
} runtime_pid_t;

typedef struct {
    char id[65];
    uint8_t pid_index;
    bool above;
    bool has_warning;
    bool has_critical;
    double warning;
    double critical;
    double hysteresis;
    uint32_t trigger_dwell_ms;
    uint32_t clear_dwell_ms;
    uint8_t priority;
} runtime_alert_t;

typedef struct {
    uint8_t pid_count;
    uint8_t page_count;
    uint8_t alert_count;
    uint8_t page_pids[EGAUGE_RUNTIME_PAGES];
    uint8_t rotation;
    runtime_pid_t pids[EGAUGE_RUNTIME_PIDS];
    runtime_alert_t alerts[EGAUGE_RUNTIME_ALERTS];
} config_runtime_t;

/* Compiles only operations the current ELM transport and display can execute.
 * Unsupported documents remain staged and cannot become active. */
esp_err_t config_runtime_validate(const esp_partition_t *partition,
                                  uint32_t document_offset, uint32_t length,
                                  void *context);
esp_err_t config_runtime_load(config_runtime_t *out);
