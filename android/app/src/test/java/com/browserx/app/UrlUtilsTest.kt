package com.browserx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun fullUrlStaysAsIs() {
        assertEquals(
            "https://example.com/page",
            resolveInputToUrl("https://example.com/page", SearchEngine.GOOGLE)
        )
        assertEquals(
            "http://example.com",
            resolveInputToUrl("http://example.com", SearchEngine.GOOGLE)
        )
    }

    @Test
    fun bareDomainGetsHttps() {
        assertEquals("https://example.com", resolveInputToUrl("example.com", SearchEngine.GOOGLE))
        assertEquals("https://www.google.com", resolveInputToUrl("www.google.com", SearchEngine.GOOGLE))
    }

    @Test
    fun plainTextBecomesSearch() {
        assertEquals(
            "https://www.google.com/search?q=cats+and+dogs",
            resolveInputToUrl("cats and dogs", SearchEngine.GOOGLE)
        )
        assertEquals(
            "https://duckduckgo.com/?q=hello+world",
            resolveInputToUrl("hello world", SearchEngine.DUCKDUCKGO)
        )
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertEquals("", resolveInputToUrl("   ", SearchEngine.GOOGLE))
    }

    @Test
    fun adHostMatching() {
        assertTrue(isAdHost("doubleclick.net"))
        assertTrue(isAdHost("pagead2.googlesyndication.com"))
        assertTrue(isAdHost("sub.ads.yahoo.com"))
        assertFalse(isAdHost("example.com"))
        assertFalse(isAdHost("notdoubleclick.net"))
        assertFalse(isAdHost(""))
    }

    @Test
    fun hostParsing() {
        assertEquals("www.example.com", hostOf("https://www.example.com/a/b?x=1"))
        assertEquals("", hostOf("not a url at all"))
    }
}
