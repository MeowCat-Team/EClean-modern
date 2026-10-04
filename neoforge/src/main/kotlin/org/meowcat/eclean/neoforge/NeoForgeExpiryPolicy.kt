package org.meowcat.eclean.neoforge

/** NeoForge clamps expiry extensions to a value which can be saved in the item's short Age tag. */
internal object NeoForgeExpiryPolicy {
    private const val MAX_LIFESPAN = Short.MAX_VALUE.toInt() - 1

    data class Retry(val age: Int, val extraLife: Int)

    fun isExtended(age: Int, lifespan: Int, extraLife: Int): Boolean =
        age < (lifespan + extraLife).coerceIn(0, MAX_LIFESPAN)

    fun retryNextTick(age: Int, lifespan: Int): Retry {
        val retainedAge = age.coerceAtMost(MAX_LIFESPAN - 1)
        val nextExpiry = (retainedAge + 1).coerceIn(0, MAX_LIFESPAN)
        return Retry(retainedAge, nextExpiry - lifespan)
    }
}
