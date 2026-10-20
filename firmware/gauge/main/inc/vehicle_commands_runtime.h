#pragma once
#include "vehicle_commands.h"
#include "config_runtime.h"
#include "ble_obd.h"
#include "esp_err.h"

esp_err_t vehicle_commands_runtime_init(const config_runtime_t *runtime);
/* Authenticated App and physical shortcut/menu both enqueue typed intents here.
 * Queue admission never performs flash or adapter IO on the caller's thread. */
bool   vehicle_commands_runtime_submit(const uint8_t *bytes, size_t length);
size_t vehicle_commands_runtime_status(uint8_t kind, uint8_t out[32]);
/* Primary source owner only. True reserves its adapter for this command job.
 * Context qualification/identity/guard proofs are supplied by the vehicle profile,
 * never inferred from a readable PID, selected adapter or phone connection. */
bool vehicle_commands_runtime_worker(unsigned                  source,
                                     ble_obd_ctx_t            *adapter,
                                     vehicle_command_context_t context,
                                     uint32_t                  now);
/* Physical menu shares the same boot/revision/link-fenced queue and operation counter. */
bool vehicle_commands_runtime_intent(uint8_t kind, bool stop, uint8_t preset, uint16_t seconds);
bool vehicle_commands_runtime_maintenance_allowed(bool configuration);
