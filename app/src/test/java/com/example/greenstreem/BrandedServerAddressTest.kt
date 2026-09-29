package com.example.greenstreem

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandedServerAddressTest {
    private val next = "https://totallyfucked.freindmts.com"
    @Test fun migratesOldProviderAndPreservesEncodedPathAndQuery() {
        assertEquals("$next/get.php?username=test%2Buser&password=test%2Fvalue", BrandedServerAddress.migrate(
            "http://kennye71.trustissues.life:80/get.php?username=test%2Buser&password=test%2Fvalue", true, next))
    }
    @Test fun leavesUnbrandedAndOtherProvidersAlone() {
        val old = "https://kennye71.trustissues.life"
        assertEquals(old, BrandedServerAddress.migrate(old, false, next))
        for (url in listOf("https://other.example", "https://kennye71.trustissues.life.other.example", next, "not a url", "")) {
            assertEquals(url, BrandedServerAddress.migrate(url, true, next))
        }
    }
    @Test fun supportsTrailingSlashAndHostCase() {
        assertEquals("$next/", BrandedServerAddress.migrate("https://KENNYE71.TRUSTISSUES.LIFE/", true, next))
    }
}
