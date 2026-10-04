package org.meowcat.eclean.neoforge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NeoForgeExpiryPolicyTest {
    @Test
    fun `short custom item lifespans retry without waiting for vanilla expiry`() {
        assertFalse(NeoForgeExpiryPolicy.isExtended(100, 100, 0))
        val retry = NeoForgeExpiryPolicy.retryNextTick(100, 100)
        assertEquals(100, retry.age)
        assertEquals(1, retry.extraLife)
        assertTrue(NeoForgeExpiryPolicy.isExtended(retry.age, 100, retry.extraLife))
        assertFalse(NeoForgeExpiryPolicy.isExtended(retry.age + 1, 101, 0))
    }

    @Test
    fun `a provider extension is honored only when it actually postpones expiry`() {
        assertTrue(NeoForgeExpiryPolicy.isExtended(6000, 6000, 2000))
        assertFalse(NeoForgeExpiryPolicy.isExtended(8000, 6000, 2000))
        assertFalse(NeoForgeExpiryPolicy.isExtended(6000, 6000, -20))
        assertFalse(NeoForgeExpiryPolicy.isExtended(32766, 32766, 1))
    }

    @Test
    fun `overdue items receive exactly one tick of grace`() {
        val retry = NeoForgeExpiryPolicy.retryNextTick(6500, 6000)
        assertEquals(6500, retry.age)
        assertEquals(501, retry.extraLife)
        assertTrue(NeoForgeExpiryPolicy.isExtended(retry.age, 6000, retry.extraLife))
        assertFalse(NeoForgeExpiryPolicy.isExtended(retry.age + 1, 6501, 0))
    }

    @Test
    fun `short age limit rolls back only enough to retry next tick`() {
        for ((age, lifespan) in listOf(32766 to 32766, 32767 to 32766, 50000 to 50000)) {
            val retry = NeoForgeExpiryPolicy.retryNextTick(age, lifespan)
            assertEquals(32765, retry.age)
            assertTrue(NeoForgeExpiryPolicy.isExtended(retry.age, lifespan, retry.extraLife))
            assertFalse(NeoForgeExpiryPolicy.isExtended(retry.age + 1, 32766, 0))
        }
    }
}
