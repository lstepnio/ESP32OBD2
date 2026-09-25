#pragma once

#include <stdint.h>
#include "freertos/queue.h"

typedef struct _ui_t ui_t;

/* Experimental public discovery endpoint. Register before NimBLE host starts. */
int ble_companion_register(void);
/* Called on the NimBLE host thread after controller synchronization. */
void ble_companion_start(void);
/* Forget a phone handle after a host reset. */
void ble_companion_reset(void);

/* Call before BLE host startup. Selection requests are applied by app_main. */
void ble_companion_set_control(ui_t *ui, QueueHandle_t selection_queue, uint8_t selected_index);
void ble_companion_open_pairing_window(void);
void ble_companion_selection_applied(uint8_t selected_index);
void ble_companion_tick(void);
/* Physical 12-second hold only. Erases the owner association and restarts. */
void ble_companion_forget_owner(void);
