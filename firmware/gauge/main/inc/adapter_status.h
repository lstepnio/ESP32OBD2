#pragma once
#include <stdint.h>
#include <stddef.h>
#include "config_runtime.h"
#define ADAPTER_SOURCE_STATUS_SIZE 160
void adapter_status_init(const config_runtime_t *runtime);
void adapter_status_event(unsigned source, uint32_t generation, uint8_t phase, int result);
void adapter_status_support(unsigned source, uint32_t generation, uint32_t ecu,
                            uint8_t base, const uint8_t *bytes, size_t length);
size_t adapter_status_snapshot(uint8_t out[ADAPTER_SOURCE_STATUS_SIZE]);

bool adapter_status_ready(void);
