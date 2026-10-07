package com.lstepnio.egauge.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun toneColor(tone: StatusTone): Color = when (tone) {
    StatusTone.Error, StatusTone.Critical -> LocalSemanticColors.current.critical
    StatusTone.Stale, StatusTone.Offline -> LocalSemanticColors.current.warning
    StatusTone.Success -> LocalSemanticColors.current.success
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(EGaugeTokens.Radius.card.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun StatusCard(state: StatusUi, modifier: Modifier = Modifier) {
    val tone = toneColor(state.tone)
    Surface(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        if (state.tone in setOf(StatusTone.Error, StatusTone.Critical, StatusTone.Stale))
            liveRegion = LiveRegionMode.Polite
    }, shape = RoundedCornerShape(EGaugeTokens.Radius.card.dp), color = MaterialTheme.colorScheme.surface,
        border = if (state.tone in setOf(StatusTone.Error, StatusTone.Critical, StatusTone.Stale))
            BorderStroke(1.dp, tone.copy(alpha = .6f)) else null) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EGaugeIcon(when (state.tone) {
                StatusTone.Success -> GaugeIcon.Check
                StatusTone.Error, StatusTone.Critical, StatusTone.Stale -> GaugeIcon.Warning
                StatusTone.Loading -> GaugeIcon.Refresh
                StatusTone.Offline -> GaugeIcon.Bluetooth
                else -> GaugeIcon.Info
            }, color = tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleMedium)
                if (state.detail.isNotBlank()) Text(state.detail, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ConnectionPill(label: String, checked: Boolean, modifier: Modifier = Modifier,
    icon: GaugeIcon? = null, onClick: (() -> Unit)? = null, clickLabel: String = "Open updates") {
    Surface(modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button,
        onClickLabel = clickLabel, onClick = onClick) else Modifier), shape = CircleShape,
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EGaugeIcon(icon ?: if (checked) GaugeIcon.Check else GaugeIcon.Bluetooth,
                modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("primary-action"), enabled = enabled,
        shape = RoundedCornerShape(EGaugeTokens.Radius.pill.dp), contentPadding = PaddingValues(20.dp, 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

val LocalConnectionStatus = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = LocalConnectionStatus.current) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onBack != null) IconButton(onClick = onBack) { EGaugeIcon(GaugeIcon.Back, "Go back") }
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).semantics { heading() })
            if (!largeText) trailing?.invoke()
        }
        if (largeText) trailing?.invoke()
    }
}

@Composable
fun ReadingTile(name: String, unit: String, selected: Boolean, onClick: () -> Unit,
                modifier: Modifier = Modifier, enabled: Boolean = true, supporting: String? = null) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 64.dp)
        .semantics { this.selected = selected },
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(supporting ?: unit, style = MaterialTheme.typography.bodyMedium)
            }
            EGaugeIcon(if (selected) GaugeIcon.Check else GaugeIcon.Add)
        }
    }
}

@Composable
fun LimitField(label: String, value: String, unit: String, onChange: (String) -> Unit,
               enabled: Boolean = true, error: String? = null, hint: String? = null) {
    OutlinedTextField(value, onChange, label = { Text(label) }, suffix = { Text(unit) },
        enabled = enabled, singleLine = true, isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        supportingText = if (error != null || hint != null) ({ Text(error ?: hint.orEmpty()) }) else null,
        modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) })
}

@Composable
fun ProgressStepper(stages: List<String>, current: Int, progress: Int? = null, finished: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        stages.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    stateDescription = when { finished || index < current -> "Complete"; index == current -> "In progress"; else -> "Waiting" }
                }) {
                Surface(shape = CircleShape,
                    color = if (index <= current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        if (finished || index < current) EGaugeIcon(GaugeIcon.Check, modifier = Modifier.size(18.dp))
                        else Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(label, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("$progress% sent", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    StatusCard(StatusUi(title, detail, StatusTone.Disabled), modifier)
}

@Composable
fun SettingsRow(title: String, detail: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EGaugeIcon(GaugeIcon.Next)
        }
    }
}

@Composable
fun DetailContent(details: List<DetailUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        details.forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(field.label, style = MaterialTheme.typography.labelLarge)
                Text(field.value, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
