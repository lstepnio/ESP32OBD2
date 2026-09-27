package com.lstepnio.egauge

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CanvasColor = Color(0xFF0C1114)
private val SurfaceColor = Color(0xFF172025)
private val RaisedColor = Color(0xFF202C32)
private val TextColor = Color(0xFFF2F5EF)
private val MutedColor = Color(0xFFA9BABD)
private val AccentColor = Color(0xFFB7F36B)
private val WarningColor = Color(0xFFFFCB66)
private val CriticalColor = Color(0xFFFF7E79)

class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) readGauge()
        else model.connectionError("Nearby device permission is required to find the gauge")
    }
    private val wifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) model.installSelectedUpdate()
        else model.updateFailed("Nearby Wi-Fi permission is required for automatic fast transfer")
    }
    private val updatePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val bundle = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { DevUpdateBundle.read(this@MainActivity, it) }
                        ?: error("Could not open selected update package")
                }
                model.updatePackageLoaded(bundle)
            } catch (error: Exception) {
                model.updatePackageError(error.message ?: "Update package is invalid")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keepAwakeInDebug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (keepAwakeInDebug) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            SideEffect {
                if (keepAwakeInDebug || model.updateInProgress)
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AccentColor,
                    onPrimary = CanvasColor,
                    background = CanvasColor,
                    onBackground = TextColor,
                    surface = SurfaceColor,
                    onSurface = TextColor,
                    surfaceVariant = RaisedColor,
                    onSurfaceVariant = MutedColor,
                    error = CriticalColor,
                ),
            ) {
                CompanionApp(model, onFindGauge = ::requestGauge, onSelectReading = ::requestSelection,
                    onReadSaved = ::requestSavedSnapshot, onRotate = ::requestRotation,
                    onApplyNumeric = ::requestNumericConfiguration,
                    onReadConfig = ::requestActiveConfiguration,
                    onReadDocument = ::requestActiveDocument,
                    onReadDiagnostics = ::requestDiagnostics,
                    onReadBootIdentity = ::requestBootIdentity,
                    onInstallUpdate = ::requestInstallUpdate,
                    onSelectUpdate = { updatePicker.launch(arrayOf("application/zip", "application/octet-stream")) })
            }
        }
    }

    private fun requestGauge() {
        val required = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (required.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) readGauge()
        else permissionLauncher.launch(required)
    }

    private fun readGauge() {
        model.discoverGauge()
    }

    private fun requestSelection() {
        if (Build.VERSION.SDK_INT >= 31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            model.selectionError("Grant nearby device permission, then try again")
            return
        }
        model.selectReadingOnGauge()
    }

    private fun requestRotation(rotation: Int) {
        model.rotateGauge(rotation)
    }

    private fun requestSavedSnapshot() {
        model.readSavedGauge()
    }

    private fun requestNumericConfiguration() {
        model.sendNumericConfiguration()
    }

    private fun requestActiveConfiguration() {
        model.readConfiguration()
    }

    private fun requestActiveDocument() {
        model.readConfigurationDocument()
    }

    private fun requestDiagnostics() {
        model.readGaugeDiagnostics()
    }

    private fun requestBootIdentity() {
        model.readRunningFirmware()
    }

    private fun requestInstallUpdate() {
        val permission = when {
            model.capabilities?.wifiBulk == null -> null
            Build.VERSION.SDK_INT >= 33 -> Manifest.permission.NEARBY_WIFI_DEVICES
            else -> Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (permission != null && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            wifiPermissionLauncher.launch(permission)
        else model.installSelectedUpdate()
    }
}

@Composable
private fun CompanionApp(model: AppViewModel, onFindGauge: () -> Unit,
                         onSelectReading: () -> Unit, onReadSaved: () -> Unit,
                         onRotate: (Int) -> Unit, onApplyNumeric: () -> Unit,
                         onReadConfig: () -> Unit, onReadDocument: () -> Unit,
                         onReadDiagnostics: () -> Unit,
                         onReadBootIdentity: () -> Unit,
                         onInstallUpdate: () -> Unit,
                         onSelectUpdate: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Row(Modifier.fillMaxSize().background(CanvasColor)) {
                NavigationRail(containerColor = SurfaceColor) {
                    Spacer(Modifier.height(28.dp))
                    Destination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = model.destination == destination,
                            onClick = { model.navigate(destination) },
                            icon = { Text(destination.glyph, fontSize = 23.sp) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                AppBody(model, onFindGauge, onSelectReading, onReadSaved, onRotate, onApplyNumeric,
                    onReadConfig, onReadDocument, onReadDiagnostics, onReadBootIdentity, onInstallUpdate, onSelectUpdate, Modifier.weight(1f))
            }
        } else {
            Scaffold(containerColor = CanvasColor, bottomBar = {
                NavigationBar(containerColor = SurfaceColor) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = model.destination == destination,
                            onClick = { model.navigate(destination) },
                            icon = { Text(destination.glyph, fontSize = 22.sp) },
                            label = { Text(destination.label, maxLines = 1) },
                        )
                    }
                }
            }) { padding -> AppBody(model, onFindGauge, onSelectReading, onReadSaved, onRotate,
                onApplyNumeric, onReadConfig, onReadDocument, onReadDiagnostics, onReadBootIdentity, onInstallUpdate, onSelectUpdate, Modifier.padding(padding)) }
        }
    }
}

