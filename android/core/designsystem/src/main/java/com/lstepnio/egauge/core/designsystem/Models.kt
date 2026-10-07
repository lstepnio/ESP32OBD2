package com.lstepnio.egauge.core.designsystem

import androidx.compose.runtime.Immutable

/** Only presentation values belong here. No device or protocol objects. */
enum class StatusTone { Neutral, Loading, Disabled, Error, Success, Stale, Offline, Critical }
@Immutable
data class StatusUi(val title: String, val detail: String = "", val tone: StatusTone = StatusTone.Neutral)
@Immutable
data class DetailUi(val label: String, val value: String)
enum class PreviewLayout { Numeric, Arc, Bar, Trend, Dual }
enum class PreviewCondition { Normal, Warning, Critical, Stale, Offline }
@Immutable
data class ReadingPreviewUi(
    val name: String = "Engine speed", val value: String = "2,840", val unit: String = "rpm",
    val layout: PreviewLayout = PreviewLayout.Numeric, val condition: PreviewCondition = PreviewCondition.Normal,
    val secondaryName: String? = null, val secondaryValue: String? = null, val secondaryUnit: String? = null,
) {
    val shownValue: String get() = if (condition in setOf(PreviewCondition.Stale, PreviewCondition.Offline)) "--" else value
    val stateLabel: String get() = when (condition) {
        PreviewCondition.Normal -> "Example reading"
        PreviewCondition.Warning -> "Warning"
        PreviewCondition.Critical -> "Critical alert"
        PreviewCondition.Stale -> "No recent reading"
        PreviewCondition.Offline -> "Adapter offline"
    }
    val description: String get() = "Preview. $name, $shownValue $unit. $stateLabel." +
        if (layout == PreviewLayout.Dual) " ${secondaryName.orEmpty()}, ${if (shownValue == "--") "--" else secondaryValue.orEmpty()} ${secondaryUnit.orEmpty()}." else ""
}
