package com.lstepnio.egauge

import android.content.Context

/** Remembers only the user's chosen gauge identity. Ownership still comes from the BLE bond. */
class GaugeAssociationStore(context: Context) {
    private val preferences = context.getSharedPreferences("gauge-association", Context.MODE_PRIVATE)

    fun rememberedId(): String? = preferences.getString(KEY_GAUGE_ID, null)

    fun remember(id: String) {
        require(id.isNotBlank())
        check(preferences.edit().putString(KEY_GAUGE_ID, id).commit()) {
            "Could not save the selected gauge"
        }
    }

    companion object {
        private const val KEY_GAUGE_ID = "selected-gauge-id"
    }
}
