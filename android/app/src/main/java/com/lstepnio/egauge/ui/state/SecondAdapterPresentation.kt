package com.lstepnio.egauge.ui.state

import com.lstepnio.egauge.*

fun secondAdapterSetupIssue(
    primary: AdapterBinding?,
    child: AdapterBinding?,
    connected: Boolean,
    supported: Boolean
): String? = when {
    primary == null -> "Choose the primary engine adapter in Car first."

    child == null -> "Choose the second adapter for the transmission connection."

    samePhysicalAdapter(
        primary,
        child
    ) -> "Choose a different adapter from the primary engine adapter."

    !connected -> "Reconnect the gauge to check second-adapter support."

    !supported -> "Update the gauge before using both adapters."

    else -> null
}
