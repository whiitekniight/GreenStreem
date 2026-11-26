package com.example.greenstreem

import org.junit.Assert.*
import org.junit.Test

class DlnaCastingTest {
    private fun description(control: String="/upnp/control")="""<root xmlns="urn:schemas-upnp-org:device-1-0"><device><friendlyName>Living Room &amp; TV</friendlyName><serviceList><service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>$control</controlURL></service></serviceList></device></root>"""
    @Test fun discoversTransportAndResolvesRelativeEndpoint() {
        val device=DlnaCasting.describe(description(),"http://192.168.1.50:1400/description.xml")!!
        assertEquals("Living Room & TV",device.name)
        assertEquals("http://192.168.1.50:1400/upnp/control",device.endpoint)
    }
    @Test fun doesNotTreatNonRendererAsTv() {
        assertNull(DlnaCasting.describe(description().replace("AVTransport:1","ContentDirectory:1"),"http://192.168.1.50/root.xml"))
    }
    @Test fun rejectsExternalEntityAndCrossHostControl() {
        assertTrue(runCatching { DlnaCasting.describe("<!DOCTYPE root [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+description(),"http://192.168.1.50/root.xml") }.isFailure)
        assertTrue(runCatching { DlnaCasting.describe(description("http://evil.example/control"),"http://192.168.1.50/root.xml") }.isFailure)
    }
    @Test fun metadataEscapesProviderUrlsAndTitles() {
        assertEquals("A&amp;B&lt;C&gt;&quot;&apos;",CastSupport.xml("A&B<C>\"'"))
    }
    @Test fun rendererReceivesLoadPlaySeekAndStopInOrder() {
        val actions=java.util.concurrent.CopyOnWriteArrayList<String>()
        val bodies=java.util.concurrent.CopyOnWriteArrayList<String>()
        val server=object: fi.iki.elonen.NanoHTTPD("127.0.0.1",0) {
            override fun serve(s: IHTTPSession): Response {
                val action=s.headers["soapaction"].orEmpty().substringAfter('#').trim('"')
                actions.add(action)
                val body=mutableMapOf<String,String>(); s.parseBody(body); bodies.add(body["postData"].orEmpty())
                val value=if(action=="GetTransportInfo") "<CurrentTransportState>PLAYING</CurrentTransportState>" else ""
                return newFixedLengthResponse(Response.Status.OK,"text/xml","<Envelope><Body>$value</Body></Envelope>")
            }
        }
        server.start(5000,true)
        try {
            val device=DlnaCasting.Device("Test TV","http://127.0.0.1:${server.listeningPort}/control","urn:schemas-upnp-org:service:AVTransport:1")
            DlnaCasting.load(device,CastSupport.Media("http://192.0.2.1/video.mp4?a=1&b=2","A & B",position=30_000))
            assertTrue(DlnaCasting.playing(device))
            DlnaCasting.action(device,"Pause"); DlnaCasting.action(device,"Stop")
            assertEquals(listOf("Stop","SetAVTransportURI","Play","Seek","GetTransportInfo","Pause","Stop"),actions.toList())
            assertTrue(bodies[1].contains("video.mp4?a=1&amp;b=2"))
            assertTrue(bodies[3].contains("00:00:30"))
        } finally { server.stop() }
    }
}
