package com.bitchat.android.desertlink

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class RescueOperationsTest {
    private val now = 1_800_000_000_000L
    private fun keys() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    @Test fun progressSeparatesRelayDestinationAndHumanConfirmation() {
        val sender = keys(); val relay = keys(); val rescue = keys()
        val trusted = RescueProtocol.fingerprint(Base64.getEncoder().encodeToString(rescue.public.encoded))
        val sos = RescueProtocol.sos(sender, "Need assistance", now)
        val evidence = mutableListOf<RescueRecord>()
        assertEquals(IncidentPhase.QUEUED, RescueOperations.progress(sos, evidence, trusted, now).phase)
        evidence.add(RescueProtocol.custody(relay, sos, now + 1))
        assertEquals(IncidentPhase.CARRIED, RescueOperations.progress(sos, evidence, trusted, now + 1).phase)
        evidence.add(RescueProtocol.receipt(rescue, sos, now + 2, false))
        assertEquals(IncidentPhase.DELIVERED, RescueOperations.progress(sos, evidence, trusted, now + 2).phase)
        evidence.add(RescueProtocol.receipt(rescue, sos, now + 3, true))
        assertEquals(IncidentPhase.CONFIRMED, RescueOperations.progress(sos, evidence, trusted, now + 3).phase)
    }

    @Test fun oldOrUntrustedAcknowledgmentsNeverConfirmUpdatedSos() {
        val origin = keys(); val rescue = keys(); val stranger = keys()
        val old = RescueProtocol.sos(origin, "Initial", now)
        val next = RescueProtocol.sos(origin, "More urgent", now + 1, old.incident, 2)
        val trusted = RescueProtocol.fingerprint(Base64.getEncoder().encodeToString(rescue.public.encoded))
        val evidence = listOf(RescueProtocol.receipt(rescue, old, now + 2, true), RescueProtocol.receipt(stranger, next, now + 2, true))
        val progress = RescueOperations.progress(next, evidence, trusted, now + 2)
        assertEquals(IncidentPhase.QUEUED, progress.phase)
        assertTrue(progress.untrustedAcknowledgment)
    }

    @Test fun expiredUnconfirmedIncidentsHaveAnExplicitState() {
        val sos = RescueProtocol.sos(keys(), "Need water", now)
        assertEquals(IncidentPhase.EXPIRED, RescueOperations.progress(sos, emptyList(), "", sos.expires).phase)
    }

    @Test fun signedCustodyDoesNotPretendToBeOperatorConfirmation() {
        val sos = RescueProtocol.sos(keys(), "Need assistance", now)
        val custody = RescueProtocol.custody(keys(), sos, now + 1)
        assertTrue(RescueProtocol.valid(custody, now + 1))
        assertFalse(RescueProtocol.valid(custody.copy(kind = "ack"), now + 1))
    }

    @Test fun schedulerPrioritizesNewRecordsAndDoesNotRepeatEveryTick() {
        val origin = keys(); val rescue = keys()
        val sos = RescueProtocol.sos(origin, "SOS", now)
        val ack = RescueProtocol.receipt(rescue, sos, now, true)
        val scheduler = RescueScheduler()
        val first = scheduler.next(listOf(sos, ack), now, 15_000, 1)
        assertEquals("ack", first.single().kind)
        scheduler.markSent(ack, now)
        assertEquals(sos.id, scheduler.next(listOf(sos, ack), now + 1, 15_000).single().id)
        scheduler.markSent(sos, now + 1)
        assertTrue(scheduler.next(listOf(sos, ack), now + 2, 15_000).isEmpty())
        scheduler.newEncounter()
        assertEquals(2, scheduler.next(listOf(sos, ack), now + 3, 15_000).size)
    }

    @Test fun composePreservesManualLandmarkAndPeopleCount() {
        assertEquals("Medical · 2 people. Near marker 7. Need assistance", RescueOperations.composeSituation("Medical", 2, " marker 7 ", "Need assistance"))
    }
}
