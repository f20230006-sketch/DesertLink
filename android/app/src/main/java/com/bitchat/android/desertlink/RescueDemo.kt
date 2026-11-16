package com.bitchat.android.desertlink

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

data class DemoSnapshot(val step: Int, val title: String, val detail: String,
    val sourceRevision: Int, val relayRevision: Int, val baseRevision: Int, val confirmed: Boolean)

/** Isolated ledgers, explicit contacts. No Bluetooth calls or physical-peer claims. */
class RescueDemo {
    private fun key() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val sourceKeys = key()
    private val baseKeys = key()
    val source = RescueLedger()
    val relay = RescueLedger()
    val base = RescueLedger()
    private val now = 1_800_000_000_000L
    private var v1 = RescueProtocol.sos(sourceKeys, "Vehicle stopped. Need water.", now)
    private var v2: RescueRecord? = null
    private var oldAck: RescueRecord? = null
    private var currentAck: RescueRecord? = null
    var step = 0
        private set
    private var title = "Ready for the disconnected-update demo"
    private var detail = "Three simulated devices run on this phone. Each has its own ledger. Advance through explicit radio contacts."

    fun advance(): DemoSnapshot {
        when (step) {
            0 -> { source.accept(v1, now); title = "SOS v1 stored at source"; detail = "No peer is reachable. Nothing has been delivered." }
            1 -> { relay.accept(v1, now); title = "A passing vehicle collects v1"; detail = "Explicit source → relay contact. The relay now leaves radio range." }
            2 -> { v2 = RescueProtocol.sos(sourceKeys, "Medical emergency. Urgency increased.", now + 1_000, v1.incident, 2); source.accept(v2!!, now + 1_000); title = "Source updates to v2 while isolated"; detail = "The relay still has only v1. The new urgency cannot cross an empty radio gap." }
            3 -> { base.accept(relay.latest().single(), now + 2_000); oldAck = RescueProtocol.receipt(baseKeys, v1, now + 2_000, true); base.accept(oldAck!!, now + 2_000); relay.accept(oldAck!!, now + 2_000); title = "Rescue base receives and confirms v1"; detail = "Base cannot know that v2 exists. Its signed acknowledgment names v1 exactly." }
            4 -> { source.accept(oldAck!!, now + 3_000); title = "Old ACK returns — v2 stays unconfirmed"; detail = "This is the key safety behavior: confirmation of v1 never confirms unseen v2." }
            5 -> { relay.accept(v2!!, now + 4_000); title = "Relay reconnects and learns v2"; detail = "The newer signed revision replaces the older payload in the relay's local store." }
            6 -> { base.accept(relay.latest().single(), now + 5_000); currentAck = RescueProtocol.receipt(baseKeys, v2!!, now + 5_000, true); base.accept(currentAck!!, now + 5_000); relay.accept(currentAck!!, now + 5_000); title = "Rescue operator confirms the new urgency"; detail = "Base now has v2. The new ACK is carried back by the vehicle." }
            7 -> { source.accept(currentAck!!, now + 6_000); title = "Correct revision confirmed at source"; detail = "The v2 acknowledgment has returned. Both ends now agree on the exact incident revision." }
        }
        step = minOf(8, step + 1)
        return snapshot()
    }
    fun snapshot(): DemoSnapshot {
        val latest = source.latest().firstOrNull()
        val trusted = RescueProtocol.fingerprint(java.util.Base64.getEncoder().encodeToString(baseKeys.public.encoded))
        return DemoSnapshot(step, title, detail, latest?.revision ?: 0, relay.latest().firstOrNull()?.revision ?: 0,
            base.latest().firstOrNull()?.revision ?: 0, latest != null && source.confirmation(latest, trusted) != null)
    }
}
