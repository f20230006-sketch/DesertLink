package com.bitchat.android.desertlink

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bitchat.android.R
import com.bitchat.android.mesh.BluetoothMeshDelegate
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.service.MeshServiceHolder
import kotlinx.coroutines.*

/** The only running transport is the upstream BLE service. */
class DesertLinkService : Service(), BluetoothMeshDelegate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var mesh: BluetoothMeshService? = null
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("desertlink-ble", "Bluetooth rescue network", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, DesertLinkActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(10002, NotificationCompat.Builder(this, "desertlink-ble")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("DESERTLINK · Bluetooth network")
            .setContentText("Waiting for contacts. Your incidents stay saved offline.")
            .setContentIntent(open).setOngoing(true).build())
        scope.launch {
            val candidate = MeshServiceHolder.getOrCreate(applicationContext)
            if (!isActive) { candidate.stopServices(); return@launch }
            mesh = candidate
            candidate.delegate = this@DesertLinkService
            RescueRuntime.bind(candidate)
            runCatching { candidate.startServices() }.onFailure { RescueRuntime.bind(null) }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { mesh?.let { if (!it.isRunningForRescue()) runCatching { it.startServices() } } }
        return START_STICKY
    }
    override fun onDestroy() {
        scope.cancel()
        RescueRuntime.bind(null)
        mesh?.delegate = null
        mesh?.stopServices()
        mesh = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun didReceiveMessage(message: BitchatMessage) { RescueRuntime.get(this).receive(message.content) }
    override fun didUpdatePeerList(peers: List<String>) { }
    override fun didReceiveChannelLeave(channel: String, fromPeer: String) { }
    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) { }
    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) { }
    override fun didReceiveVerifyChallenge(peerID: String, payload: ByteArray, timestampMs: Long) { }
    override fun didReceiveVerifyResponse(peerID: String, payload: ByteArray, timestampMs: Long) { }
    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? = null
    override fun getNickname(): String = "DESERTLINK"
    override fun isFavorite(peerID: String): Boolean = false
    companion object {
        fun start(context: Context) { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, DesertLinkService::class.java)) }
    }
}
