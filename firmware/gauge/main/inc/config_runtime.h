#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"
#include "esp_partition.h"
#include "obd.h"
#include "config_store.h"

#define EGAUGE_RUNTIME_PIDS 32
#define EGAUGE_RUNTIME_PAGES 8
#define EGAUGE_RUNTIME_ALERTS 32
#define CONFIG_RUNTIME_STATUS_SIZE 44

typedef enum {
    RUNTIME_RENDERER_NUMERIC = 0,
    RUNTIME_RENDERER_ARC,
    RUNTIME_RENDERER_BAR,
    RUNTIME_RENDERER_TREND,
    RUNTIME_RENDERER_DUAL,
} runtime_renderer_t;

typedef struct {
    char id[65];
    char name[33];
    runtime_renderer_t renderer;
    uint8_t pid_count;
    uint8_t pid_indices[2];
} runtime_page_t;

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
    uint8_t rotation;
    uint8_t brightness;
    runtime_pid_t pids[EGAUGE_RUNTIME_PIDS];
    runtime_page_t pages[EGAUGE_RUNTIME_PAGES];
    runtime_alert_t alerts[EGAUGE_RUNTIME_ALERTS];
} config_runtime_t;

/* Compiles only operations the current ELM transport and display can execute.
 * Unsupported documents remain staged and cannot become active. */
esp_err_t config_runtime_validate(const esp_partition_t *partition,
                                  uint32_t document_offset, uint32_t length,
                                  void *context);
esp_err_t config_runtime_load(config_runtime_t *out, config_store_record_t *running,
                              bool *used_previous);
/* Protected boot-time identity: running revision/hash, previous-generation
 * recovery, and unconfirmed trial state. */
size_t config_runtime_status(uint8_t out[CONFIG_RUNTIME_STATUS_SIZE]);
/* Clears the volatile trial flag after the persistent journal is confirmed. */
void config_runtime_mark_confirmed(void);
