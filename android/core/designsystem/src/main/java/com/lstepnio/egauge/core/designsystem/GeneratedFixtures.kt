// Generated from design/fixtures/ui-states.json. Do not edit.
package com.lstepnio.egauge.core.designsystem

object ComponentFixtures {
    val all = listOf(
        ComponentFixture("default", "Ready to customize", "Choose the readings you want to see.", "2,840", StatusTone.Neutral),
        ComponentFixture("loading", "Sending to gauge", "Keep your gauge powered and nearby.", "2,840", StatusTone.Loading),
        ComponentFixture("disabled", "Not available yet", "You can still preview your display.", "--", StatusTone.Disabled),
        ComponentFixture("error", "Your changes were not sent", "Check your gauge, then try again.", "--", StatusTone.Error),
        ComponentFixture("success", "Saved & running on gauge", "Your gauge confirmed these settings.", "2,840", StatusTone.Success),
        ComponentFixture("stale", "Readings are out of date", "Check your adapter connection.", "--", StatusTone.Stale),
        ComponentFixture("offline", "Your gauge is offline", "Keep your gauge powered and reconnect.", "--", StatusTone.Offline),
        ComponentFixture("critical", "Coolant is too hot", "Stop safely and check your engine.", "118", StatusTone.Critical),
    )
}
