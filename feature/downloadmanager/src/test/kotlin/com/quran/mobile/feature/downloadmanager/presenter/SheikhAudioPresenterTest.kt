package com.quran.mobile.feature.downloadmanager.presenter

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.model.download.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.mobile.common.download.DownloadConstants
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.feature.downloadmanager.fakes.FakeAudioCacheInvalidator
import com.quran.mobile.feature.downloadmanager.fakes.FakeAudioExtensionDecider
import com.quran.mobile.feature.downloadmanager.fakes.FakeDownloader
import com.quran.mobile.feature.downloadmanager.fakes.FakeQariDownloadInfoSource
import com.quran.mobile.feature.downloadmanager.fakes.FakeQuranFileManager
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.EntryForQari
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhDownloadDialog
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SuraDownloadStatusEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class SheikhAudioPresenterTest {

  private val source = FakeQariDownloadInfoSource()
  private val downloadInfoStreams = DownloadInfoStreams()
  private val fakeFileManager = FakeQuranFileManager()
  private val fakeAudioCacheInvalidator = FakeAudioCacheInvalidator()
  private val fakeExtensionDecider = FakeAudioExtensionDecider()
  private val fakeDownloader = FakeDownloader()

  private lateinit var tempDir: Path

  @Before
  fun setup() {
    tempDir = Files.createTempDirectory("sheikh-audio-test")
    fakeFileManager.audioDirectory = tempDir.toString()
  }

  @After
  fun teardown() {
    tempDir.toFile().deleteRecursively()
  }

  private fun createPresenter() = SheikhAudioPresenter(
    source, downloadInfoStreams, fakeFileManager,
    fakeAudioCacheInvalidator, fakeExtensionDecider, fakeDownloader
  )

  private fun makeGaplessQari(id: Int, db: String = "db_$id") =
    Qari(id, 0, "url/$id", path = "path/$id", hasGaplessAlternative = false, db = db)

  private fun makeGappedQari(id: Int) =
    Qari(id, 0, "url/$id", path = "path/$id", hasGaplessAlternative = false)

  private fun makeGaplessInfo(qari: Qari, fullyDownloaded: List<Int>) =
    QariDownloadInfo.GaplessQariDownloadInfo(qari, fullyDownloaded, emptyList())

  private fun makeGappedInfo(qari: Qari, fullyDownloaded: List<Int>) =
    QariDownloadInfo.GappedQariDownloadInfo(qari, fullyDownloaded, emptyList())

  private fun emitQariInfo(qariInfo: QariDownloadInfo) {
    source.emit(listOf(qariInfo))
  }

  private fun createDatabaseFile(qari: Qari): File {
    val dir = File(tempDir.toFile(), qari.path)
    dir.mkdirs()
    val dbFile = File(dir, "${qari.databaseName}.db")
    dbFile.createNewFile()
    return dbFile
  }

  private fun createSuraFile(qari: Qari, sura: Int, extension: String): File {
    val dir = File(tempDir.toFile(), qari.path)
    dir.mkdirs()
    val suraFile = File(dir, "${sura.toString().padStart(3, '0')}.$extension")
    suraFile.createNewFile()
    return suraFile
  }

  private fun createSuraDirectory(qari: Qari, sura: Int): File {
    val dir = File(File(tempDir.toFile(), qari.path), sura.toString())
    dir.mkdirs()
    File(dir, "dummy.mp3").createNewFile()
    return dir
  }

  // --- sheikhInfo flow ---

  @Test
  fun `sheikhInfo emits model with 114 sura entries plus database entry for gapless qari`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    createPresenter().sheikhInfo(1).test {
      val model = awaitItem()
      assertThat(model.suraUiModel).hasSize(115)
      assertThat(model.suraUiModel[0]).isInstanceOf(EntryForQari.DatabaseForQari::class.java)
      val suraEntries = model.suraUiModel.filterIsInstance<EntryForQari.SuraForQari>()
      assertThat(suraEntries).hasSize(114)
      assertThat(suraEntries.first().sura).isEqualTo(1)
      assertThat(suraEntries.last().sura).isEqualTo(114)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `sheikhInfo marks downloaded suras from fullyDownloadedSuras`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, listOf(1, 2, 114)))

    createPresenter().sheikhInfo(1).test {
      val model = awaitItem()
      val suraEntries = model.suraUiModel.filterIsInstance<EntryForQari.SuraForQari>()
      assertThat(suraEntries.first { it.sura == 1 }.isDownloaded).isTrue()
      assertThat(suraEntries.first { it.sura == 2 }.isDownloaded).isTrue()
      assertThat(suraEntries.first { it.sura == 114 }.isDownloaded).isTrue()
      assertThat(suraEntries.first { it.sura == 3 }.isDownloaded).isFalse()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `sheikhInfo omits database entry for gapped qari`() = runTest {
    val qari = makeGappedQari(1)
    emitQariInfo(makeGappedInfo(qari, emptyList()))

    createPresenter().sheikhInfo(1).test {
      val model = awaitItem()
      assertThat(model.suraUiModel).hasSize(114)
      assertThat(model.suraUiModel.filterIsInstance<EntryForQari.DatabaseForQari>()).isEmpty()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `sheikhInfo database entry reflects file existence`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()

    // Without database file on disk
    presenter.sheikhInfo(1).test {
      val model = awaitItem()
      val dbEntry = model.suraUiModel[0] as EntryForQari.DatabaseForQari
      assertThat(dbEntry.isDownloaded).isFalse()
      cancelAndIgnoreRemainingEvents()
    }

    // Create the database file
    createDatabaseFile(qari)

    // Re-emit to trigger re-evaluation
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    presenter.sheikhInfo(1).test {
      val model = awaitItem()
      val dbEntry = model.suraUiModel[0] as EntryForQari.DatabaseForQari
      assertThat(dbEntry.isDownloaded).isTrue()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `sheikhInfo filters by qariId ignoring other qaris`() = runTest {
    val qari1 = makeGaplessQari(1)
    val qari2 = makeGaplessQari(2)
    source.emit(listOf(makeGaplessInfo(qari1, listOf(1)), makeGaplessInfo(qari2, listOf(2))))

    createPresenter().sheikhInfo(1).test {
      val model = awaitItem()
      assertThat(model.qariItem.id).isEqualTo(1)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `sheikhInfo reflects selection and dialog state changes`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      val initial = awaitItem()
      assertThat(initial.selections).isEmpty()
      assertThat(initial.dialog).isEqualTo(SheikhDownloadDialog.None)

      val entry = EntryForQari.SuraForQari(1, false)
      presenter.selectEntry(entry)
      val afterSelect = awaitItem()
      assertThat(afterSelect.selections).containsExactly(entry)

      presenter.showPostNotificationsRationaleDialog()
      val afterDialog = awaitItem()
      assertThat(afterDialog.dialog).isEqualTo(SheikhDownloadDialog.PostNotificationsPermission)

      cancelAndIgnoreRemainingEvents()
    }
  }

  // --- Selection management ---

  @Test
  fun `selectEntry adds entry to selection`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      val entry = EntryForQari.SuraForQari(1, false)
      presenter.selectEntry(entry)
      val updated = awaitItem()
      assertThat(updated.selections).containsExactly(entry)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `toggleEntrySelection adds unselected entry`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      val entry = EntryForQari.SuraForQari(1, false)
      presenter.toggleEntrySelection(entry)
      val updated = awaitItem()
      assertThat(updated.selections).containsExactly(entry)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `toggleEntrySelection removes already selected entry`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      val entry = EntryForQari.SuraForQari(1, false)
      presenter.selectEntry(entry)
      awaitItem() // after select

      presenter.toggleEntrySelection(entry)
      val toggled = awaitItem()
      assertThat(toggled.selections).isEmpty()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `clearSelection empties the selection list`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.selectEntry(EntryForQari.SuraForQari(1, false))
      awaitItem()
      presenter.selectEntry(EntryForQari.SuraForQari(2, false))
      awaitItem()

      presenter.clearSelection()
      val cleared = awaitItem()
      assertThat(cleared.selections).isEmpty()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `multiple selectEntry calls accumulate entries`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.selectEntry(EntryForQari.SuraForQari(1, false))
      awaitItem()
      presenter.selectEntry(EntryForQari.SuraForQari(2, false))
      awaitItem()
      presenter.selectEntry(EntryForQari.SuraForQari(3, false))
      val result = awaitItem()

      assertThat(result.selections).hasSize(3)
      cancelAndIgnoreRemainingEvents()
    }
  }

  // --- Dialog state management ---

  @Test
  fun `showPostNotificationsRationaleDialog sets correct dialog`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.showPostNotificationsRationaleDialog()
      val updated = awaitItem()
      assertThat(updated.dialog).isEqualTo(SheikhDownloadDialog.PostNotificationsPermission)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `onRemoveSelection sets RemoveConfirmation with current selections`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      val entry1 = EntryForQari.SuraForQari(1, true)
      val entry2 = EntryForQari.SuraForQari(2, true)
      presenter.selectEntry(entry1)
      awaitItem()
      presenter.selectEntry(entry2)
      awaitItem()

      presenter.onRemoveSelection()
      val updated = awaitItem()
      assertThat(updated.dialog).isEqualTo(SheikhDownloadDialog.RemoveConfirmation(listOf(entry1, entry2)))

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `onCancelDialog resets dialog to None`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.showPostNotificationsRationaleDialog()
      awaitItem() // dialog set

      presenter.onCancelDialog()
      val updated = awaitItem()
      assertThat(updated.dialog).isEqualTo(SheikhDownloadDialog.None)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `onDownloadSelection with empty selection shows DownloadRangeSelection`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadSelection(1)
      val updated = awaitItem()
      assertThat(updated.dialog).isEqualTo(SheikhDownloadDialog.DownloadRangeSelection)

      cancelAndIgnoreRemainingEvents()
    }
  }

  // --- Download flow ---

  @Test
  fun `onDownloadSelection with entries clears selection and downloads`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.selectEntry(EntryForQari.SuraForQari(1, false))
      awaitItem()
      presenter.selectEntry(EntryForQari.SuraForQari(2, false))
      awaitItem()
      presenter.selectEntry(EntryForQari.SuraForQari(3, false))
      awaitItem()

      presenter.onDownloadSelection(1)
      // Selection cleared and dialog set to DownloadStatus
      val updated = awaitItem()
      assertThat(updated.selections).isEmpty()

      assertThat(fakeDownloader.downloadCompleteSurasCalls).hasSize(1)
      assertThat(fakeDownloader.downloadCompleteSurasCalls[0].suras).containsExactly(1, 2, 3)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `onDownloadRange delegates to downloader with suras and flag`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(5, 10), true)
      awaitItem() // DownloadStatus dialog

      assertThat(fakeDownloader.downloadCompleteSurasCalls).hasSize(1)
      val call = fakeDownloader.downloadCompleteSurasCalls[0]
      assertThat(call.suras).containsExactly(5, 10)
      assertThat(call.downloadDatabase).isTrue()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `download skips already downloaded suras`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, listOf(1, 2)))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(1, 2, 3, 4), false)
      awaitItem() // DownloadStatus dialog

      assertThat(fakeDownloader.downloadCompleteSurasCalls).hasSize(1)
      assertThat(fakeDownloader.downloadCompleteSurasCalls[0].suras).containsExactly(3, 4)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `download sorts remaining suras`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(5, 3, 1), false)
      awaitItem() // DownloadStatus dialog

      assertThat(fakeDownloader.downloadCompleteSurasCalls[0].suras)
        .containsExactly(1, 3, 5).inOrder()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `download only downloads database when all suras already downloaded`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, listOf(1, 2, 3)))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(1, 2, 3), true)
      awaitItem() // DownloadStatus dialog

      assertThat(fakeDownloader.downloadCompleteSurasCalls).isEmpty()
      assertThat(fakeDownloader.downloadAudioDatabaseCalls).hasSize(1)
      assertThat(fakeDownloader.downloadAudioDatabaseCalls[0].id).isEqualTo(1)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `download does nothing when database exists and only database requested`() = runTest {
    val qari = makeGaplessQari(1)
    createDatabaseFile(qari)
    emitQariInfo(makeGaplessInfo(qari, listOf(1, 2, 3)))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(1, 2, 3), true)
      awaitItem() // DownloadStatus dialog

      assertThat(fakeDownloader.downloadCompleteSurasCalls).isEmpty()
      assertThat(fakeDownloader.downloadAudioDatabaseCalls).isEmpty()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `download does nothing when qariId not found`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(99, listOf(1, 2), false)
      awaitItem() // DownloadStatus dialog

      assertThat(fakeDownloader.downloadCompleteSurasCalls).isEmpty()
      assertThat(fakeDownloader.downloadAudioDatabaseCalls).isEmpty()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `onDownloadSelection with database entry downloads database`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.selectEntry(EntryForQari.DatabaseForQari(false))
      awaitItem()
      presenter.selectEntry(EntryForQari.SuraForQari(1, false))
      awaitItem()

      presenter.onDownloadSelection(1)
      awaitItem() // selection cleared + DownloadStatus

      assertThat(fakeDownloader.downloadCompleteSurasCalls).hasSize(1)
      assertThat(fakeDownloader.downloadCompleteSurasCalls[0].downloadDatabase).isTrue()
      assertThat(fakeDownloader.downloadCompleteSurasCalls[0].suras).containsExactly(1)

      cancelAndIgnoreRemainingEvents()
    }
  }

  // --- Download status events ---

  @Test
  fun `download sets DownloadStatus dialog with progress flow`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(1), false)
      val downloading = awaitItem()
      assertThat(downloading.dialog).isInstanceOf(SheikhDownloadDialog.DownloadStatus::class.java)

      val statusFlow = (downloading.dialog as SheikhDownloadDialog.DownloadStatus).statusFlow
      val progressEvents = mutableListOf<SuraDownloadStatusEvent.Progress>()
      val progressJob = launch(UnconfinedTestDispatcher(testScheduler)) {
        statusFlow.collect { progressEvents.add(it) }
      }

      downloadInfoStreams.emitEvent(
        DownloadInfo.FileDownloadProgress(
          key = "key", type = 0, metadata = AudioDownloadMetadata(1),
          progress = 50, sura = 1, ayah = 1,
          downloadedSize = 500L, totalSize = 1000L,
          currentFile = 1, totalFiles = 1
        )
      )
      // Allow processing
      testScheduler.advanceUntilIdle()

      assertThat(progressEvents).isNotEmpty()
      assertThat(progressEvents[0].progress).isEqualTo(50)

      progressJob.cancel()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `download resets dialog to None on batch success`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(1), false)
      val downloading = awaitItem()
      val statusFlow = (downloading.dialog as SheikhDownloadDialog.DownloadStatus).statusFlow
      val progressJob = launch(UnconfinedTestDispatcher(testScheduler)) { statusFlow.collect { } }

      downloadInfoStreams.emitEvent(
        DownloadInfo.DownloadBatchSuccess(
          key = "key", type = 0, metadata = AudioDownloadMetadata(1)
        )
      )

      val done = awaitItem()
      assertThat(done.dialog).isEqualTo(SheikhDownloadDialog.None)

      progressJob.cancel()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `download sets DownloadError dialog on non-cancelled error`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(1), false)
      val downloading = awaitItem()
      val statusFlow = (downloading.dialog as SheikhDownloadDialog.DownloadStatus).statusFlow
      val progressJob = launch(UnconfinedTestDispatcher(testScheduler)) { statusFlow.collect { } }

      downloadInfoStreams.emitEvent(
        DownloadInfo.DownloadBatchError(
          key = "key", type = 0, metadata = AudioDownloadMetadata(1),
          errorId = DownloadConstants.ERROR_NETWORK, errorResource = 0,
          errorString = "Network error"
        )
      )

      val error = awaitItem()
      assertThat(error.dialog).isEqualTo(
        SheikhDownloadDialog.DownloadError(DownloadConstants.ERROR_NETWORK, "Network error")
      )

      progressJob.cancel()
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `download resets dialog to None on cancelled error`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.onDownloadRange(1, listOf(1), false)
      val downloading = awaitItem()
      val statusFlow = (downloading.dialog as SheikhDownloadDialog.DownloadStatus).statusFlow
      val progressJob = launch(UnconfinedTestDispatcher(testScheduler)) { statusFlow.collect { } }

      downloadInfoStreams.emitEvent(
        DownloadInfo.DownloadBatchError(
          key = "key", type = 0, metadata = AudioDownloadMetadata(1),
          errorId = DownloadConstants.ERROR_CANCELLED, errorResource = 0,
          errorString = "Cancelled"
        )
      )

      val done = awaitItem()
      assertThat(done.dialog).isEqualTo(SheikhDownloadDialog.None)

      progressJob.cancel()
      cancelAndIgnoreRemainingEvents()
    }
  }

  // --- removeSuras ---

  @Test
  fun `removeSuras gapless deletes sura files with all allowed extensions`() = runTest {
    val qari = makeGaplessQari(1)
    fakeExtensionDecider.allowedExtensionsForQariMap[1] = listOf("mp3", "opus")
    emitQariInfo(makeGaplessInfo(qari, listOf(1, 2)))

    val file1mp3 = createSuraFile(qari, 1, "mp3")
    val file1opus = createSuraFile(qari, 1, "opus")
    val file2mp3 = createSuraFile(qari, 2, "mp3")
    val file2opus = createSuraFile(qari, 2, "opus")

    val presenter = createPresenter()
    presenter.removeSuras(1, listOf(1, 2), false)

    assertThat(file1mp3.exists()).isFalse()
    assertThat(file1opus.exists()).isFalse()
    assertThat(file2mp3.exists()).isFalse()
    assertThat(file2opus.exists()).isFalse()
  }

  @Test
  fun `removeSuras gapless deletes database when removeDatabase is true`() = runTest {
    val qari = makeGaplessQari(1)
    fakeExtensionDecider.allowedExtensionsForQariMap[1] = listOf("mp3")
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val dbFile = createDatabaseFile(qari)
    assertThat(dbFile.exists()).isTrue()

    val presenter = createPresenter()
    presenter.removeSuras(1, emptyList(), true)

    assertThat(dbFile.exists()).isFalse()
  }

  @Test
  fun `removeSuras gapless preserves database when removeDatabase is false`() = runTest {
    val qari = makeGaplessQari(1)
    fakeExtensionDecider.allowedExtensionsForQariMap[1] = listOf("mp3")
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val dbFile = createDatabaseFile(qari)

    val presenter = createPresenter()
    presenter.removeSuras(1, emptyList(), false)

    assertThat(dbFile.exists()).isTrue()
  }

  @Test
  fun `removeSuras gapped deletes sura directories recursively`() = runTest {
    val qari = makeGappedQari(1)
    emitQariInfo(makeGappedInfo(qari, listOf(1, 2)))

    val dir1 = createSuraDirectory(qari, 1)
    val dir2 = createSuraDirectory(qari, 2)
    assertThat(dir1.exists()).isTrue()
    assertThat(dir2.exists()).isTrue()

    val presenter = createPresenter()
    presenter.removeSuras(1, listOf(1, 2), false)

    assertThat(dir1.exists()).isFalse()
    assertThat(dir2.exists()).isFalse()
  }

  @Test
  fun `removeSuras invalidates cache and resets state`() = runTest {
    val qari = makeGaplessQari(1)
    fakeExtensionDecider.allowedExtensionsForQariMap[1] = listOf("mp3")
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.selectEntry(EntryForQari.SuraForQari(1, true))
      awaitItem()
      presenter.showPostNotificationsRationaleDialog()
      awaitItem()

      presenter.removeSuras(1, listOf(1), false)

      // First emission: selections cleared (dialog still PostNotificationsPermission)
      val selectionCleared = awaitItem()
      assertThat(selectionCleared.selections).isEmpty()

      // Second emission: dialog reset to None (after IO work completes)
      val dialogReset = awaitItem()
      assertThat(dialogReset.dialog).isEqualTo(SheikhDownloadDialog.None)
      assertThat(fakeAudioCacheInvalidator.invalidatedQariIds).containsExactly(1)

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `removeSuras does nothing when audio directory is null`() = runTest {
    val qari = makeGaplessQari(1)
    fakeExtensionDecider.allowedExtensionsForQariMap[1] = listOf("mp3")
    emitQariInfo(makeGaplessInfo(qari, emptyList()))
    fakeFileManager.audioDirectory = null

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.selectEntry(EntryForQari.SuraForQari(1, true))
      awaitItem()

      presenter.removeSuras(1, listOf(1), false)

      // Single emission: selections cleared (dialog was already None, so no second emission)
      val updated = awaitItem()
      assertThat(updated.selections).isEmpty()
      assertThat(updated.dialog).isEqualTo(SheikhDownloadDialog.None)
      assertThat(fakeAudioCacheInvalidator.invalidatedQariIds).isEmpty()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `removeSuras does nothing when qariId not found`() = runTest {
    val qari = makeGaplessQari(1)
    fakeExtensionDecider.allowedExtensionsForQariMap[1] = listOf("mp3")
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      presenter.selectEntry(EntryForQari.SuraForQari(1, true))
      awaitItem()

      presenter.removeSuras(99, listOf(1), false)

      // Single emission: selections cleared (dialog was already None, so no second emission)
      val updated = awaitItem()
      assertThat(updated.selections).isEmpty()
      assertThat(updated.dialog).isEqualTo(SheikhDownloadDialog.None)
      assertThat(fakeAudioCacheInvalidator.invalidatedQariIds).isEmpty()

      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `removeSuras gapless skips database deletion when databaseName is null`() = runTest {
    val qari = makeGappedQari(1) // gapped = no databaseName
    emitQariInfo(makeGappedInfo(qari, listOf(1)))

    val suraDir = createSuraDirectory(qari, 1)
    assertThat(suraDir.exists()).isTrue()

    val presenter = createPresenter()
    presenter.removeSuras(1, listOf(1), true)

    // Gapped qari deletes directories, no database to delete
    assertThat(suraDir.exists()).isFalse()
    assertThat(fakeAudioCacheInvalidator.invalidatedQariIds).containsExactly(1)
  }

  // --- cancelDownloads and onSuraAction ---

  @Test
  fun `cancelDownloads delegates to downloader`() = runTest {
    val presenter = createPresenter()
    presenter.cancelDownloads()
    assertThat(fakeDownloader.cancelDownloadsCalled).isEqualTo(1)
  }

  @Test
  fun `onSuraAction routes based on entry download state`() = runTest {
    val qari = makeGaplessQari(1)
    emitQariInfo(makeGaplessInfo(qari, emptyList()))

    val presenter = createPresenter()
    presenter.sheikhInfo(1).test {
      awaitItem() // initial

      // Downloaded entry triggers RemoveConfirmation
      val downloadedEntry = EntryForQari.SuraForQari(1, isDownloaded = true)
      presenter.selectEntry(downloadedEntry)
      awaitItem()

      presenter.onSuraAction(1, downloadedEntry)
      val removeDialog = awaitItem()
      assertThat(removeDialog.dialog).isInstanceOf(SheikhDownloadDialog.RemoveConfirmation::class.java)

      presenter.onCancelDialog()
      awaitItem()
      presenter.clearSelection()
      awaitItem()

      // Not-downloaded entry with empty selection triggers DownloadRangeSelection
      val notDownloadedEntry = EntryForQari.SuraForQari(1, isDownloaded = false)
      presenter.onSuraAction(1, notDownloadedEntry)
      val downloadDialog = awaitItem()
      assertThat(downloadDialog.dialog).isEqualTo(SheikhDownloadDialog.DownloadRangeSelection)

      cancelAndIgnoreRemainingEvents()
    }
  }
}
