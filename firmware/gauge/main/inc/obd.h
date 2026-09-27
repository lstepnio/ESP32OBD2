#pragma once

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

#include <stddef.h>
#include <stdint.h>
#include "pid_decoder.h"

// ---------------------------------------------------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------------------------------------------------

typedef struct
{
    uint8_t              pid;         // PID number
    size_t               len;         // Length of the data expected for this PID
    const char          *name;        // Name of the PID
    const char          *unit;        // Unit of the PID (optional)
    pid_numeric_decoder_t decoder;
} obd_pid_cfg_t;
