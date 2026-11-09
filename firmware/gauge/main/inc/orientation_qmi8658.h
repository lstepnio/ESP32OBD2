#pragma once
#include "orientation_runtime.h"

struct i2c_master_bus_t;

/* Use the board's existing bus. The sensor adapter owns only its devices. */
bool orientation_qmi8658_bus(struct i2c_master_bus_t *shared, orientation_bus_t *bus);
