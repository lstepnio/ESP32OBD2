package com.lstepnio.egauge.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.state.*

@Composable
fun ConnectionStatusWidget(gaugeLabel: String, gaugeReady: Boolean,
    links: List<ConnectionLinkUi>, updateNotice: UpdateNotice, onClick: () -> Unit) {
    ConnectionPill(appConnectionLabel(gaugeLabel, gaugeReady, links),
        gaugeReady && links.isNotEmpty() && links.all { it.status.tone == StatusTone.Success },
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = buildList {
                add("Phone to gauge: $gaugeLabel")
                links.forEach { add("${it.title}: ${it.status.title}") }
                if (updateNotice == UpdateNotice.Ready) add("Update available")
                if (updateNotice == UpdateNotice.NeedsCheck) add("Update needs attention")
            }.joinToString(". ")
        },
        icon = when (updateNotice) {
            UpdateNotice.Ready -> GaugeIcon.Download
            UpdateNotice.NeedsCheck -> GaugeIcon.Warning
            UpdateNotice.None -> null
        }, onClick = onClick, clickLabel = "Show connection status")
}
