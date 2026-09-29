package com.lstepnio.egauge.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.*
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.car.CarScreen
import com.lstepnio.egauge.ui.customize.*
import com.lstepnio.egauge.ui.expert.*
import com.lstepnio.egauge.ui.home.HomeScreen
import com.lstepnio.egauge.ui.settings.*
import com.lstepnio.egauge.ui.setup.SetupScreen
import com.lstepnio.egauge.ui.state.*

/** Debug-only examples. These render production screens with no device, repository or ViewModel. */
object ScreenFixtures {
    val names = listOf("setup", "pair", "gauge", "readings", "layouts", "limits", "review", "car", "updates", "recovery", "settings", "expert")
    private val details = listOf(DetailUi("Example identifier", "preview-gauge"),
        DetailUi("Example revision", "7"), DetailUi("Example SHA-256", "a".repeat(64)))
    private val pages = listOf(
        GaugePageDraft("one", "Engine speed", GaugeLayout.Arc, listOf("rpm")),
        GaugePageDraft("two", "Coolant temperature", GaugeLayout.Numeric, listOf("coolant")),
        GaugePageDraft("three", "Engine load", GaugeLayout.Bar, listOf("load")),
    ).map(::pageUi)
    private val customize = CustomizeUiState(pages = pages, editingPage = 0,
        readings = demoCatalog.filter { it.id != "tcm" }.map(::readingUi),
        alerts = listOf(alertUi(defaultAlert())), blockers = emptyList(), canSend = true, needsCheck = false,
        found = true, busy = false, editingEnabled = true, advanced = false,
        supportedLayouts = GaugeLayout.entries.toSet(), details = details)
    private val customizeActions = CustomizeActions(
        selectPage = {}, selectReading = {}, selectSecondary = {}, addPage = {}, removePage = {}, movePage = { _, _ -> },
        layout = {}, addAlert = {}, removeAlert = {}, warning = { _, _ -> }, critical = { _, _ -> },
        direction = { _, _ -> }, resetMargin = { _, _ -> }, trigger = { _, _ -> }, clear = { _, _ -> },
        check = {}, send = {}, setup = {})
    private val success = StatusUi("Saved & running on gauge", "Your gauge confirmed these settings.", StatusTone.Success)
    private val recovery = operationUi(OperationState(1, OperationKind.CONFIGURATION, OperationStage.OUTCOME_UNKNOWN,
        "Example", "Example disconnect", terminal = true))
    private val home = HomeUiState("eGauge", "Gauge ready", true, success, pages, false,
        "Customize", HomeAction.Customize, false, details)
    private val settings = SettingsUiState("eGauge", true, 0, true, false, false, false, "Example 1.0", details)
    private val car = CarUiState("My car", listOf(VehicleUi("one", "My car")), "one",
        StatusUi("Check-engine light is on", "Review the available codes.", StatusTone.Critical),
        listOf(FaultUi("P0301", faultDescription("P0301"), "Confirmed")), true, true, false, details)
    private val update = operationUi(OperationState(2, OperationKind.UPDATE, OperationStage.SENDING,
        "Example update", progressPercent = 64))
    private val updates = UpdatesUiState(update.status, "Example 1.1", "Example 1.0", false, false,
        true, true, false, details)
    private val expert = ExpertUiState(demoCatalog.map(::readingUi), true, true, true, false, details,
        "", "All", "rpm", "41 0C 2C 60", StatusUi("Example result: 2,840 rpm"), "01 0C", "ECM",
        StatusUi("Example request"), "Not run", false)
    private val expertActions = ExpertActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    @Composable
    fun Screen(name: String) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Text("Example · UI preview", Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.weight(1f)) {
                when (name) {
                    "setup", "pair" -> SetupScreen(SetupUiState(name == "pair", OwnerAccess.DISCOVERED, name == "setup",
                        emptyList(), StatusUi(if (name == "pair") "Gauge found" else "Looking for your gauge",
                            "Keep your gauge powered and nearby.", if (name == "setup") StatusTone.Loading else StatusTone.Neutral), details), {}, {}, {}, {}, {})
                    "gauge" -> HomeScreen(home, {}, {})
                    "recovery" -> HomeScreen(home.copy(status = recovery.status, pendingChanges = true,
                        primaryLabel = "Check gauge", primaryAction = HomeAction.Check), {}, {})
                    "readings", "layouts", "limits", "review" -> DashboardEditorScreen(customize,
                        mapOf("readings" to 0, "layouts" to 1, "limits" to 3, "review" to 5).getValue(name), {}, {}, customizeActions)
                    "car" -> CarScreen(car, {}, {}, {}, {})
                    "updates" -> UpdatesScreen(updates, update, true, {}, {}, {}, {}, {})
                    "settings" -> SettingsScreen(settings, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {})
                    "expert" -> ExpertScreen(expert, "", {}, {}, false, expertActions)
                    else -> error("Unknown screen fixture: $name")
                }
            }
        }
    }
}

class ScreenFixtureProvider : PreviewParameterProvider<String> {
    override val values = ScreenFixtures.names.asSequence()
}

@Preview(name = "Compact light", widthDp = 390, heightDp = 844, showBackground = true)
@Preview(name = "Compact dark", widthDp = 390, heightDp = 844, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Expanded light", widthDp = 1000, heightDp = 800, showBackground = true)
@Preview(name = "Expanded dark", widthDp = 1000, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text", widthDp = 390, heightDp = 844, fontScale = 2f, showBackground = true)
@Composable
private fun JourneyPreviews(@PreviewParameter(ScreenFixtureProvider::class) name: String) {
    EGaugeTheme { Surface(color = MaterialTheme.colorScheme.background) { ScreenFixtures.Screen(name) } }
}
