package com.bitchat.android.desertlink

enum class IncidentPhase { QUEUED, CARRIED, DELIVERED, CONFIRMED, EXPIRED }
data class IncidentProgress(val phase: IncidentPhase, val carriers: Int, val untrustedAcknowledgment: Boolean)

object RescueOperations {
    fun progress(incident: RescueRecord, evidence: List<RescueRecord>, trusted: String, now: Long): IncidentProgress {
        val matching = evidence.filter { it.target == incident.id }
        val trustedAck = matching.any { it.kind == "ack" && trusted.isNotBlank() && it.fingerprint == trusted }
        val trustedReceipt = matching.any { it.kind == "receipt" && trusted.isNotBlank() && it.fingerprint == trusted }
        val carriers = matching.filter { it.kind == "custody" && it.fingerprint != incident.fingerprint }.map { it.fingerprint }.distinct().size
        return IncidentProgress(when {
            trustedAck -> IncidentPhase.CONFIRMED
            incident.expires <= now -> IncidentPhase.EXPIRED
            trustedReceipt -> IncidentPhase.DELIVERED
            carriers > 0 -> IncidentPhase.CARRIED
            else -> IncidentPhase.QUEUED
        }, carriers, matching.any { it.kind == "ack" && it.fingerprint != trusted })
    }
    fun isResolved(incident: RescueRecord) = incident.text.startsWith("RESOLVED: ")

    fun composeSituation(category: String, people: Int, landmark: String, detail: String): String {
        require(category in listOf("Breakdown", "Medical", "Water", "Lost", "Other"))
        require(people in 1..20)
        val parts = listOf("$category · $people ${if (people == 1) "person" else "people"}",
            landmark.trim().takeIf { it.isNotEmpty() }?.let { "Near $it" }, detail.trim().takeIf { it.isNotEmpty() })
        return parts.filterNotNull().joinToString(". ")
    }
}

/** Fresh records are sent first; old records are retried fairly after the interval. */
class RescueScheduler {
    private val lastSent = mutableMapOf<String, Long>()
    fun next(records: List<RescueRecord>, now: Long, interval: Long, limit: Int = 3): List<RescueRecord> {
        val active = records.filter { it.expires > now }
        lastSent.keys.retainAll(active.map { it.id }.toSet())
        return active.filter { lastSent[it.id]?.let { sent -> now - sent >= interval } ?: true }
            .sortedWith(compareBy<RescueRecord> { lastSent.containsKey(it.id) }
                .thenBy { lastSent[it.id] ?: 0L }
                .thenBy { when (it.kind) { "ack" -> 0; "receipt" -> 1; "sos" -> 2; else -> 3 } })
            .take(limit)
    }
    fun markSent(record: RescueRecord, now: Long) { lastSent[record.id] = now }
    fun newEncounter() { lastSent.clear() }
}
