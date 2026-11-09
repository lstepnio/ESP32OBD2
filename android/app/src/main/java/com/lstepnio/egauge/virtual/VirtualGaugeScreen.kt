package com.lstepnio.egauge.virtual

import android.bluetooth.BluetoothAdapter
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lstepnio.egauge.GaugeLayout
import com.lstepnio.egauge.GaugePageDraft
import com.lstepnio.egauge.MeasurementSystem
import com.lstepnio.egauge.MeasurementUnits
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.readingCatalog
import com.lstepnio.egauge.readingFraction
import com.lstepnio.egauge.rpmRedlineFraction
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.readingName
import kotlin.math.abs
import kotlinx.coroutines.*

internal enum class VirtualStatus(val label: String) {
    Unselected("Choose adapter"),
    Permission("Allow access"),
    BluetoothOff("Bluetooth off"),
    UnsupportedPhone("Bluetooth unavailable"),
    Connecting("Connecting"),
    Waiting("Waiting for readings"),
    Live("Live readings"),
    Retrying("Reconnecting"),
    UnsupportedAdapter("Adapter unsupported")
}

private const val TREND_WINDOW_MS = 30_000L

internal typealias VirtualReadings = Map<VirtualPid, List<VirtualSample>>

internal fun virtualFace(
    page: GaugePageDraft,
    readings: VirtualReadings,
    now: Long,
    units: MeasurementSystem
): ReadingPreviewUi {
    fun history(id: String): List<VirtualSample> {
        val pid = VirtualPid.entries.firstOrNull { it.id == id } ?: return emptyList()
        val samples = readings[pid].orEmpty()
        val latest = samples.lastOrNull()?.takeIf { it.fresh(pid, now) } ?: return emptyList()
        return samples.filter { latest.observedAtMs - it.observedAtMs in 0..TREND_WINDOW_MS }
    }
    fun formatted(id: String): String {
        val latest = history(id).lastOrNull() ?: return "--"
        val unit = readingCatalog.first { it.id == id }.unit
        return MeasurementUnits.displayValue(latest.value.toString(), unit, units)
    }
    val primary = page.pidIds.first()
    val arc = page.pidIds.getOrElse(page.arcMetricIndex) { primary }
    val fraction = history(arc).lastOrNull()?.let { sample ->
        val metric = readingCatalog.first { it.id == arc }
        readingFraction(arc, sample.value, metric.minimum, metric.maximum, page.rpmRedline)
    }
    val trend = if (page.layout == GaugeLayout.Trend) history(primary) else emptyList()
    val trendMetric = readingCatalog.first { it.id == primary }
    val trendFractions = if (trend.isEmpty()) {
        emptyList()
    } else {
        val low = trend.minOf { it.value }
        val high = trend.maxOf { it.value }
        val floor = when (trendMetric.unit) {
            "°C" -> 8.0
            "rpm" -> 500.0
            "%", "km/h" -> 5.0
            "V" -> 0.2
            else -> maxOf(abs(trend.last().value) * 0.05, (trendMetric.maximum - trendMetric.minimum) * 0.001)
        }
        val span = maxOf(floor, (high - low) * 1.25)
        val center = (low + high) / 2
        trend.map { (0.5 + (it.value - center) / span).toFloat().coerceIn(0f, 1f) }
    }
    val secondary = page.pidIds.getOrNull(1)
    fun unit(id: String) = MeasurementUnits.label(readingCatalog.first { it.id == id }.unit, units)
    return ReadingPreviewUi(
        name = readingName(primary),
        value = formatted(primary),
        unit = unit(primary),
        layout = PreviewLayout.valueOf(page.layout.name),
        live = true,
        secondaryName = secondary?.let(::readingName),
        secondaryValue = secondary?.let(::formatted),
        secondaryUnit = secondary?.let(::unit),
        arcMetricIndex = page.arcMetricIndex,
        arcFraction = fraction ?: 0f,
        arcAvailable = fraction != null,
        trendFractions = trendFractions,
        trendPositions = trend.map { 1f - (trend.last().observedAtMs - it.observedAtMs).toFloat() / TREND_WINDOW_MS },
        rpmRedlineFraction = rpmRedlineFraction(page.rpmRedline)
    )
}

