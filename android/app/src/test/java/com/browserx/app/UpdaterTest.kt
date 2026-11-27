package com.browserx.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun readsVersionCodeFromReleaseNotes() {
        val body = """
            versionName=1.1.0
            versionCode=1042

            Changes in this build:
            - toolbar on top
        """.trimIndent()
        assertEquals(1042, Updater.parseVersionCode(body))
        assertEquals("1.1.0", Updater.parseVersionName(body))
    }

    @Test
    fun versionCodeIgnoresCaseAndSeparator() {
        assertEquals(7, Updater.parseVersionCode("Versioncode: 7"))
        assertEquals(19, Updater.parseVersionCode("build versionCode =19"))
    }

    @Test
    fun missingVersionCodeIsNull() {
        assertNull(Updater.parseVersionCode(null))
        assertNull(Updater.parseVersionCode(""))
        assertNull(Updater.parseVersionCode("no build metadata here"))
    }

    @Test
    fun numberHereOnlyCompares() {
        assertTrue(Updater.isNewer(1002, 1001))
        assertFalse(Updater.isNewer(1001, 1001))
        assertFalse(Updater.isNewer(1000, 1001))
        assertFalse(Updater.isNewer(null, 1001))
    }
}
