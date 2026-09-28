package com.lstepnio.egauge.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.*

@Composable
fun HomeScreen(state: HomeUiState, onPrimary: () -> Unit, onCustomize: () -> Unit, onDetails: () -> Unit,
               onEditPage: (Int) -> Unit = {}, statusInBanner: Boolean = false) {
    val pager = rememberPagerState(pageCount = { state.pages.size })
    ScreenContent {
        ScreenTitle(state.gaugeName, trailing = { ConnectionPill(state.connection, state.connectionVerified) })
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalPager(pager, modifier = Modifier.fillMaxWidth().testTag("page-carousel")) { index ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PreviewPage(state.pages[index], index, state.pages.size, onEditPage)
                    }
                }
                Text("Swipe between pages · Hold to edit", modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, second = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!statusInBanner) StatusCard(state.status)
                PrimaryAction(state.primaryLabel, onPrimary, enabled = !state.busy)
                if (state.primaryAction != HomeAction.Customize)
                    OutlinedButton(onCustomize, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Customize") }
                TextButton(onDetails, Modifier.fillMaxWidth()) { Text("Details") }
            }
        })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewPage(page: PageUi, index: Int, total: Int, onEdit: (Int) -> Unit) {
    Box(Modifier.widthIn(max = 280.dp).fillMaxWidth().combinedClickable(
        onClick = {}, onLongClick = { onEdit(index) }, onLongClickLabel = "Edit ${page.name}"
    ).semantics(mergeDescendants = true) {
        contentDescription = "Page ${index + 1} of $total, ${page.name}. Swipe to change page. Hold to edit."
    }) {
        RoundPreview(page.preview, Modifier.fillMaxWidth())
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface.copy(alpha = .9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Text("${index + 1}/$total", style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}
