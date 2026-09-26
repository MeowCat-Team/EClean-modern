package update

import kotlin.test.*
import top.e404.eclean.update.*

class ReleaseVersionTest {
    @Test fun `semver precedence including prerelease identifiers is numeric and ordered`() {
        val values = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.9.0", "1.10.0")
        values.zipWithNext().forEach { (a,b) -> assertTrue(ReleaseVersion.parse(a)!! < ReleaseVersion.parse(b)!!) }
        assertEquals(0, ReleaseVersion.parse("v0.2.9")!!.compareTo(ReleaseVersion.parse("0.2.9+build.123")!!))
    }
    @Test fun `stable updates ignore draft prerelease older and malformed releases regardless of API order`() {
        val candidates = listOf(ReleaseCandidate("v9.0.0", draft = true), ReleaseCandidate("v2.0.0-rc.1", prerelease = true),
            ReleaseCandidate("v0.2.8"), ReleaseCandidate("v0.2.10"), ReleaseCandidate("v0.2.9"), ReleaseCandidate("latest"))
        assertEquals("v0.2.10", newerRelease("0.2.9", candidates)?.tag)
        assertNull(newerRelease("0.2.10", candidates))
        assertNull(newerRelease("unknown", candidates))
    }
    @Test fun `preview installs can advance while malformed semantic versions are rejected`() {
        assertEquals("1.0.0-rc.2", newerRelease("1.0.0-rc.1", listOf(ReleaseCandidate("1.0.0-rc.2", prerelease = true)))?.tag)
        listOf("1.0", "01.0.0", "1.0.0-01", "1.0.0+", "1.0.0-", "1.0.0-alpha..1").forEach { assertNull(ReleaseVersion.parse(it)) }
    }
}
