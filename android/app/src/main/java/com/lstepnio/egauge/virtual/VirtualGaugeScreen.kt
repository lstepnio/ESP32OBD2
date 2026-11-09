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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lstepnio.egauge.GaugePageDraft
import com.lstepnio.egauge.MeasurementSystem
import com.lstepnio.egauge.MeasurementUnits
import com.lstepnio.egauge.core.designsystem.*
import com.lstepnio.egauge.gearPositions
import com.lstepnio.egauge.readingCatalog
import com.lstepnio.egauge.ui.ScreenContent
import com.lstepnio.egauge.ui.state.readingName
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

internal enum class VirtualStatus(val label: String) {
    Unselected("Choose adapter"),
    Permission("Allow access"),
    BluetoothOff("Bluetooth off"),
    UnsupportedPhone("Bluetooth unavailable"),
    Connecting("Connecting"),
    Waiting("Waiting for readings"),
    Live("Live readings"),
    Retrying("Reconnecting"),
    UnsupportedAdapter("Adapter unsupported"),
    CheckPort("Check transmission port")
}

internal typealias VirtualReadings = Map<VirtualPid, List<VirtualSample>>

internal fun virtualFace(
    page: GaugePageDraft,
    readings: VirtualReadings,
    now: Long,
    units: MeasurementSystem
): ReadingPreviewUi {
    fun history(id: String): List<VirtualSample> {
        val pid = VirtualPid.entries.firstOrNull { it.id == id } ?: return emptyList()
        return readings[pid].orEmpty().filter { it.fresh(pid, now) }
    }
    fun formatted(id: String): String {
        val latest = history(id).lastOrNull() ?: return "--"
        if (id == "tcmgear") return gearPositions[latest.value] ?: "--"
        val unit = readingCatalog.first { it.id == id }.unit
        return MeasurementUnits.displayValue(latest.value.toString(), unit, units)
    }
    fun fraction(id: String, sample: VirtualSample): Float {
        val metric = readingCatalog.first { it.id == id }
        return ((sample.value - metric.minimum) / (metric.maximum - metric.minimum)).toFloat().coerceIn(0f, 1f)
    }
    val primary = page.pidIds.first()
    val arc = page.pidIds.getOrElse(page.arcMetricIndex) { primary }
    val fraction = history(arc).lastOrNull()?.let { fraction(arc, it) }
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
        primaryGear = primary == "tcmgear",
        secondaryGear = secondary == "tcmgear",
        arcFraction = fraction ?: 0f,
        arcAvailable = fraction != null,
        trendFractions = history(primary).map { fraction(primary, it) }
    )
}

internal data class VirtualBindings(val engine: String?, val transmission: String?) {
    fun address(source: VirtualSource) = when (source) {
        VirtualSource.Engine -> engine
        VirtualSource.Transmission -> transmission?.takeIf { engine != null && it != engine }
    }

    fun accepts(source: VirtualSource, address: String): Boolean = when (source) {
        VirtualSource.Engine -> address != transmission
        VirtualSource.Transmission -> engine != null && address != engine
    }
}

internal data class VirtualLink(
    val status: VirtualStatus = VirtualStatus.Unselected,
    val readings: VirtualReadings = emptyMap()
) {
    fun statusAt(now: Long): VirtualStatus = if (status == VirtualStatus.Waiting &&
        readings.any { (pid, samples) -> samples.lastOrNull()?.fresh(pid, now) == true }
    ) {
        VirtualStatus.Live
    } else {
        status
    }
}

