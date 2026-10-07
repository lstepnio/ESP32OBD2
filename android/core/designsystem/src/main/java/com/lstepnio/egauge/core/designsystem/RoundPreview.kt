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
import androidx.compose.ui.platform.LocalDensity
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
fun RoundPreview(state: ReadingPreviewUi, modifier: Modifier = Modifier, showDescription: Boolean = LocalDensity.current.fontScale > 1.3f) {
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
                drawCircle(EGaugeTokens.Dark.gaugeTrack, 138f, Offset(140f, 140f), style = Stroke(1.4f))
                val missing = state.condition in setOf(PreviewCondition.Stale, PreviewCondition.Offline)
                if (state.layout == PreviewLayout.Arc) {
                    drawArc(EGaugeTokens.Dark.gaugeTrack, 138f, 264f, false, Offset(19.5f, 19.5f), Size(241f, 241f), style = Stroke(13f, cap = StrokeCap.Round))
                    if (!missing) drawArc(accent, 138f, if (state.condition == PreviewCondition.Critical) 258f else 148f,
                        false, Offset(19.5f, 19.5f), Size(241f, 241f), style = Stroke(13f, cap = StrokeCap.Round))
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
                text("PREVIEW", 55f, 10f, EGaugeTokens.Dark.gaugeMuted)
                if (state.layout == PreviewLayout.Dual) {
                    text(state.name, 82f, 12f)
                    text(state.shownValue, 121f, 38f, accent, true)
                    text(state.unit, 141f, 12f, EGaugeTokens.Dark.gaugeMuted)
                    drawLine(EGaugeTokens.Dark.gaugeTrack, Offset(64f, 153f), Offset(216f, 153f), 1.5f)
                    text(state.secondaryName ?: "Second reading", 177f, 12f)
                    text(if (missing) "--" else state.secondaryValue ?: "--", 212f, 34f, bold = true)
                    text(state.secondaryUnit ?: "", 232f, 12f, EGaugeTokens.Dark.gaugeMuted)
                } else {
                    text(state.name, 88f, 13f)
                    text(state.shownValue, 151f, 58f, if (state.condition == PreviewCondition.Normal) EGaugeTokens.Dark.gaugeText else accent, true)
                    text(state.unit, 178f, 14f, EGaugeTokens.Dark.gaugeMuted)
                    when (state.layout) {
                        PreviewLayout.Bar -> {
                            drawRoundRect(EGaugeTokens.Dark.gaugeTrack, Offset(66f, 201f), Size(148f, 9f), androidx.compose.ui.geometry.CornerRadius(4.5f))
                            if (!missing) drawRoundRect(accent, Offset(66f, 201f), Size(90f, 9f), androidx.compose.ui.geometry.CornerRadius(4.5f))
                        }
                        PreviewLayout.Trend -> if (!missing) {
                            val path = Path().apply {
                                moveTo(62f, 216f); lineTo(82f, 207f); lineTo(99f, 210f); lineTo(120f, 195f)
                                lineTo(145f, 203f); lineTo(164f, 187f); lineTo(190f, 193f); lineTo(215f, 183f)
                            }
                            drawPath(path, accent, style = Stroke(3f, cap = StrokeCap.Round))
                        }
                        else -> Unit
                    }
                    text(if (state.condition == PreviewCondition.Normal) "eGauge" else state.stateLabel,
                        239f, 11f, accent)
                }
            }
        }
        if (showDescription) {
            val summary = "Preview · ${state.name} · ${state.shownValue} ${state.unit}" +
                (if (state.condition == PreviewCondition.Normal) "" else " · ${state.stateLabel}") +
                if (state.layout == PreviewLayout.Dual) "\n${state.secondaryName.orEmpty()} · " +
                    "${if (state.shownValue == "--") "--" else state.secondaryValue.orEmpty()} ${state.secondaryUnit.orEmpty()}" else ""
            // The canvas already provides one complete announcement, including the secondary reading.
            Text(summary, modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
