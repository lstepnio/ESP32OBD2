package com.lstepnio.egauge

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.lstepnio.egauge.ui.CompanionApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Platform permissions, file picker, window integration. Features live in ui/. */
class MainActivity : ComponentActivity() {
    private val model: AppViewModel by viewModels()
    private var fold by mutableStateOf<FoldingFeature?>(null)
    private val automaticConnectionEnabled: Boolean get() =
        !(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            intent.getBooleanExtra("debug_disable_auto_connect", false))
    private val gaugePermissions: Array<String> get() = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.isNotEmpty() && grants.values.all { it }) {
            if (automaticConnectionEnabled) model.retryConnection() else model.discoverGauge()
        } else model.retryConnection()
    }
    private val wifiPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) installUpdate()
        else model.updatePackageError("Nearby Wi-Fi permission is required. Allow it in Android settings, then try again.")
    }
    private val updatePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            try {
                val bundle = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { DevUpdateBundle.read(this@MainActivity, it) }
                        ?: error("Could not open the update package")
                }
                model.updatePackageLoaded(bundle)
            } catch (error: Exception) {
                model.updatePackageError(error.message ?: "Update package is invalid")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val debug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debug) model.setDebugOtaPauseBeforeActivationMs(intent.getLongExtra("debug_ota_pause_before_activation_ms", 0L))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity).collect { layout ->
                    fold = layout.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                }
            }
        }
        setContent {
            SideEffect {
                if (debug || model.updateInProgress) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            CompanionApp(model, ::requestGauge, ::requestInstallUpdate,
                { updatePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, fold)
        }
    }

    override fun onStart() {
        super.onStart()
        if (automaticConnectionEnabled) {
            model.setConnectionForeground(true)
            val preferences = getSharedPreferences("connection-permissions", MODE_PRIVATE)
            if (gaugePermissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED } &&
                !preferences.getBoolean("nearby-requested", false)) {
                preferences.edit().putBoolean("nearby-requested", true).apply()
                permissionLauncher.launch(gaugePermissions)
            }
        }
    }

    override fun onStop() {
        model.setConnectionForeground(false)
        super.onStop()
    }

    private fun requestGauge() {
        if (gaugePermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            if (automaticConnectionEnabled) {
                if (model.connection.phase == com.lstepnio.egauge.connection.ConnectionPhase.BluetoothOff)
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                else model.retryConnection()
            } else model.discoverGauge()
        } else {
            val asked = getSharedPreferences("connection-permissions", MODE_PRIVATE).getBoolean("nearby-requested", false)
            if (asked && gaugePermissions.none { shouldShowRequestPermissionRationale(it) })
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
            else permissionLauncher.launch(gaugePermissions)
        }
    }

    private fun requestInstallUpdate() {
        val permission = when {
            model.capabilities?.wifiBulk == null -> null
            Build.VERSION.SDK_INT >= 33 -> Manifest.permission.NEARBY_WIFI_DEVICES
            else -> Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (permission != null && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            wifiPermissionLauncher.launch(permission)
        else installUpdate()
    }

    private fun installUpdate() {
        if (model.updateReady) model.installSelectedUpdate()
        else model.downloadHostedFirmware(installWhenReady = true)
    }
}
