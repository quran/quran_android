package org.quran.app.data

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class JvmRecitationStorageInventoryTest {
    @Test fun durableMetadataSurvivesRestartAndOnlyRegularOwnedFilesAreListed() = runTest {
        val root = Files.createTempDirectory("recitation-inventory").toFile()
        val external = Files.createTempFile("external-recording", ".mp3").toFile()
        try {
            val first = JvmRecitationFileCache(root)
            first.writeAtomic("alafasy-001001.mp3", syntheticMp3())
            root.resolve("recitation-old.part").writeBytes(ByteArray(7))
            root.resolve("foreign.wav").writeBytes(ByteArray(8))
            root.resolve("husary-001002.mp3").mkdir()
            external.writeBytes(syntheticMp3())
            Files.createSymbolicLink(root.resolve("sudais-001001.mp3").toPath(), external.toPath())
            val restarted = JvmRecitationFileCache(root)
            assertEquals(listOf(RecitationCacheEntry("alafasy-001001.mp3", 834)), restarted.entries())
            assertNull(restarted.localUri("sudais-001001.mp3"))
            assertNull(restarted.read("sudais-001001.mp3"))
            restarted.remove("alafasy-001001.mp3")
            assertTrue(first.entries().isEmpty())
            assertTrue(external.isFile)
            assertEquals(834L, external.length())
        } finally { root.deleteRecursively(); external.delete() }
    }
}