@Composable
internal fun VirtualGaugeScreen(
    vehicleId: String,
    pages: List<GaugePageDraft>,
    units: MeasurementSystem,
    onBack: () -> Unit,
    onEdit: (Int, MeasurementSystem) -> Unit,
    onBluetoothSettings: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val adapter = remember(context) { VirtualAdapter(context.applicationContext) }
    val prefs = remember(context) { context.getSharedPreferences("virtual-gauge", 0) }
    var displayUnits by remember(prefs) {
        mutableStateOf(MeasurementSystem.entries.firstOrNull { it.name == prefs.getString("units", null) } ?: units)
    }
    val adapterKey = "adapter.$vehicleId"
    var address by remember(vehicleId) {
        mutableStateOf(prefs.getString(adapterKey, null)?.takeIf(BluetoothAdapter::checkBluetoothAddress))
    }
    var picker by rememberSaveable(vehicleId) { mutableStateOf(false) }
    var scanRequest by rememberSaveable(vehicleId) { mutableIntStateOf(0) }
    var permissionsChanged by remember { mutableIntStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsChanged++
    }
    var status by remember { mutableStateOf(VirtualStatus.Unselected) }
    var readings by remember { mutableStateOf<VirtualReadings>(emptyMap()) }
    var results by remember { mutableStateOf<List<VirtualCandidate>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    val visibleIds by rememberUpdatedState(pages.getOrNull(pager.currentPage)?.pidIds.orEmpty().toSet())
    val pids = remember(pages) {
        val ids = pages.flatMap { it.pidIds }.toSet()
        VirtualPid.entries.filter { it.id in ids }.toSet()
    }
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(250)
            }
        }
    }
    LaunchedEffect(lifecycle, address, picker, scanRequest, permissionsChanged, pids) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            readings = emptyMap()
            try {
                var retryMs = 1_000L
                while (true) {
                    status = when {
                        !adapter.available -> VirtualStatus.UnsupportedPhone
                        !context.hasVirtualPermissions() -> VirtualStatus.Permission
                        !adapter.enabled -> VirtualStatus.BluetoothOff
                        picker || address == null -> VirtualStatus.Unselected
                        else -> VirtualStatus.Connecting
                    }
                    if (status == VirtualStatus.Connecting) {
                        if (pids.isEmpty()) {
                            status = VirtualStatus.Waiting
                            awaitCancellation()
                        }
                        try {
                            adapter.read(requireNotNull(address)) {
                                status = VirtualStatus.Waiting
                                val dueAt = mutableMapOf<VirtualPid, Long>()
                                while (true) {
                                    val pid = nextVirtualPid(pids, visibleIds, dueAt, SystemClock.elapsedRealtime())
                                    if (pid == null) {
                                        delay(50)
                                        continue
                                    }
                                    val value = sample(pid)
                                    val observed = SystemClock.elapsedRealtime()
                                    val interval = if (value == null) {
                                        4_000L
                                    } else if (pid.id in visibleIds) {
                                        pid.periodMs
                                    } else {
                                        3_000L
                                    }
                                    dueAt[pid] = observed + interval
                                    if (value != null) {
                                        val previous = readings[pid].orEmpty()
                                        val recent = if (previous.lastOrNull()?.fresh(pid, observed) == true) {
                                            previous.filter { observed - it.observedAtMs <= TREND_WINDOW_MS }.takeLast(59)
                                        } else {
                                            emptyList()
                                        }
                                        readings = readings + (pid to (recent + VirtualSample(value, observed)))
                                        retryMs = 1_000
                                    }
                                }
                            }
                        } catch (_: UnsupportedVirtualAdapter) {
                            status = VirtualStatus.UnsupportedAdapter
                            awaitCancellation()
                        } catch (error: Exception) {
                            currentCoroutineContext().ensureActive()
                            readings = emptyMap()
                            status = VirtualStatus.Retrying
                            delay(retryMs)
                            retryMs = (retryMs * 2).coerceAtMost(15_000L)
                        }
                    } else if (picker && status == VirtualStatus.Unselected) {
                        scanning = true
                        results = emptyList()
                        try {
                            results = adapter.scan()
                        } catch (error: Exception) {
                            currentCoroutineContext().ensureActive()
                        } finally {
                            scanning = false
                        }
                        searched = true
                        awaitCancellation()
                    } else if (status == VirtualStatus.Unselected || status == VirtualStatus.UnsupportedPhone) {
                        awaitCancellation()
                    } else {
                        delay(1_000)
                    }
                }
            } finally {
                readings = emptyMap()
            }
        }
    }
    val effectiveStatus = if (status == VirtualStatus.Waiting && readings.any { (pid, history) ->
            history.lastOrNull()?.fresh(pid, now) == true
        }
    ) {
        VirtualStatus.Live
    } else {
        status
    }
    BackHandler(enabled = picker) { picker = false }
    VirtualGaugeContent(
        pages, displayUnits, pager, readings, now, effectiveStatus,
        picker, scanning, searched, results,
        onBack = { if (picker) picker = false else onBack() },
        onSearch = {
            searched = false
            picker = true
            scanRequest++
        },
        onChoose = {
            prefs.edit().putString(adapterKey, it).apply()
            address = it
            picker = false
        },
        onPermission = { permissions.launch(virtualPermissions) },
        onBluetoothSettings = onBluetoothSettings,
        onUnits = {
            prefs.edit().putString("units", it.name).apply()
            displayUnits = it
        },
        onEdit = { onEdit(pager.currentPage.coerceIn(pages.indices), displayUnits) }
    )
}

