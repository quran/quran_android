package org.quran.app.downloads

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import org.quran.app.model.*

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagementControllerTest {
    private val file = RecitationDownload("alafasy", VerseId(1, 1), 834)

    @Test fun refreshPublishesCompletedStorageInventory() = runTest {
        val storage = FakeRecitationStorage(listOf(file))
        val controller = DownloadManagementController(storage, this)
        controller.refresh()
        assertTrue(controller.state.value.isLoading)
        runCurrent()
        assertEquals(listOf(file), controller.state.value.snapshot?.entries)
        assertEquals(834L, controller.state.value.snapshot?.totalBytes)
        assertFalse(controller.state.value.isLoading)
    }

    @Test fun removalRefreshesTotalsWithoutTouchingOtherDownloads() = runTest {
        val other = file.copy(verseId = VerseId(1, 2), sizeBytes = 1200)
        val storage = FakeRecitationStorage(listOf(file, other))
        val controller = DownloadManagementController(storage, this)
        controller.refresh(); runCurrent()
        controller.remove(file); runCurrent()
        assertEquals(listOf(other), controller.state.value.snapshot?.entries)
        assertEquals(1200L, controller.state.value.snapshot?.totalBytes)
        assertEquals(DownloadManagementNotice.REMOVED, controller.state.value.notice)
        assertNull(controller.state.value.removing)
    }

    @Test fun protectedRemovalLeavesInventoryAndExplainsWhy() = runTest {
        val storage = FakeRecitationStorage(listOf(file)).apply { result = RecitationRemovalResult.IN_USE }
        val controller = DownloadManagementController(storage, this)
        controller.refresh(); runCurrent()
        controller.remove(file); runCurrent()
        assertEquals(listOf(file), controller.state.value.snapshot?.entries)
        assertEquals(DownloadManagementNotice.IN_USE, controller.state.value.notice)
    }

    @Test fun alreadyMissingFileRefreshesStaleInventory() = runTest {
        val storage = FakeRecitationStorage(listOf(file))
        val controller = DownloadManagementController(storage, this)
        controller.refresh(); runCurrent()
        storage.entries = emptyList()
        storage.result = RecitationRemovalResult.NOT_FOUND
        controller.remove(file); runCurrent()
        assertTrue(controller.state.value.snapshot!!.entries.isEmpty())
        assertEquals(DownloadManagementNotice.NOT_FOUND, controller.state.value.notice)
    }

    @Test fun failedRefreshKeepsPreviousSnapshotAndAllowsRetry() = runTest {
        val storage = FakeRecitationStorage(listOf(file))
        val controller = DownloadManagementController(storage, this)
        controller.refresh(); runCurrent()
        storage.inventoryAction = { error("Unreadable storage") }
        controller.refresh(); runCurrent()
        assertEquals(listOf(file), controller.state.value.snapshot?.entries)
        assertEquals(DownloadManagementNotice.FAILED, controller.state.value.notice)
        assertFalse(controller.state.value.isLoading)
        storage.inventoryAction = null
        controller.refresh(); runCurrent()
        assertNull(controller.state.value.notice)
    }

    @Test fun failedPostRemovalRefreshCanRecoverTheActualInventory() = runTest {
        val storage = FakeRecitationStorage(listOf(file))
        val controller = DownloadManagementController(storage, this)
        controller.refresh(); runCurrent()
        storage.inventoryAction = { error("Unreadable storage") }
        controller.remove(file); runCurrent()
        assertEquals(DownloadManagementNotice.REMOVE_FAILED, controller.state.value.notice)
        assertFalse(controller.state.value.isBusy)
        assertEquals(listOf(file), controller.state.value.snapshot?.entries)
        assertTrue(storage.entries.isEmpty())
        storage.inventoryAction = null
        controller.refresh(); runCurrent()
        assertTrue(controller.state.value.snapshot!!.entries.isEmpty())
        assertNull(controller.state.value.notice)
    }

    @Test fun duplicateRemovalCannotStartWhileOneIsInFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        val storage = FakeRecitationStorage(listOf(file)).apply { removalGate = gate }
        val controller = DownloadManagementController(storage, this)
        controller.remove(file); runCurrent()
        controller.remove(file); runCurrent()
        assertEquals(1, storage.removals)
        gate.complete(Unit); runCurrent()
        assertEquals(DownloadManagementNotice.REMOVED, controller.state.value.notice)
    }

    @Test fun closeSuppressesEvenAnUncooperativeLateInventory() = runTest {
        val gate = CompletableDeferred<Unit>()
        val storage = FakeRecitationStorage(listOf(file)).apply {
            inventoryAction = { withContext(NonCancellable) { gate.await(); snapshot() } }
        }
        val controller = DownloadManagementController(storage, this)
        controller.refresh(); runCurrent()
        controller.close()
        gate.complete(Unit); runCurrent()
        assertNull(controller.state.value.snapshot)
        assertFalse(controller.state.value.isLoading)
    }

    @Test fun canceledScopeNeverLeavesLoadingStuck() = runTest {
        val job = SupervisorJob().apply { cancel() }
        val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
        val controller = DownloadManagementController(FakeRecitationStorage(listOf(file)), scope)
        controller.refresh(); runCurrent()
        assertFalse(controller.state.value.isLoading)
        assertNull(controller.state.value.snapshot)
    }
}
