package org.quran.app.data

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest

class JvmRecitationFileCacheTest {
    @Test fun completeFilesSurviveCacheRecreationAndCanBeDeleted() = runTest {
        val directory = Files.createTempDirectory("recitation-test").toFile()
        try {
            val key = "alafasy-018075.mp3"
            val first = JvmRecitationFileCache(directory)
            val uri = first.writeAtomic(key, syntheticMp3())
            val restarted = JvmRecitationFileCache(directory)
            assertEquals(uri, restarted.localUri(key))
            assertContentEquals(syntheticMp3(), restarted.read(key))
            assertTrue(uri.startsWith("file://"))
            assertEquals(listOf(key), directory.list()?.toList())
            restarted.remove(key)
            assertNull(first.localUri(key))
            assertFailsWith<IllegalArgumentException> { restarted.read("../escape.mp3") }
        } finally { directory.deleteRecursively() }
    }

    @Test fun cancelledWriteDoesNotPublishOrLeaveTemporaryFiles() = runTest {
        val directory = Files.createTempDirectory("recitation-cancel-test").toFile()
        try {
            val cache = JvmRecitationFileCache(directory)
            val job = launch(start = CoroutineStart.LAZY) { cache.writeAtomic("alafasy-001001.mp3", syntheticMp3()) }
            job.cancelAndJoin()
            assertNull(cache.localUri("alafasy-001001.mp3"))
            assertTrue(directory.list().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
