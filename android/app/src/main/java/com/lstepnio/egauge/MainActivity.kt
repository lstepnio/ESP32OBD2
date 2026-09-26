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
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                model.connected(model.bleClient.readNearby())
            } catch (error: TimeoutCancellationException) {
                model.connectionError("Gauge discovery timed out. Keep the gauge powered and try again")
            } catch (error: CancellationException) {
                model.connectionError("Gauge discovery was interrupted")
                throw error
            } catch (error: Exception) {
                model.connectionError(error.message ?: "Could not read the gauge")
            }
        }
    }

    private fun requestSelection() {
        if (Build.VERSION.SDK_INT >= 31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            model.selectionError("Grant nearby device permission, then try again")
            return
        }
        val index = when (model.draft.pidId) {
            "rpm" -> 0; "speed" -> 1; "load" -> 2; "coolant" -> 3; "fuel" -> 4
            else -> { model.selectionError("This example has no built-in gauge reading"); return }
        }
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                model.selectionApplied(model.bleClient.selectNearby(index))
                if (model.capabilities?.savedStateRead == true) {
                    model.markScanning(true)
                    delay(350)
                    try {
                        model.snapshotRead(model.bleClient.readSavedSnapshot())
                    } catch (error: CancellationException) {
                        model.snapshotError("Saved gauge state refresh was interrupted")
                        throw error
                    } catch (error: Exception) {
                        model.snapshotError("Reading changed on gauge; saved state refresh failed. Tap Read saved gauge state to retry.")
                    }
                }
            } catch (error: TimeoutCancellationException) {
                model.selectionError("Pairing or gauge confirmation timed out. Open pairing on the gauge and retry")
            } catch (error: CancellationException) {
                model.selectionError("Gauge selection was interrupted; read its state before retrying")
                throw error
            } catch (error: Exception) {
                model.selectionError(error.message ?: "Could not select gauge reading")
            }
        }
    }

    private fun requestRotation(rotation: Int) {
        if (model.capabilities?.displayRotationWrite != true) {
            model.selectionError("Gauge does not offer display rotation control")
            return
        }
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                model.snapshotRead(model.bleClient.rotateNearby(rotation))
            } catch (error: CancellationException) {
                model.snapshotError("Display rotation was interrupted; read saved state before retrying")
                throw error
            } catch (error: Exception) {
                model.snapshotError(error.message ?: "Gauge did not confirm display rotation")
            }
        }
    }

    private fun requestSavedSnapshot() {
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                model.snapshotRead(model.bleClient.readSavedSnapshot())
            } catch (error: CancellationException) {
                model.snapshotError("Saved gauge state read was interrupted")
                throw error
            } catch (error: Exception) {
                model.snapshotError(error.message ?: "Could not read saved gauge state")
            }
        }
    }

    private fun requestNumericConfiguration() {
        if (model.capabilities?.experimentalNumericConfig != true) return
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                val profileId = model.profileCollection.activeId
                val draft = model.draft
                val baseRevision = model.activeConfigRevision
                    ?: error("Refresh the saved gauge configuration before sending")
                val baseHash = model.verifiedConfigHash
                    ?: error("Refresh the saved gauge configuration before sending")
                val applied = GaugeConfigTransferClient(this@MainActivity).apply(
                    model.bleClient.selectedGauge(), draft, profileId, baseRevision, baseHash)
                model.configApplied(applied, profileId, draft)
            } catch (error: CancellationException) {
                model.selectionError("Configuration transfer interrupted; read gauge status before retrying")
                throw error
            } catch (error: Exception) {
                model.selectionError(error.message ?: "Gauge did not confirm configuration")
            }
        }
    }

    private fun requestActiveConfiguration() {
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                model.configStatusRead(GaugeConfigTransferClient(this@MainActivity)
                    .readActive(model.bleClient.selectedGauge()))
            } catch (error: CancellationException) {
                model.selectionError("Configuration status read was interrupted")
                throw error
            } catch (error: Exception) {
                model.selectionError(error.message ?: "Could not read active configuration")
            }
        }
    }

    private fun requestActiveDocument() {
        if (model.capabilities?.experimentalNumericConfig != true) return
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                model.activeDocumentRead(GaugeConfigTransferClient(this@MainActivity)
                    .readActiveDocument(model.bleClient.selectedGauge()))
            } catch (error: CancellationException) {
                model.activeDocumentError("Configuration document read was interrupted")
                throw error
            } catch (error: Exception) {
                model.activeDocumentError(error.message ?: "Could not verify saved configuration document")
            }
        }
    }

    private fun requestDiagnostics() {
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                model.diagnosticsRead(GaugeConfigTransferClient(this@MainActivity)
                    .readDiagnostics(model.bleClient.selectedGauge()))
            } catch (error: CancellationException) {
                model.selectionError("Diagnostic read was interrupted")
                throw error
            } catch (error: Exception) {
                model.selectionError(error.message ?: "Could not read gauge diagnostics")
            }
        }
    }

    private fun requestBootIdentity() {
        model.markScanning(true)
        lifecycleScope.launch {
            try {
                model.bootIdentityRead(GaugeConfigTransferClient(this@MainActivity)
                    .readBootIdentity(model.bleClient.selectedGauge()))
            } catch (error: CancellationException) {
                model.selectionError("Firmware identity read was interrupted")
                throw error
            } catch (error: Exception) {
                model.selectionError(error.message ?: "Could not read running firmware identity")
            }
        }
    }

    private fun requestInstallUpdate() {
        model.installSelectedUpdate()
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
            when (model.destination) {
                Destination.Garage -> GarageScreen(model, onFindGauge)
                Destination.Design -> DesignScreen(model, onApplyNumeric, onReadDocument)
                Destination.Pids -> PidsScreen(model)
                Destination.Device -> DeviceScreen(model, onFindGauge, onSelectReading, onReadSaved,
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
        StatusPill("DEMO DATA", WarningColor)
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
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextColor)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun GarageScreen(model: AppViewModel, onFindGauge: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        Intro("00 / Your garage", "Ready for your next drive.",
            "Build your view now. Connect the gauge to see which features its firmware supports.")
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
            Button(onClick = onFindGauge, enabled = !model.scanning, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (model.scanning) "Finding gauge…" else "Find nearby gauge")
            }
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
            Text("The current preview stays on this phone. Device configuration needs a future secure firmware operation.",
                color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { model.navigate(Destination.Design) }) { Text("Open design studio") }
        }
        Spacer(Modifier.height(24.dp))
        SectionHeading("Sources")
        SourceRow("ECM", "Engine adapter", "Not connected to the app")
        Spacer(Modifier.height(10.dp))
        SourceRow("TCM", "Transmission adapter", "Add when the second adapter is available")
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
        Intro("01 / Your dashboard", "Make every signal count.",
            "Editing ${model.profileCollection.active.name}. Choose a reading and a layout; values are simulated.")
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
        SectionHeading("Renderer")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GaugeLayout.entries.forEach { layout ->
                FilterChip(
                    selected = model.draft.layout == layout,
                    onClick = { model.selectLayout(layout) },
                    label = { Text(layout.label, fontSize = 12.sp, maxLines = 1) },
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionHeading("Primary reading")
        Panel {
            Text(pid.name, color = TextColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("${pid.source}  •  ${pid.request}  •  ${pid.exampleKind}; support unknown",
                color = MutedColor, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = { model.navigate(Destination.Pids) }) { Text("Browse PID catalog") }
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
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Apply to gauge")
        }
        val numericReady = model.capabilities?.experimentalNumericConfig == true &&
            !model.scanning && model.profileError == null && model.draft.source == "ECM" &&
            model.draft.pidId in listOf("rpm", "coolant", "speed") &&
            model.draft.warning in -40..215 && model.draft.critical in -40..215 &&
            model.draft.warning + model.draft.hysteresis < model.draft.critical &&
            model.draft.warning - model.draft.hysteresis >= -40 &&
            model.activeConfigRevision != null && model.verifiedConfigHash != null
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onApplyNumeric, enabled = numericReady,
            modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(if (model.scanning) "Sending numeric profile…" else "Send experimental numeric profile")
        }
        Text("Sends RPM, coolant and speed numeric pages plus your coolant thresholds. " +
            "The selected page opens first. Refresh saved configuration first; a changed gauge revision blocks the write. " +
            "Requires the owner bond; vehicle support is unverified.",
            color = MutedColor, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("Full configuration is pending:", color = MutedColor, fontSize = 13.sp)
        model.configurationBlockers.forEach { reason ->
            Text("• $reason", color = MutedColor, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ThresholdRow(label: String, value: Int, tint: Color, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = tint, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = onDecrease, modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−", fontSize = 20.sp) }
        Text("$value°", color = TextColor, modifier = Modifier.width(58.dp),
            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = onIncrease, modifier = Modifier.size(48.dp),
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
        (model.sourceFilter == "All" || pid.source == model.sourceFilter) &&
            (model.query.isBlank() || "${pid.name} ${pid.request} ${pid.category} ${pid.source}"
                .contains(model.query.trim(), ignoreCase = true))
    }
    Column(Modifier.fillMaxSize()) {
        Intro("02 / Explore data", "Find what matters.",
            "Search example PIDs by name, request or source. Real support requires an adapter session.")
        OutlinedTextField(value = model.query, onValueChange = model::search,
            label = { Text("Search PIDs") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
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
                Text("Simulated path: queued, querying, responding or no response. Each real result will need a vehicle profile, adapter, ECU source and time.",
                    color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
                Text("Current vehicle evidence: none", color = WarningColor, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { model.showLab(!model.labOpen) },
            modifier = Modifier.fillMaxWidth()) {
            Text(if (model.labOpen) "Close decoder lab" else "Open decoder lab")
        }
        if (model.labOpen) {
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
        OutlinedButton(onClick = { model.showCustomLab(!model.customLabOpen) },
            modifier = Modifier.fillMaxWidth()) {
            Text(if (model.customLabOpen) "Close custom request lab" else "Draft a custom read request")
        }
        if (model.customLabOpen) {
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
                    OutlinedButton(onClick = { model.selectPid(pid); model.navigate(Destination.Design) },
                        enabled = model.profileError == null) {
                        Text("Use in preview")
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
        Intro("03 / Device", "Know what is ready.",
            "Capabilities come from the gauge. Other panels show the planned workflow without vehicle actions.")
        Panel {
            SectionHeading("Connection")
            Text(model.deviceMessage, color = MutedColor, fontSize = 15.sp)
            val caps = model.capabilities
            if (caps != null) {
                Spacer(Modifier.height(14.dp))
                InfoLine("Board", caps.board)
                InfoLine("Protocol", "${caps.protocolMajor} • ${when {
                    model.activeConfigRevision != null -> "numeric owner control"
                    caps.quickSelect -> "paired quick select"
                    caps.experimentalNumericConfig -> "owner control available"
                    else -> "discovery only"
                }}")
                InfoLine("Adapter slots", "${caps.maxAdapterLinks} • coexistence unverified")
                InfoLine("Configuration", when {
                    caps.experimentalNumericConfig -> "Experimental numeric ECM profile transfer"
                    caps.displayRotationWrite -> "Built-in reading and display rotation"
                    caps.quickSelect -> "Built-in reading only"
                    else -> "Read only"
                })
                model.savedGauge?.let { saved ->
                    Spacer(Modifier.height(12.dp))
                    InfoLine("Saved reading", listOf("RPM", "Speed", "Engine load", "Coolant", "Fuel")[saved.readingIndex])
                    InfoLine("Saved revision", saved.revision.toString())
                    InfoLine("Display rotation", "${saved.rotation * 90}°")
                    Text("This is the gauge's persisted selection. The design studio remains a phone-only draft.",
                        color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onFindGauge, enabled = !model.scanning) {
                Text(if (model.scanning) "Finding gauge…" else "Read gauge capabilities")
            }
            if (caps?.experimentalNumericConfig == true) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onReadConfig, enabled = !model.scanning) {
                    Text("Read active configuration status")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onReadDocument, enabled = !model.scanning) {
                    Text("Verify saved configuration document")
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
                    OutlinedButton(onClick = { model.navigate(Destination.Design) }) {
                        Text("Compare with phone draft")
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
            SectionHeading("Diagnostics")
            val diagnostics = model.diagnostics
            StatusPill(when {
                diagnostics?.milFresh == true && diagnostics.milOn -> "MIL ON"
                diagnostics?.milFresh == true -> "MIL OFF"
                else -> "NO FRESH VEHICLE EVIDENCE"
            }, when {
                diagnostics?.milFresh == true && diagnostics.milOn -> CriticalColor
                diagnostics?.milFresh == true -> AccentColor
                else -> WarningColor
            })
            Spacer(Modifier.height(12.dp))
            if (diagnostics != null) {
                if (diagnostics.milFresh) InfoLine("ECU reported count", diagnostics.reportedCount.toString())
                listOf(
                    Triple("Confirmed", diagnostics.confirmedFresh, diagnostics.confirmedCount to diagnostics.confirmedFirst),
                    Triple("Pending", diagnostics.pendingFresh, diagnostics.pendingCount to diagnostics.pendingFirst),
                    Triple("Permanent", diagnostics.permanentFresh, diagnostics.permanentCount to diagnostics.permanentFirst),
                ).forEach { (label, fresh, data) ->
                    InfoLine(label, if (fresh) "${data.first} code(s)${data.second?.let { ", first $it" } ?: ""}"
                        else "No fresh response")
                }
                Text("Counts and first codes are a read-only snapshot from headerless adapter replies. " +
                    "No ECU identity or complete code list is available yet.",
                    color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
            } else Text("Read the gauge's protected snapshot to check MIL and available code categories. " +
                "No adapter session has been verified.",
                color = MutedColor, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onReadDiagnostics,
                enabled = !model.scanning && model.capabilities?.experimentalNumericConfig == true) {
                Text("Read gauge diagnostics")
            }
            Text("Code clearing requires a fresh, explicit confirmation on the real ECU.",
                color = MutedColor, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(18.dp))
        Panel {
            SectionHeading("Firmware update")
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
                InfoLine("Partition", "0x${boot.partitionAddress.toString(16)}")
                InfoLine("ELF SHA-256", boot.elfSha256.take(16) + "…")
            }
            OutlinedButton(onClick = onReadBootIdentity,
                enabled = !model.scanning && model.capabilities?.experimentalNumericConfig == true) {
                Text("Read running firmware")
            }
            Spacer(Modifier.height(12.dp))
            Text("Import a locally signed development package to check its board, size, SHA-256 and P-256 signature.",
                color = MutedColor, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSelectUpdate, enabled = !model.updateInProgress) { Text("Choose signed package") }
            Spacer(Modifier.height(8.dp))
            Text(model.updatePackageMessage, color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onInstallUpdate,
                enabled = model.updateReady && !model.scanning && model.capabilities?.experimentalNumericConfig == true) {
                Text(if (model.updateInProgress) "Installing…" else "Install development update")
            }
            Text("Keep the app open and gauge powered until the new image is confirmed. Interrupted transfers can be retried.",
                color = MutedColor, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = MutedColor, modifier = Modifier.width(112.dp), fontSize = 14.sp)
        Text(value, color = TextColor, fontSize = 14.sp, lineHeight = 18.sp)
    }
}
