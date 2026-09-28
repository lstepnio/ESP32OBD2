package com.lstepnio.egauge.core.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

@Immutable
data class ComponentFixture(val id: String, val title: String, val detail: String, val value: String, val tone: StatusTone)
class ComponentFixtureProvider : PreviewParameterProvider<ComponentFixture> {
    override val values = ComponentFixtures.all.asSequence()
}

/** The same public fixture renderer is used by previews and instrumented pixel goldens. */
@Composable
fun ComponentFixtureGallery(fixture: ComponentFixture) {
    val enabled = fixture.tone !in setOf(StatusTone.Disabled, StatusTone.Loading)
    val condition = when (fixture.tone) {
        StatusTone.Stale -> PreviewCondition.Stale
        StatusTone.Offline -> PreviewCondition.Offline
        StatusTone.Critical -> PreviewCondition.Critical
        else -> PreviewCondition.Normal
    }
    Column(Modifier.width(360.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ConnectionPill(if (fixture.tone == StatusTone.Offline) "Offline" else "Last checked", fixture.tone != StatusTone.Offline)
        StatusCard(StatusUi(fixture.title, fixture.detail, fixture.tone))
        RoundPreview(ReadingPreviewUi(value = fixture.value, layout = PreviewLayout.Arc, condition = condition),
            modifier = Modifier.width(232.dp), showDescription = false)
        ReadingTile("Engine speed", "rpm", fixture.tone == StatusTone.Success, {}, enabled = enabled)
        LimitEditor("Warn above", 105, false, {}, enabled)
        ProgressStepper(listOf("Sending", "Checking", "Done"),
            if (fixture.tone == StatusTone.Success) 2 else 0, finished = fixture.tone == StatusTone.Success)
        EmptyState("No car readings yet", "Check your adapter connection.")
        DetailContent(listOf(DetailUi("Reading", "Example · Engine speed"), DetailUi("Service / PID", "01 / 0C")))
        PrimaryAction("Send to gauge", {}, enabled = enabled)
    }
}

@Preview(name = "Dark states", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, heightDp = 1400)
@Preview(name = "Light states", showBackground = true, heightDp = 1400)
@Composable
private fun ComponentsPreview(@PreviewParameter(ComponentFixtureProvider::class) fixture: ComponentFixture) {
    EGaugeTheme { Surface { ComponentFixtureGallery(fixture) } }
}

class LayoutPreviewProvider : PreviewParameterProvider<ReadingPreviewUi> {
    override val values = PreviewLayout.entries.flatMap { layout ->
        PreviewCondition.entries.map { state -> ReadingPreviewUi(layout = layout, condition = state,
            secondaryName = "Coolant", secondaryValue = "92", secondaryUnit = "°C") }
    }.asSequence()
}

@Preview(name = "All layouts and conditions", showBackground = true)
@Composable
private fun GaugeStatesPreview(@PreviewParameter(LayoutPreviewProvider::class) state: ReadingPreviewUi) {
    EGaugeTheme { Surface { RoundPreview(state, Modifier.width(320.dp)) } }
}
