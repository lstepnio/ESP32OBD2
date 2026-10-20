package com.lstepnio.egauge.ui.car

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lstepnio.egauge.IdlePresetId
import com.lstepnio.egauge.PageAction
import com.lstepnio.egauge.PageActionType
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.ui.*
import com.lstepnio.egauge.ui.state.CarUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageActionsSheet(state: CarUiState, onDismiss: () -> Unit, onSave: (PageAction?) -> Unit) {
    var target by remember(state.activeId, state.actions) {
        mutableStateOf(state.actions.firstOrNull()?.pageId)
    }
    var menu by remember(state.activeId, state.actions) { mutableStateOf(state.actions.firstOrNull()?.type == PageActionType.VehicleActions) }
    var count by remember(state.activeId, state.actions) {
        mutableIntStateOf(state.actions.firstOrNull()?.count ?: 3)
    }
    var seconds by remember(state.activeId, state.actions) {
        mutableIntStateOf((state.actions.firstOrNull()?.windowMs ?: 5000) / 1000)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(
                .9f
            ).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Gesture action")
            Text("Swipe up to open vehicle actions or jump to a page. Vehicle commands need confirmation in the menu.")
            SettingsRow("Vehicle actions", if (menu) "Selected" else "Open Air up, Winch and ABS / traction control", state.canEditActions, {
                menu = true
                target = null
            })
            SettingsRow(
                "Off",
                if (target ==
                    null && !menu
                ) {
                    "Selected"
                } else {
                    "No gesture action"
                },
                state.canEditActions,
                {
                    target = null
                    menu = false
                }
            )
            state.actionPages.forEach { page ->
                SettingsRow(
                    page.name,
                    if (target ==
                        page.id
                    ) {
                        "Selected"
                    } else {
                        "Jump to this page"
                    },
                    state.canEditActions,
                    {
                        menu = false
                        target =
                            page.id
                    }
                )
            }
            if (target != null || menu) {
                Text("$count swipes up within $seconds seconds")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({
                        count--
                    }, enabled = state.canEditActions && count > 2) { Text("Fewer swipes") }
                    TextButton({
                        count++
                    }, enabled = state.canEditActions && count < 5) { Text("More swipes") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({
                        seconds--
                    }, enabled = state.canEditActions && seconds > 2) { Text("Less time") }
                    TextButton({
                        seconds++
                    }, enabled = state.canEditActions && seconds < 10) { Text("More time") }
                }
            }
            if (menu) IdleCoolantNotice(state.idleCoolantLimitC, state.measurementSystem)
            if (!state.actionsSupported || (menu && !state.commandsSupported)) {
                Text(
                    "You can save this choice now. Update the gauge before sending it.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PrimaryAction(
                "Save action",
                {
                    onSave(
                        if (menu) {
                            PageAction(
                                count = count,
                                windowMs = seconds * 1000,
                                type = PageActionType.VehicleActions,
                                airUpSeconds = state.idlePresets.firstOrNull { it.id == IdlePresetId.AirUp }?.seconds ?: 300,
                                winchSeconds = state.idlePresets.firstOrNull { it.id == IdlePresetId.Winch }?.seconds ?: 300
                            )
                        } else {
                            target?.let { PageAction(it, count, seconds * 1000) }
                        }
                    )
                    onDismiss()
                },
                enabled =
                    state.canEditActions &&
                        (target == null || state.actionPages.any { it.id == target })
            )
        }
    }
}
