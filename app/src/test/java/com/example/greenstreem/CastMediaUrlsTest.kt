package com.example.greenstreem

import org.junit.Assert.assertEquals
import org.junit.Test

class CastMediaUrlsTest {
    @Test fun requestsHlsForXtreamEvenWhenPhoneHasOnlyTsAlternate() {
        val source="https://example.test:8443/live/u/p%40ss/42.ts?token=a%2Fb&x=1#part"
        assertEquals("https://example.test:8443/live/u/p%40ss/42.m3u8?token=a%2Fb&x=1#part",CastMediaUrls.googleLive(source,listOf(source)))
    }
    @Test fun prefersKnownHlsOnWorkingOrigin() {
        val source="https://example.test:8443/live/u/p/42.ts"
        val preferred="https://example.test:8443/live/u/p/42.m3u8?auth=1"
        assertEquals(preferred,CastMediaUrls.googleLive(source,listOf("http://other.test/live/u/p/42.m3u8",preferred)))
    }
    @Test fun keepsWorkingHlsAndDoesNotRewriteOtherTsFiles() {
        assertEquals("http://example.test/x.m3u8",CastMediaUrls.googleLive("http://example.test/x.m3u8",emptyList()))
        assertEquals("http://example.test/movie.ts",CastMediaUrls.googleLive("http://example.test/movie.ts",emptyList()))
    }
}
