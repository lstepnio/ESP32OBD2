package com.lstepnio.egauge

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class AlertJourneyTest {
    @get:Rule val compose=activityTestRule()
    @Test fun offlineHistoryContextAndPrivateExportUseSharedPresentation() {
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish"))
        val model=(compose.activity.application as EGaugeApplication).model
        val repository=AlertRepository(compose.activity)
        val vehicle=model.profileCollection.activeId
        val event=AlertEvent(1,9,1,1000,0,AlertSeverity.Warning,false,AlertLifecycle.Active,1,false,108f,105f,"Coolant fixture","degC",1000,1,true)
        repository.ingest(vehicle,"fixture-gauge",AlertBatch(9,1,1,0,1,false,true,false,listOf(event)),"""{"baseRevision":0,"definitions":[]} """,null,System.currentTimeMillis())
        compose.runOnIdle { model.refreshAlertHistory() }
        compose.waitUntil(5000) { model.alertHistory.any { it.event.label=="Coolant fixture" } }
        val other=event.copy(sequence=2,episode=2,label="Other vehicle fixture")
        repository.ingest("other-car","other-gauge",AlertBatch(9,1,2,0,2,false,true,false,listOf(other)),"""{"baseRevision":0,"definitions":[]} """,null,System.currentTimeMillis())
        compose.runOnIdle { model.openAlertIntent(android.content.Intent().putExtra("alert_id",other.id("other-gauge")).putExtra("alert_vehicle","other-car").putExtra("alert_gauge","other-gauge")) }
        compose.waitUntil(5000) { model.notificationAlert?.event?.label=="Other vehicle fixture" }
        assertEquals(vehicle,model.profileCollection.activeId)
        compose.runOnIdle { model.refreshAlertHistory();model.selectedAlertId=null }
        compose.onNodeWithText("Car",useUnmergedTree=true).performClick()
        compose.onAllNodesWithText("Save report").assertCountEquals(0)
        compose.onAllNodesWithText("Delete history").assertCountEquals(0)
        compose.onNodeWithText("Alert history").performScrollTo().performClick()
        compose.onNodeWithText("Save report").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Coolant fixture") and hasAnyAncestor(hasTestTag("alert-history"))).performScrollTo().performClick()
        compose.onNode(hasText("Coolant fixture") and hasAnyAncestor(hasTestTag("alert-context"))).assertIsDisplayed()
        compose.onNodeWithText("Simulated alert. This does not establish a vehicle fault.").assertIsDisplayed()
        compose.onNodeWithText("Configured boundary:",substring=true).assertIsDisplayed()
        val uri=exportAlertReport(compose.activity,repository.list(vehicle),repository,vehicle)
        val zip=java.util.zip.ZipInputStream(compose.activity.contentResolver.openInputStream(uri)!!)
        assertEquals("alerts.json",zip.nextEntry.name)
        val manifest=JSONObject(zip.readBytes().toString(Charsets.UTF_8))
        assertEquals(1,manifest.getJSONArray("alerts").length())
        assertFalse(manifest.toString().contains("other-car"))
        assertFalse(manifest.toString().contains("fixture-gauge"))
        zip.close();repository.close()
    }
}
