package org.quran.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.quran.app.model.VerseId

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderListeningControllerTest {
    private val first = VerseId(1, 1)
    private val second = VerseId(1, 2)

    @Test fun exactAyahIsProtectedDownloadedPlayedAndClearedBeforeRelease() = runTest {
        val events = mutableListOf<String>()
        val repository = ReaderListeningRepositoryFake()
        val storage = ReaderListeningStorageFake(events)
        val player = ReaderListeningPlayerFake(events)
        val controller = ReaderListeningController(repository, storage, player, this)
        try {
            controller.listen("alafasy", second); runCurrent()
            assertEquals(listOf("alafasy" to second), repository.requests)
            assertEquals(listOf(listOf(second)), storage.protectedVerses)
            assertTrue(controller.state.value.isPlaying)
            controller.togglePlayback(); runCurrent()
            assertFalse(controller.state.value.isPlaying)
            assertEquals(1, storage.protected)
            controller.stop(); runCurrent()
            assertEquals(0, storage.protected)
            assertNull(controller.state.value.verseId)
            assertTrue(events.indexOf("clear") < events.indexOf("release"))
            assertFalse("terminal-release" in events)
        } finally { controller.close() }
    }

    @Test fun naturalCompletionStopsAfterOneAyahAndReplayUsesPreparedSource() = runTest {
        val events = mutableListOf<String>()
        val repository = ReaderListeningRepositoryFake()
        val player = ReaderListeningPlayerFake(events)
        val controller = ReaderListeningController(repository, ReaderListeningStorageFake(events), player, this)
        try {
            controller.listen("husary", first); runCurrent()
            player.completions.single().invoke(); runCurrent()
            assertFalse(controller.state.value.isPlaying)
            assertTrue(controller.state.value.isReady)
            controller.togglePlayback(); runCurrent()
            assertTrue(controller.state.value.isPlaying)
            assertEquals(2, player.completions.size)
            assertEquals(1, repository.requests.size)
        } finally { controller.close() }
    }

    @Test fun systemMediaPauseAndResumeStayInSyncWithReaderControls() = runTest {
        val repository = ReaderListeningRepositoryFake()
        val player = ReaderListeningPlayerFake(mutableListOf())
        val controller = ReaderListeningController(repository, ReaderListeningStorageFake(mutableListOf()), player, this)
        try {
            controller.listen("husary", first); runCurrent()
            assertTrue(controller.state.value.isPlaying)

            player.playbackChanges.single()(false)
            assertFalse(controller.state.value.isPlaying)
            player.playbackChanges.single()(true)
            assertTrue(controller.state.value.isPlaying)

            player.completions.single()()
            runCurrent()
            assertFalse(controller.state.value.isPlaying)
            assertTrue(controller.state.value.isReady)
            controller.togglePlayback()
            assertTrue(controller.state.value.isPlaying)
            assertEquals(1, repository.requests.size)
        } finally { controller.close() }
    }

    @Test fun audioInterruptionPausesReaderStateAndSystemResumeRestoresIt() = runTest {
        val player = ReaderListeningPlayerFake(mutableListOf())
        val controller = ReaderListeningController(
            ReaderListeningRepositoryFake(), ReaderListeningStorageFake(mutableListOf()), player, this,
        )
        try {
            controller.listen("husary", first); runCurrent()
            val interruptionCallback = player.playbackChanges.single()

            interruptionCallback(false)
            assertFalse(controller.state.value.isPlaying)
            assertEquals(first, controller.state.value.verseId)

            interruptionCallback(true)
            assertTrue(controller.state.value.isPlaying)
            assertEquals(first, controller.state.value.verseId)
        } finally { controller.close() }
    }

    @Test fun sourceChangeSuppressesNonCooperativeOldDownloadAndCompletion() = runTest {
        val events = mutableListOf<String>()
        val gate = CompletableDeferred<String>()
        val repository = ReaderListeningRepositoryFake().apply {
            fetch = { _, verse -> if (verse == first) withContext(NonCancellable) { gate.await() } else "file:///second.mp3" }
        }
        val storage = ReaderListeningStorageFake(events)
        val player = ReaderListeningPlayerFake(events)
        val controller = ReaderListeningController(repository, storage, player, this)
        try {
            controller.listen("alafasy", first); runCurrent()
            controller.listen("sudais", second); runCurrent()
            gate.complete("file:///stale.mp3"); runCurrent()
            assertEquals(second, controller.state.value.verseId)
            assertEquals(listOf("file:///second.mp3"), player.loaded)
            assertEquals(1, storage.protected)
            val oldCompletion = player.completions.single()
            controller.stop(); runCurrent()
            oldCompletion(); runCurrent()
            assertNull(controller.state.value.verseId)
            assertFalse(controller.state.value.isPlaying)
            assertEquals(0, storage.protected)
        } finally { gate.complete("file:///stale.mp3"); controller.close() }
    }

    @Test fun failedDownloadReleasesProtectionAndRetryIsExplicit() = runTest {
        val events = mutableListOf<String>()
        val repository = ReaderListeningRepositoryFake().apply { fetch = { _, _ -> error("offline") } }
        val storage = ReaderListeningStorageFake(events)
        val controller = ReaderListeningController(repository, storage, ReaderListeningPlayerFake(events), this)
        try {
            controller.listen("alafasy", first); runCurrent()
            assertTrue(controller.state.value.failed)
            assertFalse(controller.state.value.isPreparing)
            assertEquals(0, storage.protected)
            repository.fetch = { _, _ -> "file:///first.mp3" }
            controller.retry(); runCurrent()
            assertTrue(controller.state.value.isPlaying)
            assertFalse(controller.state.value.failed)
            assertEquals(1, storage.protected)
        } finally { controller.close() }
    }

    @Test fun stoppingPreparationCancelsDownloadAndDoesNotPublishAudio() = runTest {
        val events = mutableListOf<String>()
        val gate = CompletableDeferred<String>()
        val repository = ReaderListeningRepositoryFake().apply { fetch = { _, _ -> gate.await() } }
        val storage = ReaderListeningStorageFake(events)
        val player = ReaderListeningPlayerFake(events)
        val controller = ReaderListeningController(repository, storage, player, this)
        try {
            controller.listen("alafasy", first); runCurrent()
            assertTrue(controller.state.value.isPreparing)
            controller.stop(); runCurrent()
            assertEquals(0, storage.protected)
            assertTrue(player.loaded.isEmpty())
            assertNull(controller.state.value.verseId)
        } finally { controller.close() }
    }

    @Test fun nativeFailureIsVisibleAndRetryClearsOldSourceBeforeNewPreparation() = runTest {
        val events = mutableListOf<String>()
        val storage = ReaderListeningStorageFake(events)
        val player = ReaderListeningPlayerFake(events).apply { failPlayback = true }
        val controller = ReaderListeningController(ReaderListeningRepositoryFake(), storage, player, this)
        try {
            controller.listen("alafasy", first); runCurrent()
            assertTrue(controller.state.value.failed)
            assertFalse(controller.state.value.isPlaying)
            assertEquals(1, storage.protected)
            player.failPlayback = false
            controller.retry(); runCurrent()
            assertFalse(controller.state.value.failed)
            assertTrue(controller.state.value.isPlaying)
            assertEquals(1, storage.protected)
            assertTrue(events.indexOf("clear") < events.indexOf("release"))
        } finally { controller.close() }
    }

    @Test fun closingIdleControllerDoesNotClearAnUnownedNativePlayer() = runTest {
        val events = mutableListOf<String>()
        val controller = ReaderListeningController(ReaderListeningRepositoryFake(), ReaderListeningStorageFake(events), ReaderListeningPlayerFake(events), this)
        runCurrent()
        controller.close(); controller.close(); runCurrent()
        assertTrue(events.isEmpty())
    }

    @Test fun disposedOrCanceledScopeCannotStartPlayback() = runTest {
        val events = mutableListOf<String>()
        val repository = ReaderListeningRepositoryFake()
        val scope = CoroutineScope(SupervisorJob().apply { cancel() } + StandardTestDispatcher(testScheduler))
        val controller = ReaderListeningController(repository, ReaderListeningStorageFake(events), ReaderListeningPlayerFake(events), scope)
        controller.listen("alafasy", first); runCurrent()
        assertTrue(repository.requests.isEmpty())
        assertFalse(controller.state.value.isPreparing)
        controller.close()
        controller.listen("alafasy", second); runCurrent()
        assertTrue(repository.requests.isEmpty())
    }
}