@Composable
private fun AppBody(model: AppViewModel, onFindGauge: () -> Unit,
                    onSelectReading: () -> Unit, onReadSaved: () -> Unit,
                    onRotate: (Int) -> Unit, onApplyNumeric: () -> Unit,
                    onReadConfig: () -> Unit, onReadDocument: () -> Unit,
                    onReadDiagnostics: () -> Unit,
                    onReadBootIdentity: () -> Unit,
                    onInstallUpdate: () -> Unit,
                    onSelectUpdate: () -> Unit,
                    modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().fillMaxHeight().padding(horizontal = 20.dp)) {
            Header(model)
            if (model.operation.stage != OperationStage.IDLE) OperationBanner(model.operation)
            when (model.destination) {
                Destination.Gauge -> DesignScreen(model, onApplyNumeric, onReadDocument)
                Destination.Readings -> PidsScreen(model)
                Destination.Vehicle -> GarageScreen(model, onFindGauge)
                Destination.Settings -> DeviceScreen(model, onFindGauge, onSelectReading, onReadSaved,
                    onRotate, onReadConfig, onReadDocument, onReadDiagnostics, onReadBootIdentity, onInstallUpdate, onSelectUpdate)
            }
        }
    }
}

@Composable
private fun Header(model: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(AccentColor),
            contentAlignment = Alignment.Center,
        ) { Text("e", color = CanvasColor, fontWeight = FontWeight.Black, fontSize = 25.sp) }
        Spacer(Modifier.width(10.dp))
        Text("eGauge", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextColor)
        Spacer(Modifier.weight(1f))
        val operationActive = model.operation.stage != OperationStage.IDLE && !model.operation.terminal
        StatusPill(when {
            operationActive -> model.operation.stage.name.replace('_', ' ')
            model.capabilities != null -> "GAUGE FOUND"
            else -> "OFFLINE PREVIEW"
        }, if (model.capabilities != null && !operationActive) AccentColor else WarningColor)
    }
}

