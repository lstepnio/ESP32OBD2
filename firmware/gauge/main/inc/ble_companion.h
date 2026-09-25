#pragma once

/* Experimental public discovery endpoint. Register before NimBLE host starts. */
int ble_companion_register(void);
/* Called on the NimBLE host thread after controller synchronization. */
void ble_companion_start(void);
/* Forget a phone handle after a host reset. */
void ble_companion_reset(void);
