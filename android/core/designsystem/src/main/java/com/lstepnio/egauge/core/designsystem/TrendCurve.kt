package com.lstepnio.egauge.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path

/** Visual interpolation only. Callers split missing-data runs before constructing a curve. */
data class TrendSegment(val start: Offset, val control1: Offset, val control2: Offset, val end: Offset)

fun trendSegments(points: List<Offset>): List<TrendSegment> {
    if (points.size < 2 || points.any { !it.x.isFinite() || !it.y.isFinite() } ||
        points.zipWithNext().any { (a, b) -> a.x >= b.x }
    ) {
        return emptyList()
    }
    val slopes = points.zipWithNext { a, b -> (b.y - a.y) / (b.x - a.x) }
    fun tangent(i: Int): Float {
        if (i == 0) return slopes.first()
        if (i == points.lastIndex) return slopes.last()
        val before = slopes[i - 1]
        val after = slopes[i]
        return if (before * after <= 0f) 0f else 2f * before * after / (before + after)
    }
    return points.zipWithNext().mapIndexed { index, (a, b) ->
        val third = (b.x - a.x) / 3f
        val lower = minOf(a.y, b.y)
        val upper = maxOf(a.y, b.y)
        TrendSegment(
            a,
            Offset(a.x + third, (a.y + tangent(index) * third).coerceIn(lower, upper)),
            Offset(b.x - third, (b.y - tangent(index + 1) * third).coerceIn(lower, upper)),
            b
        )
    }
}

fun trendPath(points: List<Offset>): Path = Path().apply {
    val segments = trendSegments(points)
    segments.firstOrNull()?.let { moveTo(it.start.x, it.start.y) }
    segments.forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
}
