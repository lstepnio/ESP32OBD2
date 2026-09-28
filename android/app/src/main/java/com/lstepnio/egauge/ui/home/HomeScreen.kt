package com.lstepnio.egauge.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(state: HomeUiState, onPrimary: () -> Unit, onCustomize: () -> Unit, onDetails: () -> Unit,
               onEditPage: (Int) -> Unit = {}, statusInBanner: Boolean = false) {
    val pager = rememberPagerState(pageCount = { state.pages.size })
    val scope = rememberCoroutineScope()
    ScreenContent {
        ScreenTitle(state.gaugeName, trailing = { ConnectionPill(state.connection, state.connectionVerified) })
        ResponsivePanels(first = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalPager(pager, modifier = Modifier.fillMaxWidth().testTag("page-carousel")) { index ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RoundPreview(state.pages[index].preview, Modifier.widthIn(max = 280.dp).fillMaxWidth())
                    }
                }
                LazyRow(Modifier.fillMaxWidth().testTag("page-strip"), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)) {
                    state.pages.forEachIndexed { index, page ->
                        item(key = page.id) {
                            PageThumbnail(page, index, state.pages.size, pager.currentPage == index,
                                { scope.launch { pager.animateScrollToPage(index) } }, { onEditPage(index) })
                        }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumbnail(page: PageUi, index: Int, total: Int, selected: Boolean,
                          onSelect: () -> Unit, onEdit: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(shape = MaterialTheme.shapes.medium, color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, outline),
        modifier = Modifier.width(94.dp).combinedClickable(onClick = onSelect, onLongClick = onEdit,
            onLongClickLabel = "Edit ${page.name}").semantics(mergeDescendants = true) {
            contentDescription = "Page ${index + 1} of $total, ${page.name}. Tap to preview. Hold to edit."
            this.selected = selected
        }) {
        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            GaugeThumbnail(page)
            Text(page.name, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun GaugeThumbnail(page: PageUi) {
    val accent = EGaugeTokens.Dark.gaugeAccent
    androidx.compose.foundation.Canvas(Modifier.size(78.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 1.dp.toPx()
        drawCircle(EGaugeTokens.Dark.gauge, radius, center)
        drawCircle(Color(0xFF3A4039), radius, center, style = Stroke(1.dp.toPx()))
        when (page.layout) {
            com.lstepnio.egauge.GaugeLayout.Arc -> drawArc(accent, 138f, 152f, false,
                Offset(size.width * .12f, size.height * .12f), Size(size.width * .76f, size.height * .76f),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            com.lstepnio.egauge.GaugeLayout.Bar -> {
                drawRoundRect(Color(0xFF303730), Offset(size.width * .22f, size.height * .62f),
                    Size(size.width * .56f, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                drawRoundRect(accent, Offset(size.width * .22f, size.height * .62f),
                    Size(size.width * .36f, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            com.lstepnio.egauge.GaugeLayout.Trend -> {
                drawLine(accent, Offset(size.width * .22f, size.height * .63f), Offset(size.width * .4f, size.height * .51f), 2.dp.toPx())
                drawLine(accent, Offset(size.width * .4f, size.height * .51f), Offset(size.width * .57f, size.height * .58f), 2.dp.toPx())
                drawLine(accent, Offset(size.width * .57f, size.height * .58f), Offset(size.width * .77f, size.height * .4f), 2.dp.toPx())
            }
            com.lstepnio.egauge.GaugeLayout.Dual -> {
                drawLine(Color(0xFF454B47), Offset(size.width * .25f, size.height * .5f), Offset(size.width * .75f, size.height * .5f), 1.dp.toPx())
                drawCircle(accent, size.width * .055f, Offset(size.width / 2f, size.height * .35f))
                drawCircle(Color(0xFFF3F4EF), size.width * .055f, Offset(size.width / 2f, size.height * .65f))
            }
            com.lstepnio.egauge.GaugeLayout.Numeric -> drawCircle(accent, size.width * .09f, center)
        }
    }
}
