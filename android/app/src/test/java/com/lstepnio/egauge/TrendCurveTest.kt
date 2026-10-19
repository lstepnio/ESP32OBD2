package com.lstepnio.egauge

import androidx.compose.ui.geometry.Offset
import com.lstepnio.egauge.core.designsystem.trendSegments
import org.junit.Assert.*
import org.junit.Test

class TrendCurveTest {
    @Test fun curvedSegmentsPreserveEndpointsAndStayWithinEachSampleRange() {
        val points = listOf(Offset(0f, 15f), Offset(2f, 15.2f), Offset(3f, 15f), Offset(6f, 30f), Offset(7f, 0f), Offset(8f, 0f))
        val segments = trendSegments(points)
        assertEquals(points.size - 1, segments.size)
        segments.forEachIndexed { i, segment ->
            assertEquals(points[i], segment.start)
            assertEquals(points[i + 1], segment.end)
            for (step in 0..100) {
                val t = step / 100f
                val s = 1f - t
                val y = s * s * s * segment.start.y + 3 * s * s * t * segment.control1.y + 3 * s * t * t * segment.control2.y + t * t * t * segment.end.y
                assertTrue(y >= minOf(segment.start.y, segment.end.y) - .001f)
                assertTrue(y <= maxOf(segment.start.y, segment.end.y) + .001f)
            }
        }
    }

    @Test fun missingOrInvalidRunsNeverBecomeALine() {
        assertTrue(trendSegments(emptyList()).isEmpty())
        assertTrue(trendSegments(listOf(Offset(0f, 1f), Offset(0f, 2f))).isEmpty())
        assertTrue(trendSegments(listOf(Offset(0f, 1f), Offset(1f, Float.NaN))).isEmpty())
    }
}
