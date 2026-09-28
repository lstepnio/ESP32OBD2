package com.lstepnio.egauge.core.designsystem

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp

/** A mock physical display, with a separate font-scalable accessible equivalent. Always synthetic. */
@Composable
fun RoundPreview(state: ReadingPreviewUi, modifier: Modifier = Modifier, showDescription: Boolean = true) {
    val accent = when (state.condition) {
        PreviewCondition.Critical -> EGaugeTokens.Dark.critical
        PreviewCondition.Warning, PreviewCondition.Stale -> EGaugeTokens.Dark.warning
        else -> EGaugeTokens.Dark.gaugeAccent
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = state.description }) {
            scale(size.width / 280f, size.height / 280f, pivot = Offset.Zero) {
                drawCircle(EGaugeTokens.Dark.gauge, 139f, Offset(140f, 140f))
                drawCircle(Color(0xFF3A4039), 138f, Offset(140f, 140f), style = Stroke(1.4f))
                val missing = state.condition in setOf(PreviewCondition.Stale, PreviewCondition.Offline)
                if (state.layout == PreviewLayout.Arc) {
                    drawArc(Color(0xFF303730), 138f, 264f, false, Offset(16f, 16f), Size(248f, 248f), style = Stroke(5f, cap = StrokeCap.Round))
                    if (!missing) drawArc(accent, 138f, if (state.condition == PreviewCondition.Critical) 258f else 148f,
                        false, Offset(16f, 16f), Size(248f, 248f), style = Stroke(5f, cap = StrokeCap.Round))
                }
                fun text(value: String, y: Float, size: Float, color: Color = EGaugeTokens.Dark.gaugeText,
                         bold: Boolean = false) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = size; this.color = color.toArgb(); textAlign = Paint.Align.CENTER
                        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                    }
                    // Device geometry is fixed; the scalable description below carries the full name.
                    val fitted = if (paint.measureText(value) > 220f) value.take(19) + "…" else value
                    drawContext.canvas.nativeCanvas.drawText(fitted, 140f, y, paint)
                }
                text("PREVIEW", 55f, 10f, EGaugeTokens.Dark.muted)
                if (state.layout == PreviewLayout.Dual) {
                    text(state.name, 82f, 12f)
                    text(state.shownValue, 121f, 38f, accent, true)
                    text(state.unit, 141f, 12f, EGaugeTokens.Dark.muted)
                    drawLine(Color(0xFF454B47), Offset(64f, 153f), Offset(216f, 153f), 1f)
                    text(state.secondaryName ?: "Second reading", 177f, 12f)
                    text(if (missing) "--" else state.secondaryValue ?: "--", 212f, 34f, bold = true)
                    text(state.secondaryUnit ?: "", 232f, 12f, EGaugeTokens.Dark.muted)
                } else {
                    text(state.name, 88f, 13f)
                    text(state.shownValue, 151f, 58f, if (state.condition == PreviewCondition.Normal) EGaugeTokens.Dark.gaugeText else accent, true)
                    text(state.unit, 178f, 14f, EGaugeTokens.Dark.muted)
                    when (state.layout) {
                        PreviewLayout.Bar -> {
                            drawRoundRect(Color(0xFF303730), Offset(66f, 201f), Size(148f, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
                            if (!missing) drawRoundRect(accent, Offset(66f, 201f), Size(90f, 6f), androidx.compose.ui.geometry.CornerRadius(3f))
                        }
                        PreviewLayout.Trend -> if (!missing) {
                            val path = Path().apply {
                                moveTo(62f, 216f); lineTo(82f, 207f); lineTo(99f, 210f); lineTo(120f, 195f)
                                lineTo(145f, 203f); lineTo(164f, 187f); lineTo(190f, 193f); lineTo(215f, 183f)
                            }
                            drawPath(path, accent, style = Stroke(2f))
                        }
                        else -> Unit
                    }
                    text(if (state.condition == PreviewCondition.Normal) "eGauge" else state.stateLabel,
                        239f, 11f, accent)
                }
            }
        }
        if (showDescription) Text("Preview · ${state.name} · ${state.shownValue} ${state.unit}" +
            if (state.condition == PreviewCondition.Normal) "" else " · ${state.stateLabel}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
