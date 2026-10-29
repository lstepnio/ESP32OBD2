#pragma once
#include <stdint.h>

/* Two workers: at most one full operation plus cleanup ahead of admission. */
#define BLE_RECOVERY_OPERATION_MS 12000U
#define BLE_RECOVERY_CLEANUP_MS 1000U
#define BLE_RECOVERY_ADMISSION_MS (BLE_RECOVERY_OPERATION_MS + BLE_RECOVERY_CLEANUP_MS)
#define BLE_RECOVERY_TOTAL_MS (BLE_RECOVERY_ADMISSION_MS + BLE_RECOVERY_OPERATION_MS + BLE_RECOVERY_CLEANUP_MS)

static inline uint32_t ble_recovery_admission_ms(uint32_t total_ms)
{
    return total_ms < BLE_RECOVERY_ADMISSION_MS ? total_ms : BLE_RECOVERY_ADMISSION_MS;
}

static inline uint32_t ble_recovery_operation_ms(uint32_t remaining_ms)
{
    if (remaining_ms <= BLE_RECOVERY_CLEANUP_MS)
        return 0;
    remaining_ms -= BLE_RECOVERY_CLEANUP_MS;
    return remaining_ms < BLE_RECOVERY_OPERATION_MS ? remaining_ms : BLE_RECOVERY_OPERATION_MS;
}

static inline uintptr_t ble_recovery_next_token(uintptr_t old, unsigned source)
{
    uintptr_t generation = (old & ~(uintptr_t)1U) + 2U;
    if (!generation)
        generation = 2U;
    return generation | (source & 1U);
}
