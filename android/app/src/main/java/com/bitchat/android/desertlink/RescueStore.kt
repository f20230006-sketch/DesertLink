package com.bitchat.android.desertlink

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.Base64

data class RescueState(
    val records: List<RescueRecord> = emptyList(), val fingerprint: String = "", val role: String = "traveller",
    val trustedRescue: String = "", val transmissions: Int = 0, val peers: Int = 0,
    val notice: String = "Loading local incident store…", val ready: Boolean = false
)
private data class SavedLedger(val records: List<RescueRecord>, val heads: Map<String, Int>)

/** Durable, signed incident gossip over the upstream BLE transport. I/O stays off the UI thread. */
class RescueStore(context: Context, private val scope: CoroutineScope,
    private val sendBle: (String) -> Unit, private val peerCount: () -> Int) {
    private val prefs = context.getSharedPreferences("desertlink.incidents.v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val ledger = RescueLedger()
    private val mutex = Mutex()
    private lateinit var keys: KeyPair
    private var cursor = 0
    private val mutable = MutableStateFlow(RescueState())
    val state = mutable.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    if (!keyStore.containsAlias("desertlink-signing-v1")) {
                        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                            initialize(KeyGenParameterSpec.Builder("desertlink-signing-v1", KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                                .setDigests(KeyProperties.DIGEST_SHA256).build())
                        }.generateKeyPair()
                    }
                    keys = KeyPair(keyStore.getCertificate("desertlink-signing-v1").publicKey,
                        keyStore.getKey("desertlink-signing-v1", null) as java.security.PrivateKey)
                    val saved = prefs.getString("ledger", null)?.let { runCatching { gson.fromJson(it, SavedLedger::class.java) }.getOrNull() }
                    if (saved != null) {
                        saved.records.forEach { ledger.accept(it, System.currentTimeMillis()) }
                        saved.heads.forEach { (incident, revision) -> ledger.heads[incident] = maxOf(ledger.heads[incident] ?: 0, revision) }
                    }
                    val pub = Base64.getEncoder().encodeToString(keys.public.encoded)
                    mutable.value = mutable.value.copy(fingerprint = RescueProtocol.fingerprint(pub),
                        role = prefs.getString("role", "traveller") ?: "traveller",
                        trustedRescue = prefs.getString("trusted", "") ?: "", ready = true)
                    publish("Ready. Messages stay here until another BLE node encounters this phone.")
                }.onFailure { mutable.value = mutable.value.copy(notice = "Local store could not initialize. Restart the app.") }
            }
            while (true) {
                delay(5_000)
                mutex.withLock {
                    if (!mutable.value.ready) return@withLock
                    val before = ledger.records.size
                    ledger.prune(System.currentTimeMillis())
                    val peers = runCatching { peerCount() }.getOrDefault(0)
                    val queue = ledger.records.values.toList().sortedWith(compareBy<RescueRecord> { it.kind == "sos" }.thenBy { it.expires })
                    if (peers > 0 && queue.isNotEmpty()) {
                        val record = queue[cursor++ % queue.size]
                        runCatching { sendBle(RescueProtocol.encode(record)) }.onSuccess {
                            mutable.value = mutable.value.copy(transmissions = mutable.value.transmissions + 1)
                        }
                    }
                    if (before != ledger.records.size) persist()
                    mutable.value = mutable.value.copy(peers = peers, records = ledger.records.values.toList())
                }
            }
        }
    }

    private fun persist() {
        check(prefs.edit().putString("ledger", gson.toJson(SavedLedger(ledger.records.values.toList(), ledger.heads))).commit())
    }
    private fun publish(notice: String) { mutable.value = mutable.value.copy(records = ledger.records.values.toList(), notice = notice) }
    private fun mutate(block: () -> Unit) { scope.launch(Dispatchers.IO) { mutex.withLock {
        if (!mutable.value.ready) return@withLock
        runCatching { block(); persist() }.onFailure { publish("Action could not be saved. Please retry.") }
    } } }

    fun send(text: String, incident: String? = null) = mutate {
        val revision = if (incident == null) 1 else (ledger.heads[incident] ?: error("Incident unavailable")) + 1
        val packet = RescueProtocol.sos(keys, text.trim(), System.currentTimeMillis(), incident, revision)
        check(ledger.accept(packet, System.currentTimeMillis()))
        publish("SOS v$revision saved locally. A radio transmission is not destination confirmation.")
    }

    fun receive(wire: String): Boolean {
        if (!wire.startsWith(RescueProtocol.PREFIX)) return false
        mutate {
            val packet = RescueProtocol.decode(wire) ?: return@mutate
            if (ledger.accept(packet, System.currentTimeMillis())) {
                if (packet.kind == "sos" && mutable.value.role == "rescue" && packet.fingerprint != mutable.value.fingerprint) {
                    ledger.accept(RescueProtocol.receipt(keys, packet, System.currentTimeMillis(), false), System.currentTimeMillis())
                }
                publish("Received signed ${packet.kind} v${packet.revision}. Stored for future BLE contacts.")
            }
        }
        return true
    }

    fun acknowledge(packet: RescueRecord) = mutate {
        check(mutable.value.role == "rescue" && packet.fingerprint != mutable.value.fingerprint)
        check(ledger.records[packet.id] == packet && packet.expires > System.currentTimeMillis())
        ledger.accept(RescueProtocol.receipt(keys, packet, System.currentTimeMillis(), true), System.currentTimeMillis())
        publish("Operator confirmed v${packet.revision}. Signed ACK queued for its return journey.")
    }

    fun setRole(role: String) = mutate {
        require(role in setOf("traveller", "relay", "rescue"))
        check(prefs.edit().putString("role", role).commit())
        mutable.value = mutable.value.copy(role = role)
        if (role == "rescue") ledger.latest().filter { it.fingerprint != mutable.value.fingerprint }.forEach {
            ledger.accept(RescueProtocol.receipt(keys, it, System.currentTimeMillis(), false), System.currentTimeMillis())
        }
        publish("Role changed. A self-selected rescue role is not a verified rescue identity.")
    }
    fun trust(fingerprint: String) = mutate {
        val normalized = fingerprint.trim().lowercase().replace(" ", "")
        require(normalized.isEmpty() || normalized.matches(Regex("[0-9a-f]{64}")))
        check(prefs.edit().putString("trusted", normalized).commit())
        mutable.value = mutable.value.copy(trustedRescue = normalized)
        publish("Rescue identity updated. Verify this fingerprint with the operator out of band.")
    }
}
