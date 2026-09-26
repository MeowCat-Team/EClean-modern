package ui

import top.e404.eclean.ui.SearchSessions
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchSessionsTest {
    @Test fun `a replacement rejects stale chat and timeout callbacks`() {
        var now = 100L
        val sessions = SearchSessions<String, String> { now }
        val first = sessions.begin("player", "first menu", 1_000)
        assertTrue(first.claim())
        assertFalse(first.claim())
        val replacement = sessions.begin("player", "second menu", 1_000)
        assertFalse(sessions.isCurrent("player", first))
        assertFalse(sessions.remove("player", first))
        assertTrue(sessions.isCurrent("player", replacement))
        now = 1_100L
        assertTrue(sessions.expired(replacement))
        assertTrue(sessions.remove("player", replacement))
        assertNull(sessions.current("player"))
    }
}
