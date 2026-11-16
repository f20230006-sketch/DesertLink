package com.bitchat.android.desertlink

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.UUID

data class RescueEvent(val id: String = UUID.randomUUID().toString(), val time: Long,
    val incident: String, val revision: Int, val title: String)
data class RescueState(
    val records: List<RescueRecord> = emptyList(), val incidents: List<RescueRecord> = emptyList(),
    val evidence: List<RescueRecord> = emptyList(), val events: List<RescueEvent> = emptyList(),
    val fingerprint: String = "", val role: String = "traveller", val roleChosen: Boolean = false,
    val trustedRescue: String = "", val transmissions: Int = 0, val peers: Int = 0,
    val networkRunning: Boolean = false, val forwarding: Boolean = true, val batterySaver: Boolean = false,
    val now: Long = System.currentTimeMillis(), val notice: String = "Opening your local incident store…",
    val ready: Boolean = false, val busy: Boolean = false, val operation: Long = 0,
    val actionSucceeded: Boolean = false, val error: String? = null
)
private data class SavedLedger(val records: List<RescueRecord>, val heads: Map<String, Int>,
    val history: List<RescueRecord>? = null, val events: List<RescueEvent>? = null,
    val role: String? = null, val trusted: String? = null, val forwarding: Boolean? = null,
    val saver: Boolean? = null, val roleChosen: Boolean? = null, val transmissions: Int = 0)