private suspend fun pollVirtualLink(
    adapter: VirtualAdapter,
    address: String,
    source: VirtualSource,
    pids: Set<VirtualPid>,
    visible: () -> Set<String>,
    publish: (VirtualLink) -> Unit
) {
    var retryMs = 1_000L
    while (true) {
        publish(VirtualLink(VirtualStatus.Connecting))
        val failure = try {
            adapter.read(address, source) {
                var link = VirtualLink(VirtualStatus.Waiting)
                publish(link)
                val dueAt = mutableMapOf<VirtualPid, Long>()
                var lastSample = SystemClock.elapsedRealtime()
                while (true) {
                    val pid = nextVirtualPid(pids, visible(), dueAt, SystemClock.elapsedRealtime())
                    if (pid == null) {
                        delay(50)
                        continue
                    }
                    val value = sample(pid)
                    val observed = SystemClock.elapsedRealtime()
                    val interval = when {
                        value == null -> 4_000L
                        pid.id in visible() -> pid.periodMs
                        else -> 3_000L
                    }
                    dueAt[pid] = observed + interval
                    if (value != null) {
                        val recent = link.readings[pid].orEmpty().filter { it.fresh(pid, observed) }
                        link = link.copy(readings = link.readings + (pid to (recent.takeLast(23) + VirtualSample(value, observed))))
                        publish(link)
                        lastSample = observed
                        retryMs = 1_000
                    } else if (source == VirtualSource.Transmission && observed - lastSample >= 15_000) {
                        // Recheck calibration after a quiet interval before admitting enhanced data again.
                        throw VirtualTransmissionUnverified()
                    }
                }
            }
            VirtualStatus.Retrying
        } catch (_: UnsupportedVirtualAdapter) {
            publish(VirtualLink(VirtualStatus.UnsupportedAdapter))
            awaitCancellation()
        } catch (_: VirtualTransmissionUnverified) {
            VirtualStatus.CheckPort
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            VirtualStatus.Retrying
        }
        publish(VirtualLink(failure))
        delay(retryMs)
        retryMs = (retryMs * 2).coerceAtMost(15_000L)
    }
}

@Composable
internal fun VirtualGaugeScreen(
    vehicleId: String,
    pages: List<GaugePageDraft>,
    units: MeasurementSystem,
    onBack: () -> Unit,
    onEdit: (Int, MeasurementSystem) -> Unit,
    onBluetoothSettings: () -> Unit,
    onManageAccess: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    val adapter = remember(context) { VirtualAdapter(context.applicationContext) }
    val prefs = remember(context) { context.getSharedPreferences("virtual-gauge", 0) }
    var displayUnits by remember(prefs) {
        mutableStateOf(MeasurementSystem.entries.firstOrNull { it.name == prefs.getString("units", null) } ?: units)
    }
    fun key(source: VirtualSource) = if (source == VirtualSource.Engine) "adapter.$vehicleId" else "transmission.$vehicleId"
    var bindings by remember(vehicleId) {
        fun saved(source: VirtualSource) = prefs.getString(key(source), null)?.takeIf(BluetoothAdapter::checkBluetoothAddress)
        mutableStateOf(VirtualBindings(saved(VirtualSource.Engine), saved(VirtualSource.Transmission).takeIf { VirtualPid.Gear.available }))
    }
    var setup by rememberSaveable(vehicleId) { mutableStateOf(false) }
    var picker by rememberSaveable(vehicleId) { mutableStateOf<VirtualSource?>(null) }
    var scanRequest by rememberSaveable(vehicleId) { mutableIntStateOf(0) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRevision++
    }
    var radioStatus by remember { mutableStateOf<VirtualStatus?>(null) }
    var results by remember { mutableStateOf<List<VirtualCandidate>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val pager = rememberPagerState(pageCount = { pages.size })
    val visibleIds by rememberUpdatedState(pages.getOrNull(pager.currentPage)?.pidIds.orEmpty().toSet())
    val pids = remember(pages) {
        val ids = pages.flatMap { it.pidIds }.toSet()
        VirtualPid.entries.filter { it.available && it.id in ids }.toSet()
    }
    val links = remember(bindings, radioStatus, pids) { mutableStateMapOf<VirtualSource, VirtualLink>() }
    DisposableEffect(view, setup) {
        val previous = view.keepScreenOn
        if (!setup) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(lifecycle, permissionRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                radioStatus = when {
                    !adapter.available -> VirtualStatus.UnsupportedPhone
                    !context.hasVirtualPermissions() -> VirtualStatus.Permission
                    !adapter.enabled -> VirtualStatus.BluetoothOff
                    else -> null
                }
                delay(250)
            }
        }
    }
    LaunchedEffect(lifecycle, bindings, radioStatus, pids) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (radioStatus == null && adapter.enabled) {
                virtualReadLease.withLock {
                    try {
                        coroutineScope {
                            VirtualSource.entries.forEach { source ->
                                val address = bindings.address(source) ?: return@forEach
                                val owned = pids.filter { it.source == source }.toSet().ifEmpty {
                                    setOf(if (source == VirtualSource.Engine) VirtualPid.Rpm else VirtualPid.TransmissionTemp)
                                }
                                launch { pollVirtualLink(adapter, address, source, owned, { visibleIds }) { links[source] = it } }
                            }
                        }
                    } finally {
                        links.clear()
                    }
                }
            }
        }
    }
    LaunchedEffect(lifecycle, picker, scanRequest, radioStatus) {
        if (picker == null || radioStatus != null || !adapter.enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            scanning = true
            results = emptyList()
            try {
                results = adapter.scan()
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
            } finally {
                scanning = false
            }
            awaitCancellation()
        }
    }
    fun back() {
        when {
            picker != null -> picker = null
            setup -> setup = false
            else -> onBack()
        }
    }
    fun select(source: VirtualSource, address: String) {
        if (!bindings.accepts(source, address)) return
        prefs.edit().putString(key(source), address).apply()
        bindings = when (source) {
            VirtualSource.Engine -> bindings.copy(engine = address)
            VirtualSource.Transmission -> bindings.copy(transmission = address)
        }
        picker = null
    }
    val statuses = VirtualSource.entries.associateWith { source ->
        radioStatus ?: links[source]?.statusAt(now) ?: VirtualStatus.Unselected
    }
    BackHandler(enabled = setup) { back() }
    if (setup) {
        VirtualAdapterSetup(
            bindings, statuses, radioStatus, picker, scanning, results, displayUnits,
            onBack = ::back,
            onPick = {
                picker = it
                scanRequest++
            },
            onChoose = ::select,
            onPermission = { permissions.launch(virtualPermissions) },
            onBluetoothSettings = onBluetoothSettings,
            onManageAccess = onManageAccess,
            onRemoveTransmission = {
                prefs.edit().remove(key(VirtualSource.Transmission)).apply()
                bindings = bindings.copy(transmission = null)
            },
            onUnits = {
                prefs.edit().putString("units", it.name).apply()
                displayUnits = it
            }
        )
    } else {
        val readings = links.values.flatMap { it.readings.entries }.associate { it.key to it.value }
        VirtualGaugeContent(
            pages, displayUnits, pager, readings, now,
            VirtualSource.Engine.let(statuses::getValue),
            statuses[VirtualSource.Transmission].takeIf { bindings.address(VirtualSource.Transmission) != null },
            hasMainAdapter = bindings.engine != null,
            onBack = ::back,
            onSetup = { setup = true },
            onEdit = { onEdit(pager.currentPage.coerceIn(pages.indices), displayUnits) }
        )
    }
}

