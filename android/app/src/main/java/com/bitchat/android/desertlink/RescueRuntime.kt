package com.bitchat.android.desertlink

import android.content.Context
import com.bitchat.android.mesh.BluetoothMeshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Activity recreation does not own or cancel rescue storage and forwarding. */
object RescueRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var store: RescueStore? = null
    @Volatile private var updater: AppUpdater? = null
    @Synchronized fun updates(context: Context): AppUpdater = updater ?: AppUpdater(context.applicationContext, scope).also { updater = it }
    @Volatile private var radio: BluetoothMeshService? = null
    @Synchronized fun get(context: Context): RescueStore = store ?: RescueStore(context.applicationContext, scope,
        sendBle = { wire -> radio?.let { if (it.isRunningForRescue()) { it.sendMessage(wire); true } else false } ?: false },
        peerCount = { radio?.getActivePeerCount() ?: 0 },
        radioRunning = { radio?.isRunningForRescue() == true }
    ).also { store = it }
    fun bind(mesh: BluetoothMeshService?) { radio = mesh }
}