@Composable
private fun StatusPill(label: String, tint: Color) {
    Text(
        label,
        color = tint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(100.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun Intro(kicker: String, title: String, supporting: String) {
    Text(kicker.uppercase(), color = AccentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Text(title, color = TextColor, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(supporting, color = MutedColor, fontSize = 16.sp, lineHeight = 23.sp)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceColor,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, RaisedColor),
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
private fun SectionHeading(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextColor,
        modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OperationBanner(state: OperationState) {
    val tint = when (state.stage) {
        OperationStage.FAILED, OperationStage.OUTCOME_UNKNOWN -> CriticalColor
        OperationStage.RECOVERED -> WarningColor
        OperationStage.ACTIVE -> AccentColor
        else -> WarningColor
    }
    Panel(Modifier.padding(bottom = 14.dp)) {
        Text(state.title, color = tint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        state.detail?.let { Text(it, color = TextColor, fontSize = 14.sp, lineHeight = 20.sp) }
        state.progressPercent?.let { Text("$it%", color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun GarageScreen(model: AppViewModel, onFindGauge: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Intro("Vehicle", model.profileCollection.active.name,
            "Manage this vehicle profile, its gauge, and its OBD connection.")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(AccentColor.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center) { Text("◉", color = AccentColor, fontSize = 24.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("eGauge display", color = TextColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(model.deviceMessage, color = MutedColor, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(onClick = onFindGauge, enabled = !model.scanning,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text(if (model.scanning) "Finding gauge…" else "Find nearby gauge")
            }
            GaugeCandidateChooser(model)
        }
        Spacer(Modifier.height(16.dp))
        Panel {
            SectionHeading("Local vehicle profiles")
            Text("Keep dashboard drafts separate for each vehicle. These names do not confirm PID support.",
                color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
            model.profileError?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text("Saved profiles could not be opened: $error. Editing is paused to protect them.",
                    color = CriticalColor, fontSize = 13.sp, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                model.profileCollection.profiles.forEach { profile ->
                    FilterChip(
                        selected = profile.id == model.profileCollection.activeId,
                        onClick = { model.selectProfile(profile.id) },
                        enabled = model.profileError == null,
                        label = { Text(profile.name, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = model.profileNameInput, onValueChange = model::editProfileName,
                label = { Text("New profile name") }, singleLine = true,
                enabled = model.profileError == null && model.profileCollection.profiles.size < 8,
                modifier = Modifier.fillMaxWidth())
            val duplicateName = model.profileCollection.profiles.any {
                it.name.equals(model.profileNameInput.trim(), ignoreCase = true)
            }
            if (duplicateName) Text("A profile with this name already exists.",
                color = WarningColor, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = model::createProfile,
                enabled = model.profileError == null && model.profileNameInput.trim().isNotEmpty() &&
                    !duplicateName && model.profileCollection.profiles.size < 8) { Text("Create local profile") }
        }
        Spacer(Modifier.height(16.dp))
        Panel {
            StatusPill("LOCAL DRAFT", AccentColor)
            Spacer(Modifier.height(14.dp))
            Text("${model.profileCollection.active.name} dashboard", color = TextColor,
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Edits stay on this phone until you review and send a supported setup to the gauge.",
                color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { model.navigate(Destination.Gauge) }) { Text("Edit gauge") }
        }
        Spacer(Modifier.height(24.dp))
        SectionHeading("Vehicle connection")
        SourceRow("OBD", "Vehicle adapter", "Adapter setup will use this profile when discovery is available")
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { model.showAdvancedConnections(!model.advancedConnectionsOpen) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (model.advancedConnectionsOpen) "Hide advanced connections" else "Advanced connections")
        }
        if (model.advancedConnectionsOpen) {
            Spacer(Modifier.height(10.dp))
            Panel {
                Text("Second OBD adapter", color = TextColor, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold)
                Text("For swapped vehicles with separate engine and transmission interfaces. Enabling this keeps source bindings separate; simultaneous operation still requires compatible gauge firmware and hardware validation.",
                    color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(10.dp))
                FilterChip(
                    selected = model.profileCollection.active.secondAdapterEnabled,
                    onClick = { model.setSecondAdapterEnabled(!model.profileCollection.active.secondAdapterEnabled) },
                    label = { Text(if (model.profileCollection.active.secondAdapterEnabled)
                        "Second adapter enabled" else "Enable second adapter") },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(source: String, title: String, detail: String) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(source, color = AccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp))
            Column {
                Text(title, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(detail, color = MutedColor, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DesignScreen(model: AppViewModel, onApplyNumeric: () -> Unit,
                         onReadDocument: () -> Unit) {
    val pid = demoCatalog.first { it.id == model.draft.pidId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Intro("Gauge", "Build a glanceable dashboard.",
            "Editing ${model.profileCollection.active.name}. Preview values are examples until vehicle data is observed.")
        if (model.profileError != null) {
            Panel {
                Text("Local profile editing paused", color = CriticalColor,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("The saved profile format could not be opened. Your data has not been replaced.",
                    color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
            }
            return@Column
        }
        RoundPreview(pid, model.draft.layout)
        Spacer(Modifier.height(22.dp))
        SectionHeading("Layout")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GaugeLayout.entries.forEach { layout ->
                FilterChip(
                    selected = model.draft.layout == layout,
                    onClick = { model.selectLayout(layout) },
                    label = { Text(if (layout == GaugeLayout.Numeric) layout.label else "${layout.label} preview",
                        fontSize = 14.sp, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionHeading("Primary reading")
        Panel {
            Text(pid.name, color = TextColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("${pid.unit} • Example only; vehicle support has not been observed",
                color = MutedColor, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = { model.navigate(Destination.Readings) }) { Text("Choose a reading") }
        }
        Spacer(Modifier.height(22.dp))
        SectionHeading("Coolant alert preview")
        Panel {
            Text("Engine coolant • ECM • °C", color = MutedColor, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            ThresholdRow("Warning", model.draft.warning, WarningColor,
                onDecrease = { model.setWarning((model.draft.warning - 1).coerceAtLeast(-40)) },
                onIncrease = { model.setWarning((model.draft.warning + 1).coerceAtMost(215)) })
            Spacer(Modifier.height(10.dp))
            ThresholdRow("Critical", model.draft.critical, CriticalColor,
                onDecrease = { model.setCritical((model.draft.critical - 1).coerceAtLeast(-40)) },
                onIncrease = { model.setCritical((model.draft.critical + 1).coerceAtMost(215)) })
            Spacer(Modifier.height(12.dp))
            Text("Hysteresis", color = TextColor, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 2, 3, 5).forEach { value ->
                    FilterChip(selected = model.draft.hysteresis == value,
                        onClick = { model.setHysteresis(value) }, label = { Text("$value °C") })
                }
            }
            Text("Alert after", color = TextColor, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 1000, 2000, 5000).forEach { value ->
                    FilterChip(selected = model.draft.triggerDwellMs == value,
                        onClick = { model.setTriggerDwell(value) }, label = { Text("${value / 1000} s") })
                }
            }
            Text("Clear after", color = TextColor, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1000, 2000, 5000, 10000).forEach { value ->
                    FilterChip(selected = model.draft.clearDwellMs == value,
                        onClick = { model.setClearDwell(value) }, label = { Text("${value / 1000} s") })
                }
            }
            val limitsValid = model.draft.warning in -40..215 &&
                model.draft.critical in -40..215 && model.draft.warning < model.draft.critical &&
                model.draft.warning - model.draft.hysteresis >= -40 &&
                model.draft.hysteresis < model.draft.critical - model.draft.warning
            val sent = model.sentProfileId == model.profileCollection.activeId &&
                model.sentDraft == model.draft && model.activeConfigRevision != null
            Text(if (limitsValid && model.activeDocument != null)
                "Compare this phone draft with the last verified gauge readback below. Alert action awaits live coolant data."
                else if (limitsValid && sent)
                "Numeric pages and coolant thresholds were sent in revision ${model.activeConfigRevision}; the renderer preview stays local. Read back the gauge to compare."
                else if (limitsValid) "Saved in local draft; current settings have not been sent to the gauge"
                else "Check warning, critical and hysteresis spacing",
                color = if (limitsValid) MutedColor else CriticalColor, fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        Panel {
            SectionHeading("Phone draft vs gauge")
            Text("Compare the current local profile with the last authenticated document readback. Editing the draft updates this comparison without writing to the gauge.",
                color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onReadDocument,
                enabled = !model.scanning && model.capabilities?.experimentalNumericConfig == true) {
                Text(if (model.scanning) "Reading gauge…" else "Refresh saved configuration")
            }
            model.documentMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, color = if (model.documentReadFailed) WarningColor else MutedColor,
                    fontSize = 13.sp, lineHeight = 19.sp)
            }
            val comparison = model.draftComparison
            if (comparison == null) {
                Spacer(Modifier.height(12.dp))
                Text("No verified document loaded. Read the gauge to compare saved settings.",
                    color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
            } else {
                Spacer(Modifier.height(14.dp))
                Text("Revision ${comparison.revision} • ${comparison.matchingCount} match • " +
                    "${comparison.differingCount} differ • ${comparison.unknownCount} unavailable",
                    color = when {
                        comparison.differingCount > 0 -> WarningColor
                        comparison.unknownCount > 0 -> MutedColor
                        else -> AccentColor
                    },
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                comparison.fields.forEach { field ->
                    Spacer(Modifier.height(14.dp))
                    Text(field.label, color = TextColor, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("PHONE DRAFT", color = MutedColor, fontSize = 10.sp)
                            Text(field.phone, color = TextColor, fontSize = 13.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("GAUGE SAVED", color = MutedColor, fontSize = 10.sp)
                            Text(field.gauge ?: "Unavailable", color = when (field.matches) {
                                true -> AccentColor
                                false -> WarningColor
                                null -> MutedColor
                            }, fontSize = 13.sp)
                        }
                    }
                    Text(when (field.matches) {
                        true -> "Matches"
                        false -> "Different"
                        null -> "Unavailable for comparison"
                    }, color = when (field.matches) {
                        true -> AccentColor
                        false -> WarningColor
                        null -> MutedColor
                    }, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text("A match describes saved settings only. It does not confirm a live vehicle value or PID support.",
                    color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = model::adoptGaugeDraft,
                    enabled = model.canAdoptGaugeDraft && !model.scanning) {
                    Text("Use saved settings in phone draft")
                }
                if (!model.canAdoptGaugeDraft) {
                    Text("Import is available when the saved profile and all mapped editor fields are compatible.",
                        color = MutedColor, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        val projectionBlockers = ConfigurationProjector.blockers(model.draft)
        Panel {
            SectionHeading("Review what will be sent")
            Text("The current sender installs three Numeric pages in this order:",
                color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
            val ordered = when (model.draft.pidId) {
                "coolant" -> listOf("Coolant temperature", "Engine RPM", "Vehicle speed")
                "speed" -> listOf("Vehicle speed", "Coolant temperature", "Engine RPM")
                else -> listOf("Engine RPM", "Coolant temperature", "Vehicle speed")
            }
            ordered.forEachIndexed { index, label ->
                Text("${index + 1}. $label • Numeric", color = TextColor, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Coolant warning ${model.draft.warning} °C • critical ${model.draft.critical} °C",
                color = TextColor, fontSize = 14.sp)
            if (projectionBlockers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                projectionBlockers.forEach { reason ->
                    Text(reason, color = WarningColor, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
        val numericReady = model.capabilities?.experimentalNumericConfig == true && !model.scanning &&
            model.profileError == null && projectionBlockers.isEmpty() &&
            model.activeConfigRevision != null && model.verifiedConfigHash != null
        Spacer(Modifier.height(12.dp))
        Button(onClick = onApplyNumeric, enabled = numericReady,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (model.scanning) "Sending setup…" else "Review complete • Send numeric pages")
        }
        Text(when {
            model.capabilities == null -> "Find the gauge in Vehicle before sending."
            model.activeConfigRevision == null || model.verifiedConfigHash == null ->
                "Refresh saved configuration before sending so changes cannot overwrite a newer revision."
            projectionBlockers.isNotEmpty() -> "Adjust the items above before sending. Preview-only layouts stay on this phone."
            else -> "Requires the paired owner. The app confirms the saved setup is healthy and running after restart."
        }, color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun ThresholdRow(label: String, value: Int, tint: Color, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = tint, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = onDecrease, modifier = Modifier.size(48.dp)
                .semantics { contentDescription = "Decrease coolant ${label.lowercase()} temperature" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−", fontSize = 20.sp) }
        Text("$value°", color = TextColor, modifier = Modifier.width(58.dp),
            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onIncrease, modifier = Modifier.size(48.dp)
                .semantics { contentDescription = "Increase coolant ${label.lowercase()} temperature" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
private fun RoundPreview(pid: PidExample, layout: GaugeLayout) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(238.dp).clip(CircleShape).background(Color(0xFF060A0C))
                .border(2.dp, RaisedColor, CircleShape)
                .semantics { contentDescription =
                    "Simulated ${pid.name}: ${pid.demoValue} ${pid.unit}, ${layout.label} layout" },
            contentAlignment = Alignment.Center,
        ) {
            if (layout == GaugeLayout.Arc) {
                Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                    drawArc(RaisedColor, 145f, 250f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(AccentColor, 145f, 160f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Text(pid.gaugeLabel, color = MutedColor, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).width(166.dp))
            Text(pid.demoValue, color = TextColor,
                fontSize = if (pid.demoValue.length > 4) 42.sp else 48.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 77.dp).width(166.dp))
            Text(pid.unit, color = AccentColor, fontSize = 17.sp,
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 141.dp).width(140.dp))
            if (layout == GaugeLayout.Bar) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 169.dp)
                    .width(120.dp).height(6.dp).clip(CircleShape).background(RaisedColor)) {
                    Box(Modifier.fillMaxWidth(.62f).height(6.dp).background(AccentColor))
                }
            }
            if (layout == GaugeLayout.Dual) {
                Text("92 °C  /  coolant", color = MutedColor, fontSize = 13.sp,
                    textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 165.dp).width(166.dp))
            }
            if (layout == GaugeLayout.Trend) {
                Canvas(Modifier.align(Alignment.TopCenter).padding(top = 158.dp)
                    .width(110.dp).height(25.dp)) {
                    val points = listOf(.72f, .55f, .61f, .35f, .40f, .23f)
                    for (i in 0 until points.lastIndex)
                        drawLine(AccentColor,
                            Offset(size.width * i / points.lastIndex, size.height * points[i]),
                            Offset(size.width * (i + 1) / points.lastIndex, size.height * points[i + 1]),
                            strokeWidth = 2.dp.toPx())
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 19.dp)) {
                StatusPill("DEMO", WarningColor)
            }
        }
    }
}

@Composable
private fun PidsScreen(model: AppViewModel) {
    val results = demoCatalog.filter { pid ->
        (model.advancedReadingsOpen || pid.source != "TCM") &&
            (model.sourceFilter == "All" || pid.source == model.sourceFilter) &&
            (model.query.isBlank() || "${pid.name} ${pid.request} ${pid.category} ${pid.source}"
                .contains(model.query.trim(), ignoreCase = true))
    }
    Column(Modifier.fillMaxSize()) {
        Intro("Readings", "Choose familiar vehicle data.",
            "Search by name or category. Every result says whether it is an example or observed on your vehicle.")
        OutlinedTextField(value = model.query, onValueChange = model::search,
            label = { Text("Search readings") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { model.showAdvancedReadings(!model.advancedReadingsOpen) },
            modifier = Modifier.fillMaxWidth()) {
            Text(if (model.advancedReadingsOpen) "Hide technical tools" else "Technical details and custom PIDs")
        }
        if (model.advancedReadingsOpen) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "ECM", "TCM").forEach { source ->
                    FilterChip(selected = model.sourceFilter == source,
                        onClick = { model.filter(source) }, label = { Text(source) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { model.showDiscoveryPreview(!model.discoveryPreview) },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (model.discoveryPreview) "Hide discovery preview" else "Preview discovery states")
            }
            if (model.discoveryPreview) {
                Spacer(Modifier.height(8.dp))
                Panel {
                    Text("Discovery state preview", color = TextColor,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Simulated path only. Real evidence requires a vehicle profile, adapter, ECU source and observation time.",
                        color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Current vehicle evidence: none", color = WarningColor, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { model.showLab(!model.labOpen) },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (model.labOpen) "Close decoder lab" else "Open decoder lab")
            }
        }
        if (model.advancedReadingsOpen && model.labOpen) {
            Spacer(Modifier.height(10.dp))
            Panel {
                Text("Mode 01 response lab", color = TextColor,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Example decoder for ${demoCatalog.first { it.id == model.draft.pidId }.name}. Pasting bytes never sends a vehicle request.",
                    color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = model.labInput, onValueChange = model::editLabInput,
                    label = { Text("Response bytes") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                when (val result = decodeExample(demoCatalog.first { it.id == model.draft.pidId }, model.labInput)) {
                    is DecodeResult.Value -> Text(result.display, color = AccentColor,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    is DecodeResult.Error -> Text(result.message, color = WarningColor,
                        fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (model.advancedReadingsOpen) OutlinedButton(onClick = { model.showCustomLab(!model.customLabOpen) },
            modifier = Modifier.fillMaxWidth()) {
            Text(if (model.customLabOpen) "Close custom request lab" else "Draft a custom read request")
        }
        if (model.advancedReadingsOpen && model.customLabOpen) {
            Spacer(Modifier.height(10.dp))
            Panel {
                Text("Offline request check", color = TextColor,
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Choose an ECU and enter a read request. This checks syntax only; vehicle support and decoding remain unknown.",
                    color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ECM", "TCM").forEach { source ->
                        FilterChip(selected = model.customSource == source,
                            onClick = { model.selectCustomSource(source) }, label = { Text(source) })
                    }
                }
                OutlinedTextField(value = model.customRequestInput,
                    onValueChange = model::editCustomRequest,
                    label = { Text("Read request bytes") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                when (val preview = previewReadRequest(model.customRequestInput)) {
                    is ReadRequestPreview.Valid -> {
                        Text("${preview.description} • ${model.customSource}",
                            color = AccentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Request ${preview.request}  →  Expected prefix ${preview.responsePrefix}",
                            color = TextColor, fontSize = 13.sp, lineHeight = 19.sp)
                        Text("Local draft only. No adapter was queried.",
                            color = WarningColor, fontSize = 12.sp)
                    }
                    is ReadRequestPreview.Invalid -> Text(preview.reason,
                        color = WarningColor, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${results.size} catalog examples • ${model.vehicleObservations.size} vehicle observations",
            color = MutedColor, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
            items(results, key = { it.id }) { pid ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pid.name, color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("${pid.source} • ${pid.category} • ${pid.request}", color = MutedColor, fontSize = 13.sp)
                        }
                        Text(pid.unit, color = AccentColor, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Catalog: ${pid.exampleKind}  •  Vehicle support: unknown",
                        color = WarningColor, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { model.selectPid(pid); model.navigate(Destination.Gauge) },
                        enabled = model.profileError == null) {
                        Text("Use on gauge draft")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceScreen(model: AppViewModel, onFindGauge: () -> Unit,
                         onSelectReading: () -> Unit, onReadSaved: () -> Unit,
                         onRotate: (Int) -> Unit, onReadConfig: () -> Unit,
                         onReadDocument: () -> Unit,
                         onReadDiagnostics: () -> Unit, onReadBootIdentity: () -> Unit,
                         onInstallUpdate: () -> Unit,
                         onSelectUpdate: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Intro("Settings", "Gauge connection and maintenance.",
            "Manage owner access, display rotation, firmware, and technical details.")
        Panel {
            SectionHeading("Connection")
            Text(model.deviceMessage, color = MutedColor, fontSize = 15.sp)
            val caps = model.capabilities
            if (caps != null) {
                Spacer(Modifier.height(14.dp))
                InfoLine("Owner controls", when {
                    caps.experimentalNumericConfig -> "Experimental numeric ECM profile transfer"
                    caps.displayRotationWrite -> "Built-in reading and display rotation"
                    caps.quickSelect -> "Built-in reading only"
                    else -> "Read only"
                })
                InfoLine("Owner access", when (model.ownerAccess) {
                    OwnerAccess.UNKNOWN -> "Not checked"
                    OwnerAccess.DISCOVERED -> "Checked when a protected action starts"
                    OwnerAccess.AUTHENTICATED -> "Authenticated on this connection"
                })
                model.savedGauge?.let { saved ->
                    Spacer(Modifier.height(12.dp))
                    InfoLine("Saved reading", listOf("RPM", "Speed", "Engine load", "Coolant", "Fuel")[saved.readingIndex])
                    InfoLine("Saved revision", saved.revision.toString())
                    InfoLine("Display rotation", "${saved.rotation * 90}°")
                    Text("This is the gauge's saved selection. Edits in Gauge remain on this phone until sent.",
                        color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { model.showTechnicalDetails(!model.technicalDetailsOpen) }) {
                    Text(if (model.technicalDetailsOpen) "Hide technical details" else "Technical details")
                }
                if (model.technicalDetailsOpen) {
                    Spacer(Modifier.height(10.dp))
                    InfoLine("Board", caps.board)
                    InfoLine("Protocol", caps.protocolMajor.toString())
                    InfoLine("Adapter links", "${caps.maxAdapterLinks}; simultaneous use ${if (caps.simultaneousVerified) "verified" else "unverified"}")
                    InfoLine("Firmware transfer", if (caps.wifiBulk != null)
                        "Automatic private Wi-Fi" else "Bluetooth")
                    if (caps.wifiBulk == "experimental-softap-aead-v2") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = model::runWifiTransportSecurityCheck,
                            enabled = !model.scanning) {
                            Text("Run Wi-Fi security self-check")
                        }
                        Text(model.wifiSecurityMessage, color = MutedColor,
                            fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    model.runtimeIdentity?.let { runtime ->
                        InfoLine("Running revision", runtime.revision.toString())
                        InfoLine("Stored revision", runtime.storedRevision.toString())
                        InfoLine("Config SHA-256", runtime.sha256)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onFindGauge, enabled = !model.scanning) {
                Text(if (model.scanning) "Finding gauge…" else "Find or refresh gauge")
            }
            GaugeCandidateChooser(model)
            if (caps?.experimentalNumericConfig == true) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onReadConfig, enabled = !model.scanning) {
                    Text("Check running setup")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onReadDocument, enabled = !model.scanning) {
                    Text("Refresh saved setup")
                }
                model.activeDocument?.let { document ->
                    Spacer(Modifier.height(12.dp))
                    InfoLine("Document revision", document.revision.toString())
                    InfoLine("Document bytes", document.length.toString())
                    InfoLine("Profile ID", document.vehicleProfileId)
                    InfoLine("Definitions", document.definitionCount.toString())
                    InfoLine("Pages", document.pageCount.toString())
                    InfoLine("Alerts", document.alertCount.toString())
                    Text("Read from the gauge and SHA-256 verified. This does not establish vehicle PID support.",
                        color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { model.navigate(Destination.Gauge) }) {
                        Text("Review changes in Gauge")
                    }
                }
            }
            if (caps?.quickSelect == true) {
                Spacer(Modifier.height(10.dp))
                Text("First setup: long press the gauge to open pairing, then enter its code in Android. A 12-second hold resets the owner bond.",
                    color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                OutlinedButton(onClick = onSelectReading,
                    enabled = !model.scanning && model.profileError == null && model.draft.pidId != "tcm") {
                    Text(if (model.scanning) "Connecting…" else if (model.draft.pidId == "tcm")
                        "No built-in TCM reading" else "Set preview reading on gauge")
                }
                if (caps.savedStateRead) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReadSaved, enabled = !model.scanning) {
                        Text("Read saved gauge state")
                    }
                    if (caps.displayRotationWrite && model.savedGauge != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Display rotation", color = TextColor, fontSize = 14.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0..3).forEach { rotation ->
                                FilterChip(selected = model.savedGauge?.rotation == rotation,
                                    onClick = { onRotate(rotation) },
                                    enabled = !model.scanning,
                                    label = { Text("${rotation * 90}°") })
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Panel {
            SectionHeading("Check-engine and fault information")
            val diagnostics = model.diagnostics
            val diagnosticsCurrent = model.diagnosticsCurrent()
            StatusPill(when {
                diagnosticsCurrent && diagnostics?.milFresh == true && diagnostics.milOn -> "CHECK-ENGINE ON"
                diagnosticsCurrent && diagnostics?.milFresh == true -> "CHECK-ENGINE OFF"
                diagnostics != null -> "LAST READ IS STALE"
                else -> "NO RECENT VEHICLE DATA"
            }, when {
                diagnosticsCurrent && diagnostics?.milFresh == true && diagnostics.milOn -> CriticalColor
                diagnosticsCurrent && diagnostics?.milFresh == true -> AccentColor
                else -> WarningColor
            })
            Spacer(Modifier.height(12.dp))
            if (diagnostics != null && diagnosticsCurrent) {
                if (diagnostics.milFresh) InfoLine("Reported fault count", diagnostics.reportedCount.toString())
                listOf(
                    Triple("Confirmed", diagnostics.confirmedFresh, diagnostics.confirmedCount to diagnostics.confirmedFirst),
                    Triple("Pending", diagnostics.pendingFresh, diagnostics.pendingCount to diagnostics.pendingFirst),
                    Triple("Permanent", diagnostics.permanentFresh, diagnostics.permanentCount to diagnostics.permanentFirst),
                ).forEach { (label, fresh, data) ->
                    InfoLine(label, if (fresh) "${data.first} code(s)${data.second?.let { ", first $it" } ?: ""}"
                        else "No fresh response")
                }
                Text("This snapshot may include only counts and the first code in each category. Open technical details for current limitations.",
                    color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
            } else Text(if (diagnostics != null)
                "The previous snapshot is older than 30 seconds. Refresh before relying on it."
                else "Read the gauge to check current check-engine status and available fault categories.",
                color = MutedColor, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onReadDiagnostics,
                enabled = !model.scanning && model.capabilities?.experimentalNumericConfig == true) {
                Text("Refresh fault information")
            }
            Text("Clearing codes is not available yet. It will require a fresh reading and explicit vehicle-scoped confirmation.",
                color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(18.dp))
        Panel {
            SectionHeading("Firmware update")
            model.updateRecoveryResult?.let { recovery ->
                Text(when (recovery.state) {
                    UpdateRecoveryState.CHECK_REQUIRED -> "Recovery check required"
                    UpdateRecoveryState.INSTALLED -> "Update confirmed"
                    UpdateRecoveryState.PREVIOUS_FIRMWARE -> "Previous firmware recovered"
                    UpdateRecoveryState.WAITING_FOR_CONFIRMATION -> "Health check still pending"
                    UpdateRecoveryState.IDENTITY_CHECKED -> "Recovery check complete"
                }, color = when (recovery.state) {
                    UpdateRecoveryState.INSTALLED, UpdateRecoveryState.IDENTITY_CHECKED -> AccentColor
                    UpdateRecoveryState.PREVIOUS_FIRMWARE -> WarningColor
                    else -> AccentColor
                }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(recovery.message, color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
            }
            val boot = model.bootIdentity
            if (boot != null) {
                InfoLine("Running", boot.version.ifBlank { "unknown" })
                InfoLine("OTA state", when (boot.otaState) {
                    0 -> "New image"
                    1 -> "Pending boot check"
                    2 -> "Confirmed valid"
                    3 -> "Invalid"
                    4 -> "Aborted"
                    else -> "Unavailable (${boot.otaState})"
                })
                if (model.technicalDetailsOpen) {
                    InfoLine("Partition", "0x${boot.partitionAddress.toString(16)}")
                    InfoLine("ELF SHA-256", boot.elfSha256)
                }
            }
            OutlinedButton(onClick = onReadBootIdentity,
                enabled = !model.scanning && model.capabilities?.experimentalNumericConfig == true) {
                Text("Check installed firmware")
            }
            Spacer(Modifier.height(12.dp))
            Text("Check GitHub Releases for a signed development image that matches this gauge.",
                color = MutedColor, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = model::checkHostedFirmware,
                enabled = model.capabilities != null && !model.hostedUpdateBusy && !model.updateInProgress) {
                Text(if (model.hostedUpdateBusy) "Checking GitHub…" else "Check GitHub for update")
            }
            model.hostedUpdate?.let { update ->
                Spacer(Modifier.height(10.dp))
                Text("Version ${update.release.version} • ${update.release.channel.name.lowercase()}",
                    color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(update.release.releaseNotes, color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
                if (update.bundle == null) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = model::downloadHostedFirmware,
                        enabled = !model.hostedUpdateBusy && !model.updateInProgress) {
                        Text("Download and verify")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(model.hostedUpdateMessage, color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            Text("Advanced development package", color = TextColor, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSelectUpdate, enabled = !model.updateInProgress) { Text("Choose development package") }
            Spacer(Modifier.height(8.dp))
            Text(model.updatePackageMessage, color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onInstallUpdate,
                enabled = model.updateReady && !model.scanning && model.capabilities?.experimentalNumericConfig == true) {
                Text(if (model.updateInProgress) "Installing…" else "Install development update")
            }
            Text(if (model.capabilities?.wifiBulk != null)
                "Android may ask once to join the gauge's temporary network. No Wi-Fi password is required. Keep the app open and gauge powered until the new image is confirmed."
                else "Keep the app open and gauge powered until the new image is confirmed. Interrupted transfers can be retried.",
                color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        if (maxWidth < 360.dp) {
            Column {
                Text(label, color = MutedColor, fontSize = 13.sp)
                Text(value, color = TextColor, fontSize = 14.sp, lineHeight = 19.sp)
            }
        } else {
            Row {
                Text(label, color = MutedColor, modifier = Modifier.width(128.dp), fontSize = 14.sp)
                Text(value, color = TextColor, fontSize = 14.sp, lineHeight = 19.sp,
                    modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GaugeCandidateChooser(model: AppViewModel) {
    if (model.gaugeCandidates.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Text("Choose a gauge", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Text("Your choice is remembered for future connections.", color = MutedColor,
        fontSize = 13.sp, lineHeight = 18.sp)
    Spacer(Modifier.height(8.dp))
    model.gaugeCandidates.forEach { candidate ->
        OutlinedButton(
            onClick = { model.selectGaugeCandidate(candidate) },
            enabled = !model.scanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("${candidate.name}  •  …${candidate.id.takeLast(5)}  •  ${candidate.signalDbm} dBm")
        }
        Spacer(Modifier.height(6.dp))
    }
}
