package com.lstepnio.egauge.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class GaugeIcon(val path: String) {
    Gauge("M4 17a9 9 0 1 1 16 0M7 15l-2 1M17 15l2 1M12 4v3M12 14l4-4M9 20h6"),
    Car("M4 11l2-6h12l2 6M3 11h18v8H3zM6 19v2M18 19v2M6 14h2M16 14h2"),
    Settings("M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1z"),
    Check("M5 12l4 4L19 6"),
    Back("M19 12H5M11 6l-6 6 6 6"),
    Next("M5 12h14M13 6l6 6-6 6"),
    Info("M12 11v6M12 7h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0"),
    Warning("M12 3L2 21h20L12 3zM12 10v4M12 17h.01"),
    Bluetooth("M8 7l9 10-5 4V3l5 4L8 17"),
    Shield("M12 2l8 4v7c0 5-8 9-8 9s-8-4-8-9V6l8-4M8 12l3 3 5-6"),
    Tools("M4 20l8-8M14 3a6 6 0 0 0-4 8l3 3a6 6 0 0 0 8-4l-4 2-5-5 2-4"),
    Refresh("M20 8a8 8 0 1 0 0 9M20 3v5h-5"),
    Copy("M9 8h12v14H9zM5 17H2V2h13v3"),
    Add("M12 5v14M5 12h14"),
    Remove("M5 12h14"),
    Close("M5 5l14 14M19 5L5 19"),
}

@Composable
fun EGaugeIcon(icon: GaugeIcon, description: String? = null, modifier: Modifier = Modifier,
               color: Color = LocalContentColor.current) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(modifier.size(24.dp).then(if (description == null) Modifier else
        Modifier.semantics { contentDescription = description })) {
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, color, style = Stroke(width = 1.7f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}
