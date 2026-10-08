package com.lstepnio.egauge

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule

/** Fixture/UI checks must never start real Bluetooth discovery as a side effect. */
fun activityTestRule(automaticConnection: Boolean = false) = AndroidComposeTestRule(
    activityRule = ActivityScenarioRule<MainActivity>(
        Intent(ApplicationProvider.getApplicationContext<Context>(), MainActivity::class.java)
            .putExtra("debug_reset_model",true).putExtra("debug_disable_auto_connect", !automaticConnection)),
    activityProvider = { rule ->
        lateinit var activity: MainActivity
        rule.scenario.onActivity { activity = it }
        activity
    },
)
