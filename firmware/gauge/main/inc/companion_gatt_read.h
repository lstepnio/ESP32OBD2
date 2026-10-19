#pragma once
#include <stddef.h>
#include <stdint.h>
#include "host/ble_att.h"
#include "os/os_mbuf.h"

/* NimBLE ble_gatts_val_access applies the ATT Read Blob offset after this
 * callback. Always append the complete, immutable value, never a sliced tail. */
static inline int companion_gatt_read_value(struct os_mbuf *om, const void *value,
                                            size_t length, size_t offset)
{
    if (offset > length) return BLE_ATT_ERR_INVALID_OFFSET;
    if (length > UINT16_MAX) return BLE_ATT_ERR_INSUFFICIENT_RES;
    return os_mbuf_append(om, value, (uint16_t)length) == 0
        ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
}
