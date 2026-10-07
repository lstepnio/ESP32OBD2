package com.lstepnio.egauge.ui

import androidx.compose.runtime.Composable
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.*

@Composable
fun ConnectionStatusWidget(gaugeLabel: String, gaugeReady: Boolean,
    links: List<ConnectionLinkUi>, updateNotice: UpdateNotice, onClick: () -> Unit) {
    ConnectionPill(appConnectionLabel(gaugeLabel, gaugeReady, links),
        gaugeReady && links.isNotEmpty() && links.all { it.status.tone == StatusTone.Success },
        icon = when (updateNotice) {
            UpdateNotice.Ready -> GaugeIcon.Download
            UpdateNotice.NeedsCheck -> GaugeIcon.Warning
            UpdateNotice.None -> null
        }, onClick = onClick, clickLabel = "Show connection status")
}
