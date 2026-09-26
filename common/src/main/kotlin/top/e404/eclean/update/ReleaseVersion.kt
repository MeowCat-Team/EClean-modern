package top.e404.eclean.update

import java.math.BigInteger

/** SemVer precedence, with a conventional release-tag `v` prefix accepted. */
@ConsistentCopyVisibility
data class ReleaseVersion private constructor(
    val core: List<BigInteger>,
    val prerelease: List<String>,
) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        core.zip(other.core).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
        if (prerelease.isEmpty() != other.prerelease.isEmpty()) return if (prerelease.isEmpty()) 1 else -1
        prerelease.zip(other.prerelease).forEach { (a, b) ->
            if (a != b) {
                val an = a.toBigIntegerOrNull()
                val bn = b.toBigIntegerOrNull()
                return when {
                    an != null && bn != null -> an.compareTo(bn)
                    an != null -> -1
                    bn != null -> 1
                    else -> a.compareTo(b)
                }
            }
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val pattern = Regex("^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$")
        fun parse(raw: String): ReleaseVersion? {
            val match = pattern.matchEntire(raw.trim()) ?: return null
            val pre = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (pre.any { it.all(Char::isDigit) && it.length > 1 && it.startsWith('0') }) return null
            return ReleaseVersion((1..3).map { match.groupValues[it].toBigInteger() }, pre)
        }
    }
}

data class ReleaseCandidate(val tag: String, val draft: Boolean = false, val prerelease: Boolean = false)

/** Stable installs stay on stable releases. Unknown versions never imply an available update. */
fun newerRelease(current: String, candidates: List<ReleaseCandidate>): ReleaseCandidate? {
    val installed = ReleaseVersion.parse(current) ?: return null
    return candidates.filterNot { it.draft }
        .mapNotNull { item -> ReleaseVersion.parse(item.tag)?.let { item to it } }
        .filter { (item, version) -> installed.prerelease.isNotEmpty() || (!item.prerelease && version.prerelease.isEmpty()) }
        .filter { (_, version) -> version > installed }
        .maxByOrNull { it.second }?.first
}