@Composable
internal fun VirtualGaugeContent(
    pages: List<GaugePageDraft>,
    units: MeasurementSystem,
    pager: androidx.compose.foundation.pager.PagerState,
    readings: VirtualReadings,
    now: Long,
    status: VirtualStatus,
    picker: Boolean,
    scanning: Boolean,
    searched: Boolean,
    results: List<VirtualCandidate>,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onChoose: (String) -> Unit,
    onPermission: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onUnits: (MeasurementSystem) -> Unit,
    onEdit: () -> Unit
) {
    var unitsMenu by remember { mutableStateOf(false) }
    ScreenContent {
        ScreenTitle(if (picker) "Choose adapter" else "Virtual gauge", onBack) {
            ConnectionPill(status.label, status == VirtualStatus.Live)
        }
        if (picker && status == VirtualStatus.Permission) {
            PrimaryAction("Allow nearby devices", onPermission)
        } else if (picker && status == VirtualStatus.BluetoothOff) {
            PrimaryAction("Turn on Bluetooth", onBluetoothSettings)
        } else if (picker) {
            Text("Use the adapter in your car’s main OBD-II port. Turn on the ignition and disconnect any other app or gauge using it.")
            results.forEach {
                SettingsRow(it.name, "Ends in ${it.address.takeLast(5)}", true, { onChoose(it.address) })
            }
            if (scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Looking for adapters")
            } else if (searched && results.isEmpty()) {
                Text("No compatible adapters found")
                PrimaryAction("Search again", onSearch)
            }
        } else {
            HorizontalPager(pager, modifier = Modifier.fillMaxWidth()) { index ->
                RoundPreview(virtualFace(pages[index], readings, now, units), Modifier.fillMaxWidth())
            }
            if (pages.size > 1) {
                Text(
                    "Page ${pager.currentPage + 1} of ${pages.size} · Swipe to change",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            if (pages.getOrNull(pager.currentPage)?.pidIds?.any { id ->
                    VirtualPid.entries.none { it.id == id }
                } == true
            ) {
                Text("Some readings on this page need a supported vehicle setup.")
            }
            when (status) {
                VirtualStatus.Unselected -> PrimaryAction("Find adapter", onSearch)

                VirtualStatus.Permission -> PrimaryAction("Allow nearby devices", onPermission)

                VirtualStatus.BluetoothOff -> PrimaryAction("Turn on Bluetooth", onBluetoothSettings)

                VirtualStatus.UnsupportedPhone -> Text("This phone needs Bluetooth LE to connect to an adapter.")

                VirtualStatus.UnsupportedAdapter -> Text("Choose a compatible 18F0 BLE OBD-II adapter.")

                VirtualStatus.Retrying, VirtualStatus.Waiting ->
                    Text("Keep the ignition on and disconnect other apps or gauges using this adapter.")

                else -> Unit
            }
            if (status in setOf(
                    VirtualStatus.Connecting,
                    VirtualStatus.Waiting,
                    VirtualStatus.Live,
                    VirtualStatus.Retrying,
                    VirtualStatus.UnsupportedAdapter
                )
            ) {
                TextButton(onSearch) { Text("Change adapter") }
            }
            if (pages.isNotEmpty()) TextButton(onEdit) { Text("Customize pages") }
            Box {
                TextButton({ unitsMenu = true }) { Text("Units: ${units.name}") }
                DropdownMenu(unitsMenu, { unitsMenu = false }) {
                    MeasurementSystem.entries.forEach { system ->
                        DropdownMenuItem({ Text(system.name) }, {
                            unitsMenu = false
                            onUnits(system)
                        })
                    }
                }
            }
        }
    }
}
