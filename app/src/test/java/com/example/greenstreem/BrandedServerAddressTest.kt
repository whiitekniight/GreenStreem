package com.example.greenstreem

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandedServerAddressTest {
    private val next = "https://thisisnotreal.ryvox.cc:8443"
    @Test fun migratesPreviousProviderIncludingItsPortAndSavedPlayback() {
        assertEquals("$next/live/user/pass/123.ts", BrandedServerAddress.migrate(
            "https://totallyfucked.freindmts.com:443/live/user/pass/123.ts", true, next))
        assertEquals("$next/get.php?username=test%2Buser&password=test%2Fvalue#saved", BrandedServerAddress.migrate(
            "http://TOTALLYFUCKED.FREINDMTS.COM:80/get.php?username=test%2Buser&password=test%2Fvalue#saved", true, next))
    }
    @Test fun migrationIsIdempotentAndDoesNotTouchLookalikesOrUserInfo() {
        val migrated = BrandedServerAddress.migrate("https://totallyfucked.freindmts.com", true, next)
        assertEquals(next, migrated)
        assertEquals(migrated, BrandedServerAddress.migrate(migrated, true, next))
        for (url in listOf("https://totallyfucked.freindmts.com.other.example", "https://user@totallyfucked.freindmts.com", "ftp://totallyfucked.freindmts.com")) {
            assertEquals(url, BrandedServerAddress.migrate(url, true, next))
        }
        val old = "https://totallyfucked.freindmts.com"
        assertEquals(old, BrandedServerAddress.migrate(old, false, next))
    }
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
