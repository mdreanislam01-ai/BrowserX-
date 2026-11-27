package com.browserx.app

import org.junit.Assert.*
import org.junit.Test

class SessionRulesTest {
    private val a = BrowserTab("a", "https://example.com", "First", isPinned = true, desktopMode = true)
    private val b = BrowserTab("b")
    private val privateTab = BrowserTab("secret-tab", "https://secret.test", "Secret", isPrivate = true)

    @Test
    fun reopeningRestoresOnlyRegularTabsAndFallsBackFromPrivateSelection() {
        val saved = SessionCodec.encode(listOf(a, privateTab, b), privateTab.id)
        assertFalse(saved.contains("secret"))
        assertFalse(saved.contains("Secret"))
        assertEquals(TabSession(listOf(a, b), a.id), SessionCodec.decode(saved))
        assertEquals(b.id, SessionCodec.decode(SessionCodec.encode(listOf(a, b), b.id)).selected)
        assertTrue(SessionCodec.decode(SessionCodec.encode(listOf(privateTab), privateTab.id)).tabs.isEmpty())
    }

    @Test
    fun invalidSessionAndNonNetworkTargetsAreNotRestored() {
        assertTrue(SessionCodec.decode("broken").tabs.isEmpty())
        assertTrue(SessionCodec.decode("""{"tabs":[{"id":"a","url":"file:///private"}]}""").tabs.isEmpty())
        assertFalse(isWebUrl("https://user:password@example.com"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("https://"))
        assertEquals("https://example.org/a", secureUrl("http://example.org/a", true))
    }

    @Test
    fun returningToAnEarlierStopPreservesBothBranchesAndSurvivesRestart() {
        val root = PageTrail().visit(a.url, "Home")
        val left = root.visit("https://example.org/left", "Left")
        val right = left.visit(a.url, "Home", root.current).visit("https://example.org/right", "Right")
        assertEquals(listOf(null, root.current, root.current), right.stops.map { it.parent })
        val updated = a.copy(trail = right)
        assertEquals(updated, SessionCodec.decode(SessionCodec.encode(listOf(updated), a.id)).tabs.single())
    }

    @Test
    fun trailIsBoundedAndDoesNotKeepDanglingParents() {
        val trail = (1..90).fold(PageTrail()) { graph, n -> graph.visit("https://example.com/$n", "$n") }
        assertEquals(64, trail.stops.size)
        assertNull(trail.stops.first().parent)
    }

    @Test
    fun privateInputNeverSuggestsRegularHistoryFavoritesOrTabs() {
        val favorites = listOf(BookmarkItem(a.url, "Example"))
        val history = listOf(HistoryItem(a.url, "Example"))
        assertTrue(
            addressMatches("example", privateTab, listOf(a, privateTab), favorites, history, true).isEmpty(),
        )
        assertEquals(
            a.id,
            addressMatches("example", b, listOf(a, b), favorites, history, true).single().tabId,
        )
        assertTrue(addressMatches("example", b, listOf(a, b), favorites, history, false).isEmpty())
    }

    @Test
    fun commandsRespectContextAndWorkWithSuggestionsDisabled() {
        fun match(q: String, tab: BrowserTab = a) =
            addressMatches(q, tab, emptyList(), emptyList(), emptyList(), false)
        assertEquals(BrowserAction.NEW, match(">new").first().action)
        assertTrue(match(">close").isEmpty())
        assertTrue(match(">reader", b).isEmpty())
        assertTrue(match(">unknown").isEmpty())
        assertFalse(BrowserAction.FAVORITE.available(privateTab))
        assertFalse(BrowserAction.CLOSE.available(a))
    }

    @Test
    fun movingAnAddressActionSwapsTheSlots() {
        val moved = AppSettings().placeButton(leading = true, BarButton.NEW)
        assertEquals(BarButton.NEW, moved.leadingButton)
        assertEquals(BarButton.TABS, moved.trailingButton)
        val hidden = moved.placeButton(false, BarButton.NONE).placeButton(true, BarButton.NONE)
        assertEquals(BarButton.NONE, hidden.leadingButton)
        assertEquals(BarButton.NONE, hidden.trailingButton)
    }
}
