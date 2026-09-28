package com.lstepnio.egauge.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(state: HomeUiState, onPrimary: () -> Unit, onCustomize: () -> Unit, onDetails: () -> Unit,
               statusInBanner: Boolean = false) {
    val pager = rememberPagerState(pageCount = { state.pages.size })
    val scope = rememberCoroutineScope()
    ScreenContent {
        ScreenTitle(state.gaugeName, trailing = { ConnectionPill(state.connection, state.found) })
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalPager(pager, modifier = Modifier.fillMaxWidth().testTag("page-carousel")) { index ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RoundPreview(state.pages[index].preview, Modifier.widthIn(max = 280.dp).fillMaxWidth())
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.pages.forEachIndexed { index, page ->
                        FilterChip(selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            label = { Text("${index + 1}. ${page.name}") },
                            modifier = Modifier.semantics { contentDescription = "Page ${index + 1} of ${state.pages.size}, ${page.name}" })
                    }
                }
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
