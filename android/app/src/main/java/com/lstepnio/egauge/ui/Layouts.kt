package com.lstepnio.egauge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.core.designsystem.EGaugeTokens

@Composable
fun ScreenContent(scrollKey: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    key(scrollKey) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
        .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

@Composable
fun ResponsivePanels(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= (EGaugeTokens.Layout.paneBreakpoint - 120).dp && fontScale <= 1.3f) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { first(); second() }
    }
}
