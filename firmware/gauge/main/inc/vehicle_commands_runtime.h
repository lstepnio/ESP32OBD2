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

/* Coherent local UI snapshot adds presentation bits, leaving the wire unchanged. */
size_t vehicle_commands_runtime_ui_status(uint8_t kind, uint8_t out[32]);

/* Worker-owned conservative cost for guard/identity work after a diagnostic.
 * No IO and no changes to control timing or availability. */
uint32_t vehicle_commands_runtime_poll_reserve(unsigned source, uint32_t horizon_ms, uint32_t now_ms);

/* Recheck at an ordinary-work boundary so newly queued Stop is serviced first. */
bool vehicle_commands_runtime_pending(void);
/* Physical UI heartbeat only; expiry/release is observed directly by the worker,
 * even if its intent queue is full. No callbacks perform adapter IO. */
void vehicle_commands_runtime_burnout_hold(bool held);

/* Local operator observation only, never a wire command or ECU-state decoder. */
bool vehicle_commands_runtime_confirm(uint8_t kind);