/** Process-owned store. Every accepted record is committed before any forwarding round can see it. */
class RescueStore(context: Context, private val scope: CoroutineScope,
    private val sendBle: (String) -> Boolean, private val peerCount: () -> Int,
    private val radioRunning: () -> Boolean = { false }) {
    private val prefs = context.getSharedPreferences("desertlink.incidents.v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val ledger = RescueLedger()
    private val history = linkedMapOf<String, RescueRecord>()
    private val events = mutableListOf<RescueEvent>()
    private val expired = mutableSetOf<String>()
    private val scheduler = RescueScheduler()
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private lateinit var keys: KeyPair
    private val mutable = MutableStateFlow(RescueState())
    val state = mutable.asStateFlow()

    init { scope.launch(Dispatchers.IO) {
        mutex.withLock {
            runCatching {
                val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (!ks.containsAlias("desertlink-signing-v1")) {
                    KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                        initialize(KeyGenParameterSpec.Builder("desertlink-signing-v1", KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                            .setDigests(KeyProperties.DIGEST_SHA256).build())
                    }.generateKeyPair()
                }
                keys = KeyPair(ks.getCertificate("desertlink-signing-v1").publicKey, ks.getKey("desertlink-signing-v1", null) as java.security.PrivateKey)
                val saved = prefs.getString("ledger", null)?.let { gson.fromJson(it, SavedLedger::class.java) }
                val now = System.currentTimeMillis()
                saved?.records?.forEach { ledger.accept(it, now) }
                saved?.heads?.forEach { (id, version) -> ledger.heads[id] = maxOf(ledger.heads[id] ?: 0, version) }
                (saved?.history ?: saved?.records ?: emptyList()).filter { RescueProtocol.valid(it, it.created) }.forEach(::rememberRecord)
                events.addAll(saved?.events.orEmpty().take(200))
                expired.addAll(history.values.filter { it.kind == "sos" && it.expires <= now }.map { it.id })
                mutable.value = mutable.value.copy(
                    fingerprint = RescueProtocol.fingerprint(Base64.getEncoder().encodeToString(keys.public.encoded)),
                    role = saved?.role ?: prefs.getString("role", "traveller") ?: "traveller",
                    roleChosen = saved?.roleChosen ?: prefs.contains("role"),
                    trustedRescue = saved?.trusted ?: prefs.getString("trusted", "") ?: "",
                    forwarding = saved?.forwarding ?: true, batterySaver = saved?.saver ?: false,
                    transmissions = saved?.transmissions ?: 0, ready = true)
                publish("Your incidents are saved on this phone. No internet is needed for rescue messaging.")
            }.onFailure { mutable.value = mutable.value.copy(error = "The local incident store could not be opened. Restart the app; saved data has not been cleared.") }
        }
        loaded.complete(Unit)
        while (isActive) {
            delay(3_000)
            mutex.withLock {
                if (!mutable.value.ready) return@withLock
                runCatching { tick() }.onFailure { publish("Connection check will retry.") }
            }
        }
    } }

    private fun rememberRecord(r: RescueRecord) {
        val key = if (r.kind == "sos") r.incident else r.id
        if (r.kind == "sos" && (history[key]?.revision ?: 0) > r.revision) return
        history[key] = r
        while (history.size > 512) history.remove(history.keys.first())
    }
    private fun event(r: RescueRecord, title: String) {
        events.add(0, RescueEvent(time = System.currentTimeMillis(), incident = r.incident, revision = r.revision, title = title))
        while (events.size > 200) events.removeAt(events.lastIndex)
    }
    private fun saved() = SavedLedger(ledger.records.values.toList(), ledger.heads.toMap(), history.values.toList(),
        events.toList(), mutable.value.role, mutable.value.trustedRescue, mutable.value.forwarding,
        mutable.value.batterySaver, mutable.value.roleChosen, mutable.value.transmissions)
    private fun persist() { check(prefs.edit().putString("ledger", gson.toJson(saved())).commit()) }
    private fun publish(notice: String = mutable.value.notice) {
        mutable.value = mutable.value.copy(records = ledger.records.values.toList(),
            incidents = history.values.filter { it.kind == "sos" }.sortedByDescending { it.created },
            evidence = history.values.filter { it.kind != "sos" }, events = events.toList(),
            now = System.currentTimeMillis(), notice = notice)
    }
    private fun perform(userAction: Boolean = true, block: () -> String) { scope.launch(Dispatchers.IO) {
        loaded.await()
        mutex.withLock {
            if (!mutable.value.ready) return@withLock
            val backup = saved()
            val previous = mutable.value
            mutable.value = previous.copy(busy = userAction, error = null)
            runCatching {
                val notice = block()
                if (userAction || saved() != backup) persist()
                publish(notice)
                mutable.value = mutable.value.copy(busy = false, operation = previous.operation + if (userAction) 1 else 0, actionSucceeded = true)
            }.onFailure {
                ledger.records.clear(); backup.records.forEach { ledger.records[it.id] = it }
                ledger.heads.clear(); ledger.heads.putAll(backup.heads)
                history.clear(); backup.history.orEmpty().forEach(::rememberRecord)
                events.clear(); events.addAll(backup.events.orEmpty())
                mutable.value = previous.copy(busy = false, error = "This action could not be saved. Nothing was forwarded from the failed change. Please retry.",
                    operation = previous.operation + if (userAction) 1 else 0, actionSucceeded = false)
            }
        }
    } }
    private fun tick() {
        val now = System.currentTimeMillis()
        val oldSize = ledger.records.size
        ledger.prune(now)
        history.values.filter { it.kind == "sos" && it.expires <= now && expired.add(it.id) }.forEach { event(it, "Forwarding lifetime expired; incident retained in history") }
        val running = runCatching { radioRunning() }.getOrDefault(false)
        val peers = if (running) runCatching { peerCount() }.getOrDefault(0) else 0
        if (peers > mutable.value.peers) scheduler.newEncounter()
        mutable.value = mutable.value.copy(networkRunning = running, peers = peers, now = now)
        if (running && peers > 0 && mutable.value.forwarding) {
            val due = scheduler.next(ledger.records.values.toList(), now, if (mutable.value.batterySaver) 45_000 else 15_000)
            for (record in due) {
                if (runCatching { sendBle(RescueProtocol.encode(record)) }.getOrDefault(false)) {
                    scheduler.markSent(record, now)
                    mutable.value = mutable.value.copy(transmissions = mutable.value.transmissions + 1)
                }
            }
        }
        if (oldSize != ledger.records.size) persist()
        publish()
    }
    fun send(text: String, incident: String? = null) = perform {
        val version = if (incident == null) 1 else (ledger.heads[incident] ?: error("Unknown incident")) + 1
        val record = RescueProtocol.sos(keys, text.trim(), System.currentTimeMillis(), incident, version)
        check(ledger.accept(record, System.currentTimeMillis()))
        rememberRecord(record)
        event(record, if (version == 1) "SOS saved locally" else "Revision v$version saved; older ACKs cannot confirm it")
        "Saved v$version on this phone. It will travel at the next Bluetooth contact."
    }
    fun resolve(incident: RescueRecord) = send("RESOLVED: No further assistance requested.", incident.incident)
    fun receive(wire: String): Boolean {
        if (!wire.startsWith(RescueProtocol.PREFIX)) return false
        perform(false) {
            val record = RescueProtocol.decode(wire)
            if (record == null || !ledger.accept(record, System.currentTimeMillis())) return@perform mutable.value.notice
            rememberRecord(record)
            event(record, when (record.kind) { "ack" -> "Operator ACK for v${record.revision} received; trust checked separately"; "receipt" -> "Rescue-node receipt for v${record.revision} received"; "custody" -> "A relay reports a durable copy of v${record.revision}"; else -> "Signed v${record.revision} received over Bluetooth" })
            if (record.kind == "sos" && record.fingerprint != mutable.value.fingerprint) {
                val custody = RescueProtocol.custody(keys, record, System.currentTimeMillis())
                if (ledger.accept(custody, System.currentTimeMillis())) rememberRecord(custody)
                if (mutable.value.role == "rescue") {
                    val receipt = RescueProtocol.receipt(keys, record, System.currentTimeMillis(), false)
                    if (ledger.accept(receipt, System.currentTimeMillis())) rememberRecord(receipt)
                }
            }
            "Received and saved ${record.kind} v${record.revision}."
        }
        return true
    }
    fun acknowledge(record: RescueRecord) = perform {
        check(mutable.value.role == "rescue" && record.fingerprint != mutable.value.fingerprint)
        check(ledger.records[record.id] == record && record.expires > System.currentTimeMillis())
        val ack = RescueProtocol.receipt(keys, record, System.currentTimeMillis(), true)
        val accepted = ledger.accept(ack, System.currentTimeMillis())
        check(accepted || ledger.records.containsKey(ack.id))
        if (accepted) { rememberRecord(ack); event(record, "Operator confirmed v${record.revision}; return ACK queued") }
        "Confirmed v${record.revision}. The sender will know only when this ACK returns."
    }
    fun setRole(role: String) = perform {
        require(role in setOf("traveller", "relay", "rescue"))
        mutable.value = mutable.value.copy(role = role, roleChosen = true)
        if (role == "rescue") ledger.latest().filter { it.fingerprint != mutable.value.fingerprint }.forEach {
            val receipt = RescueProtocol.receipt(keys, it, System.currentTimeMillis(), false)
            if (ledger.accept(receipt, System.currentTimeMillis())) rememberRecord(receipt)
        }
        "Role saved. A rescue role alone does not establish trust."
    }
    fun trust(value: String) = perform {
        val normalized = value.trim().lowercase().replace(" ", "").removePrefix("desertlink:rescue:")
        require(normalized.isEmpty() || normalized.matches(Regex("[0-9a-f]{64}")))
        mutable.value = mutable.value.copy(trustedRescue = normalized)
        "Rescue identity saved. Compare the full code directly with the operator."
    }
    fun forwarding(enabled: Boolean) = perform { mutable.value = mutable.value.copy(forwarding = enabled); if (enabled) scheduler.newEncounter(); if (enabled) "Forwarding resumed." else "Forwarding paused. Incoming records are still saved." }
    fun batterySaver(enabled: Boolean) = perform { mutable.value = mutable.value.copy(batterySaver = enabled); "Retry interval changed. New records remain eligible immediately." }
    fun syncNow() = perform { scheduler.newEncounter(); "Pending records will be offered during the next available Bluetooth contact." }
    fun dismissError() { mutable.value = mutable.value.copy(error = null) }
}
