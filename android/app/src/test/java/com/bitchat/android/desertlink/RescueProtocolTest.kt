package com.bitchat.android.desertlink

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class RescueProtocolTest {
    private val now = 1_800_000_000_000L
    private fun keys() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    @Test fun signatureRejectsChangedUrgency() {
        val record = RescueProtocol.sos(keys(), "Need water", now)
        assertTrue(RescueProtocol.valid(record, now))
        assertFalse(RescueProtocol.valid(record.copy(text = "No help needed"), now))
        assertFalse(RescueProtocol.valid(record, record.expires))
        assertEquals(record, RescueProtocol.decode(RescueProtocol.encode(record)))
    }

    @Test fun revisionDoesNotReachDisconnectedRelay() {
        val origin = keys()
        val source = RescueLedger()
        val relay = RescueLedger()
        val first = RescueProtocol.sos(origin, "Initial SOS", now)
        source.accept(first, now)
        relay.accept(first, now)
        val second = RescueProtocol.sos(origin, "Updated urgency", now + 1, first.incident, 2)
        source.accept(second, now + 1)
        assertEquals(2, source.latest().single().revision)
        assertEquals(1, relay.latest().single().revision)
        assertTrue(relay.accept(second, now + 1))
        assertFalse(relay.accept(first, now + 1))
        assertEquals(2, relay.latest().single().revision)
    }

    @Test fun oldAckAndUntrustedAckCannotConfirmCurrentRevision() {
        val origin = keys()
        val operator = keys()
        val impostor = keys()
        val ledger = RescueLedger()
        val first = RescueProtocol.sos(origin, "Initial SOS", now)
        val second = RescueProtocol.sos(origin, "Updated SOS", now + 1, first.incident, 2)
        val trusted = RescueProtocol.fingerprint(Base64.getEncoder().encodeToString(operator.public.encoded))
        ledger.accept(second, now + 1)
        ledger.accept(RescueProtocol.receipt(operator, first, now + 2, true), now + 2)
        assertNull(ledger.confirmation(second, trusted))
        ledger.accept(RescueProtocol.receipt(impostor, second, now + 2, true), now + 2)
        assertNull(ledger.confirmation(second, trusted))
        ledger.accept(RescueProtocol.receipt(operator, second, now + 3, true), now + 3)
        assertNotNull(ledger.confirmation(second, trusted))
        assertNull(ledger.confirmation(second, ""))
    }

    @Test fun watermarkRejectsOlderPayloadAfterLatestExpires() {
        val origin = keys()
        val ledger = RescueLedger()
        val first = RescueProtocol.sos(origin, "Old SOS", now)
        val second = RescueProtocol.sos(origin, "New SOS", now + 1, first.incident, 2)
        ledger.accept(second, now + 1)
        ledger.prune(second.expires)
        val delayedOldRevision = RescueProtocol.sos(origin, "Old revision replay", second.expires, first.incident, 1)
        assertFalse(ledger.accept(delayedOldRevision, second.expires))
    }

    @Test fun demoUsesExplicitContactsAndRevisionSpecificConfirmation() {
        val demo = RescueDemo()
        repeat(4) { demo.advance() }
        val staleAck = demo.advance()
        assertEquals(2, staleAck.sourceRevision)
        assertEquals(1, staleAck.baseRevision)
        assertFalse(staleAck.confirmed)
        repeat(3) { demo.advance() }
        assertTrue(demo.snapshot().confirmed)
        assertEquals(2, demo.snapshot().baseRevision)
    }

    @Test fun malformedEnvelopeIsRejected() {
        assertNull(RescueProtocol.decode("chat text"))
        assertNull(RescueProtocol.decode("DL1:not-json"))
        val invalid = RescueProtocol.decode("DL1:{}")
        assertFalse(invalid != null && RescueProtocol.valid(invalid, now))
    }
}
