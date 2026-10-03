package org.quran.app.memorization

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*
import org.quran.app.domain.RecitationRepository
import org.quran.app.model.Reciter
import org.quran.app.model.VerseId

class RecitationAudioControllerTest {
    @Test fun cachedAudioLoadsWithoutADownload() = runTest {
        val repository = FakeAudioRepository(cachedUri = "file:///cached.mp3")
        val loaded = mutableListOf<String>()
        val controller = RecitationAudioController(repository, this, loaded::add)
        controller.select("alafasy", VerseId(1, 1))
        controller.prepareAudio()
        runCurrent()
        assertEquals(listOf("file:///cached.mp3"), loaded)
        assertEquals(0, repository.downloads)
    }

    @Test fun changingSelectionPreventsLateDownloadFromReplacingCurrentAudio() = runTest {
        val repository = FakeAudioRepository(ignoreCancellation = true)
        val loaded = mutableListOf<String>()
        val controller = RecitationAudioController(repository, this, loaded::add)
        controller.select("alafasy", VerseId(1, 1))
        controller.prepareAudio()
        runCurrent()
        controller.select("husary", VerseId(1, 1))
        repository.response.complete("file:///old.mp3")
        runCurrent()
        assertTrue(loaded.isEmpty())
        assertEquals("husary", controller.state.value.reciterId)
        assertFalse(controller.state.value.isDownloading)
    }

    @Test fun disposingPreventsLateDownloadFromStartingPlayback() = runTest {
        val repository = FakeAudioRepository(ignoreCancellation = true)
        val loaded = mutableListOf<String>()
        val controller = RecitationAudioController(repository, this, loaded::add)
        controller.select("alafasy", VerseId(1, 1))
        controller.prepareAudio()
        runCurrent()
        controller.close()
        repository.response.complete("file:///late.mp3")
        runCurrent()
        assertTrue(loaded.isEmpty())
    }

    @Test fun downloadFailureAllowsAnExplicitRetryAndNeverLoadsAudio() = runTest {
        val repository = FakeAudioRepository()
        val loaded = mutableListOf<String>()
        val controller = RecitationAudioController(repository, this, loaded::add)
        controller.select("alafasy", VerseId(1, 1))
        controller.prepareAudio()
        repository.response.completeExceptionally(IllegalStateException("offline"))
        runCurrent()
        assertTrue(controller.state.value.failed)
        assertFalse(controller.state.value.isDownloading)
        assertTrue(loaded.isEmpty())
        controller.prepareAudio()
        runCurrent()
        assertEquals(2, repository.downloads)
    }

    @Test fun nativeLoadFailureIsRecoverableAndNeverReportsReady() = runTest {
        val repository = FakeAudioRepository(cachedUri = "file:///cached.mp3")
        var attempts = 0
        val controller = RecitationAudioController(repository, this, {
            attempts++
            error("decoder rejected file")
        })
        controller.select("alafasy", VerseId(1, 1))
        controller.prepareAudio()
        runCurrent()
        assertTrue(controller.state.value.failed)
        assertFalse(controller.state.value.isDownloading)
        controller.prepareAudio()
        runCurrent()
        assertEquals(2, attempts)
    }

    private class FakeAudioRepository(
        private val cachedUri: String? = null,
        private val ignoreCancellation: Boolean = false,
    ) : RecitationRepository {
        val response = CompletableDeferred<String>()
        var downloads = 0
        override fun reciters(): List<Reciter> = emptyList()
        override fun remove(reciterId: String, verseId: VerseId) = Unit
        override fun cached(reciterId: String, verseId: VerseId) = cachedUri
        override suspend fun download(reciterId: String, verseId: VerseId): String {
            cachedUri?.let { return it }
            downloads++
            return if (ignoreCancellation) withContext(NonCancellable) { response.await() } else response.await()
        }
    }
}