@Composable
internal fun VirtualGaugeContent(
    pages: List<GaugePageDraft>,
    units: MeasurementSystem,
    pager: androidx.compose.foundation.pager.PagerState,
    readings: VirtualReadings,
    now: Long,
    engine: VirtualStatus,
    transmission: VirtualStatus?,
    hasMainAdapter: Boolean,
    onBack: () -> Unit,
    onSetup: () -> Unit,
    onEdit: () -> Unit
) {
    val mainLive = engine == VirtualStatus.Live
    val transmissionLive = transmission == VirtualStatus.Live
    val label = when {
        transmission == null -> engine.label
        mainLive && transmissionLive -> "Both adapters live"
        mainLive -> "Engine live"
        transmissionLive -> "Transmission live"
        else -> engine.label
    }
    val page = pages.getOrNull(pager.currentPage)
    ScreenContent {
        ScreenTitle("Virtual gauge", onBack) {
            ConnectionPill(label, mainLive && (transmission == null || transmissionLive), onClick = onSetup, clickLabel = "Open adapter setup")
        }
        HorizontalPager(pager, modifier = Modifier.fillMaxWidth()) { index ->
            RoundPreview(virtualFace(pages[index], readings, now, units), Modifier.fillMaxWidth())
        }
        if (pages.size > 1) {
            Text(
                "${pager.currentPage + 1} / ${pages.size}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            !hasMainAdapter -> PrimaryAction("Set up adapters", onSetup)

            page?.pidIds?.any { id -> VirtualPid.entries.none { it.available && it.id == id } } == true ->
                Text("This reading is unavailable on Virtual Gauge.", style = MaterialTheme.typography.bodySmall)

            page?.pidIds?.any { it == "tcmgear" || it == "tcmtemp" } == true && transmission == null ->
                TextButton(onSetup) { Text("Add transmission adapter") }
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            if (hasMainAdapter) {
                TextButton(onSetup, Modifier.heightIn(min = 48.dp)) { Text("Adapters") }
            }
            if (pages.isNotEmpty()) {
                OutlinedButton(onEdit, Modifier.heightIn(min = 48.dp)) { Text("Customize pages") }
            }
        }
    }
}
