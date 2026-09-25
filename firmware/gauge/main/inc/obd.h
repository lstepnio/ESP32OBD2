#pragma once

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

#include <stddef.h>
#include <stdint.h>

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef int (*obd_pid_conversion_t)(int32_t *value, uint8_t const *data, size_t len);

typedef struct
{
    uint8_t              pid;         // PID number
    size_t               len;         // Length of the data expected for this PID
    const char          *name;        // Name of the PID
    const char          *unit;        // Unit of the PID (optional)
    obd_pid_conversion_t conversion;  // Function to convert raw data to a value
} obd_pid_cfg_t;
