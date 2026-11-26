package com.example.greenstreem

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RecordingLocalPathsTest {
    @Test fun recordingsStayInsideTheChosenFolder() {
        val folder=File(System.getProperty("java.io.tmpdir"),"greenstreem-folder-test")
        assertEquals(File(folder,"Show episode.ts").canonicalFile,RecordingLocalPaths.child(folder,"Show episode.ts"))
        for(name in listOf("",".","..","../other.ts","child/other.ts","child\\other.ts","/other.ts"))
            assertTrue("Reject $name",runCatching { RecordingLocalPaths.child(folder,name) }.isFailure)
    }
}
