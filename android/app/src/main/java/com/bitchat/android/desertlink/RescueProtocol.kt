package com.bitchat.android.desertlink

import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

/** Application envelope inside an unchanged Bitchat public mesh message. Not encrypted. */
data class RescueRecord(
    val kind: String,
    val incident: String,
    val revision: Int,
    val publicKey: String,
    val text: String,
    val target: String,
    val created: Long,
    val expires: Long,
    val signature: String = ""
) {
    val fingerprint: String get() = RescueProtocol.fingerprint(publicKey)
    val id: String get() = if (kind == "sos") "$incident:$revision" else "$kind:$target:$fingerprint"
}

object RescueProtocol {
    const val PREFIX = "DL1:"
    const val LIFETIME = 3_600_000L
    private val gson = Gson()
    fun fingerprint(publicKey: String): String = MessageDigest.getInstance("SHA-256")
        .digest(Base64.getDecoder().decode(publicKey)).joinToString("") { "%02x".format(it) }

    private fun canonical(r: RescueRecord): ByteArray = gson.toJson(listOf(
        "DL1", r.kind, r.incident, r.revision.toString(), r.publicKey, r.text, r.target,
        r.created.toString(), r.expires.toString()
    )).toByteArray(StandardCharsets.UTF_8)

    fun sign(record: RescueRecord, keys: KeyPair): RescueRecord {
        require(record.publicKey == Base64.getEncoder().encodeToString(keys.public.encoded))
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keys.private)
        signer.update(canonical(record))
        return record.copy(signature = Base64.getEncoder().encodeToString(signer.sign()))
    }

    fun sos(keys: KeyPair, text: String, now: Long, incident: String? = null, revision: Int = 1): RescueRecord {
        require(text.isNotBlank() && text.length <= 160 && revision > 0)
        val pub = Base64.getEncoder().encodeToString(keys.public.encoded)
        val identifier = incident ?: "${fingerprint(pub)}:${UUID.randomUUID()}"
        require(identifier.startsWith("${fingerprint(pub)}:"))
        return sign(RescueRecord("sos", identifier, revision, pub, text, "", now, now + LIFETIME), keys)
    }

    fun receipt(keys: KeyPair, sos: RescueRecord, now: Long, human: Boolean): RescueRecord {
        val pub = Base64.getEncoder().encodeToString(keys.public.encoded)
        return sign(RescueRecord(if (human) "ack" else "receipt", sos.incident, sos.revision, pub,
            "", sos.id, now, now + LIFETIME), keys)
    }

    fun custody(keys: KeyPair, sos: RescueRecord, now: Long): RescueRecord {
        val pub = Base64.getEncoder().encodeToString(keys.public.encoded)
        return sign(RescueRecord("custody", sos.incident, sos.revision, pub, "", sos.id,
            now, now + LIFETIME), keys)
    }

    fun valid(r: RescueRecord, now: Long): Boolean = runCatching {
        if (r.kind !in setOf("sos", "custody", "receipt", "ack") || r.revision < 1 ||
            r.text.length > 160 || r.publicKey.length > 200 || r.signature.length > 200 ||
            r.incident.length > 120 || r.target.length > 160 ||
            r.created > now + 300_000 || r.created < 0 || r.expires <= now ||
            r.expires <= r.created || r.expires - r.created > LIFETIME) return false
        if (r.kind == "sos" && (r.text.isBlank() || r.target.isNotEmpty() ||
            !r.incident.startsWith("${r.fingerprint}:"))) return false
        if (r.kind != "sos" && (r.target != "${r.incident}:${r.revision}" || r.text.isNotEmpty())) return false
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(r.publicKey)))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(canonical(r))
        verifier.verify(Base64.getDecoder().decode(r.signature))
    }.getOrDefault(false)

    fun encode(r: RescueRecord): String = PREFIX + gson.toJson(r)
    fun decode(wire: String): RescueRecord? = runCatching {
        if (!wire.startsWith(PREFIX) || wire.length > 4096) return null
        gson.fromJson(wire.removePrefix(PREFIX), RescueRecord::class.java)
    }.getOrNull()
}

/** Each physical device owns one ledger. No reference to another device's state. */
class RescueLedger {
    val records = linkedMapOf<String, RescueRecord>()
    val heads = linkedMapOf<String, Int>()

    fun accept(record: RescueRecord, now: Long): Boolean {
        if (!RescueProtocol.valid(record, now)) return false
        prune(now)
        if (records.containsKey(record.id)) return false
        val replacing = record.kind == "sos" && records.values.any { it.kind == "sos" && it.incident == record.incident }
        if (records.size >= 256 && !replacing) return false
        if (record.kind == "sos") {
            if ((heads[record.incident] ?: 0) >= record.revision) return false
            if (!heads.containsKey(record.incident) && heads.size >= 1024) return false
            // Watermarks survive payload expiry to reject stale replay.
            heads[record.incident] = record.revision
            records.entries.removeAll { it.value.kind == "sos" && it.value.incident == record.incident }
        }
        records[record.id] = record
        return true
    }

    fun prune(now: Long) { records.entries.removeAll { it.value.expires <= now } }
    fun latest(): List<RescueRecord> = records.values.filter { it.kind == "sos" }.sortedByDescending { it.created }
    fun confirmation(sos: RescueRecord, trustedRescue: String): RescueRecord? = records.values.firstOrNull {
        it.kind == "ack" && it.target == sos.id && trustedRescue.isNotBlank() && it.fingerprint == trustedRescue
    }
}
