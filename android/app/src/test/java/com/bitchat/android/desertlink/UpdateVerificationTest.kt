package com.bitchat.android.desertlink

import org.junit.Assert.*
import org.junit.Test

class UpdateVerificationTest {
    @Test fun versionsCompareNumericallyAndRejectMalformedTags() {
        assertTrue(UpdateVerification.newer("v0.10.0", "0.2.0-desertlink"))
        assertFalse(UpdateVerification.newer("v0.1.0", "0.2.0"))
        assertFalse(UpdateVerification.newer("v0.2.0", "0.2.0"))
        assertFalse(UpdateVerification.newer("some-release", "0.2.0"))
    }
    @Test fun upgradesRequireSamePackageNewerCodeAndSameNonemptySignerSet() {
        val packageName = "org.example.rescue"
        assertTrue(UpdateVerification.permitted(packageName, packageName, 1, 2, setOf("trusted"), setOf("trusted")))
        assertFalse(UpdateVerification.permitted(packageName, "org.example.other", 1, 2, setOf("trusted"), setOf("trusted")))
        assertFalse(UpdateVerification.permitted(packageName, packageName, 2, 1, setOf("trusted"), setOf("trusted")))
        assertFalse(UpdateVerification.permitted(packageName, packageName, 1, 2, setOf("trusted"), setOf("other")))
        assertFalse(UpdateVerification.permitted(packageName, packageName, 1, 2, emptySet(), emptySet()))
    }
}
