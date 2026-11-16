package com.bitchat.android.desertlink

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RadioReadiness(val supported: Boolean = true, val permissions: Boolean = false, val enabled: Boolean = false)

class DesertLinkActivity : ComponentActivity() {
    private val readiness = mutableStateOf(RadioReadiness())
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshRadio(true) }
    private val enableRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshRadio(true) }
    private val required: Array<String> get() = if (Build.VERSION.SDK_INT >= 31) arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT
    ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = RescueRuntime.get(this)
        val updater = RescueRuntime.updates(this)
        setContent { DesertLinkScreen(store = store, readiness = readiness.value, updater = updater,
            onEnableBluetooth = ::requestRadio, onInstallUpdate = {
                lifecycleScope.launch {
                    val file = updater.state.value.readyFile ?: return@launch
                    if (!withContext(Dispatchers.IO) { updater.verifyApk(file) }) return@launch
                    if (!packageManager.canRequestPackageInstalls()) {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    } else {
                        val uri = FileProvider.getUriForFile(this@DesertLinkActivity, "$packageName.fileprovider", file)
                        startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                }
            }) }
    }
    override fun onResume() { super.onResume(); refreshRadio(true) }
    private fun requestRadio() {
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toMutableList()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.POST_NOTIFICATIONS)
        if (missing.isNotEmpty()) { permissionRequest.launch(missing.toTypedArray()); return }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter != null && !adapter.isEnabled) enableRequest.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        else refreshRadio(true)
    }
    private fun refreshRadio(start: Boolean) {
        val allowed = required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val enabled = allowed && runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        readiness.value = RadioReadiness(adapter != null, allowed, enabled)
        if (start && enabled) runCatching { DesertLinkService.start(this) }
    }
}
