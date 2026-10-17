#ifndef BLE_SECURITY_POLICY_H
#define BLE_SECURITY_POLICY_H

/* ESP-IDF/NimBLE level 1 rejects fresh pairing before any passkey event.
 * Levels >=2 also filter unencrypted adapter notifications. Level 0 delegates
 * owner security to the authenticated companion attributes and identity gate. */
#if !defined(CONFIG_BT_NIMBLE_SECURITY_ENABLE) || !CONFIG_BT_NIMBLE_SECURITY_ENABLE || \
    !defined(CONFIG_BT_NIMBLE_SM_SC) || !CONFIG_BT_NIMBLE_SM_SC || \
    CONFIG_BT_NIMBLE_SM_LVL != 0 || \
    (defined(CONFIG_BT_NIMBLE_SM_LEGACY) && CONFIG_BT_NIMBLE_SM_LEGACY)
#error "eGauge requires neutral global level 0, Secure Connections and disabled legacy pairing"
#endif

#endif
