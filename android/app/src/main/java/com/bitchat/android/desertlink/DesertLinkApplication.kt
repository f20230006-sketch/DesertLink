package com.bitchat.android.desertlink

import android.app.Application

/** BLE-only startup. No Nostr, Tor, Wi-Fi Aware, geohash, or live-location runtime. */
class DesertLinkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.bitchat.android.mesh.PowerManager.getInstance(this).start()
        com.bitchat.android.spam.SpamFilterPreferences.init(this)
        com.bitchat.android.ui.debug.DebugPreferenceManager.init(this)
        com.bitchat.android.service.MeshServicePreferences.init(this)
        RescueRuntime.get(this)
    }
}
