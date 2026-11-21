package com.quran.labs.androidquran.presenter.audio

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranFileManager
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.audio.Qari
import com.quran.labs.androidquran.common.audio.cache.AudioCacheInvalidator
import com.quran.labs.androidquran.common.audio.cache.QariDownloadInfoManager
import com.quran.labs.androidquran.common.audio.cache.command.AudioInfoCommand
import com.quran.labs.androidquran.common.audio.cache.command.GappedAudioInfoCommand
import com.quran.labs.androidquran.common.audio.cache.command.GaplessAudioInfoCommand
import com.quran.labs.androidquran.common.audio.extension.isRangeDownloaded
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.util.AudioDecoderCapabilities
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.common.audio.util.QuranAudioExtensionDecider
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.common.download.DownloadInfoStreams
import com.quran.mobile.common.download.Downloader
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.EntryForQari
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhDownloadDialog
import com.quran.mobile.feature.downloadmanager.model.sheikhdownload.SheikhUiModel
import com.quran.mobile.feature.downloadmanager.presenter.AudioManagerPresenter
import com.quran.mobile.feature.downloadmanager.presenter.SheikhAudioPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AudioDownloadRemovalTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  private val qari = Qari(
    id = 1,
    nameResource = 1,
    url = "https://example.com/mp3/",
    opusUrl = "https://example.com/opus/",
    path = "reciter",
    hasGaplessAlternative = false,
    db = "timing"
  )

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun unsupportedOpusCanBeRemovedAndDoesNotPreventMp3Download() = runBlocking {
    val opus = audioFile("001.opus")
    val mp3 = audioFile("002.mp3")
    val fixture = Fixture(qari)
    val state = fixture.state()
    val opusEntry = state.sura(1)
    assertThat(opusEntry.hasLocalFiles).isTrue()
    assertThat(opusEntry.isDownloaded).isFalse()

    val info = audioInfoCommand(qari).generateQariDownloadInfo(qari, temporaryFolder.root.path)
    assertThat(info.surasWithLocalFiles).containsExactly(1, 2)
    assertThat(info.fullyDownloadedSuras).containsExactly(2)
    assertThat(info.isRangeDownloaded(SuraAyah(1, 1), SuraAyah(1, 7))).isFalse()

    fixture.presenter.selectEntry(opusEntry)
    fixture.presenter.selectEntry(state.sura(2))
    fixture.presenter.onDownloadSelection(qari.id)
    assertThat(fixture.downloader.requestedSuras).containsExactly(1)

    fixture.presenter.selectEntry(opusEntry)
    fixture.presenter.onSuraAction(qari.id, opusEntry)
    val dialog = fixture.state().dialog as SheikhDownloadDialog.RemoveConfirmation
    assertThat(dialog.entriesToRemove).containsExactly(opusEntry)
    fixture.confirm(dialog)
    assertThat(opus.exists()).isFalse()
    assertThat(mp3.exists()).isTrue()
  }

  @Test
  fun mixedSelectionRemovesBothFormatsForOnlyTheConfirmedSuras() = runBlocking {
    val mp3 = audioFile("001.mp3")
    val opus = audioFile("001.opus")
    val legacyOpus = audioFile("002.opus")
    val retained = audioFile("004.mp3")
    val database = audioFile("timing.db")
    val fixture = Fixture(qari)
    val state = fixture.state()
    (1..3).forEach { fixture.presenter.selectEntry(state.sura(it)) }
    fixture.presenter.onRemoveSelection()
    val dialog = fixture.state().dialog as SheikhDownloadDialog.RemoveConfirmation
    assertThat(dialog.entriesToRemove).containsExactly(state.sura(1), state.sura(2))

    fixture.presenter.clearSelection()
    fixture.presenter.selectEntry(state.sura(4))
    fixture.confirm(dialog)
    assertThat(listOf(mp3, opus, legacyOpus).any { it.exists() }).isFalse()
    assertThat(retained.exists()).isTrue()
    assertThat(database.exists()).isTrue()
  }

  @Test
  fun emptySelectedRowsAreExcludedFromTheConfirmation() = runBlocking {
    audioFile("001.opus")
    val fixture = Fixture(qari)
    val state = fixture.state()
    fixture.presenter.selectEntry(state.sura(1))
    fixture.presenter.selectEntry(state.sura(2))
    fixture.presenter.onRemoveSelection()

    val dialog = fixture.state().dialog as SheikhDownloadDialog.RemoveConfirmation
    assertThat(dialog.entriesToRemove.single()).isEqualTo(state.sura(1))
  }

  @Test
  fun gappedInventoryIncludesUnsupportedAndPartiallyDownloadedAudio() = runBlocking {
    val gappedQari = qari.copy(db = null, hasGaplessAlternative = true)
    audioFile("103/1.opus")
    audioFile("103/2.opus")
    audioFile("103/3.opus")
    val partial = audioFile("114/1.mp3")
    val fixture = Fixture(gappedQari)
    val state = fixture.state()
    assertThat(state.sura(103).hasLocalFiles).isTrue()
    assertThat(state.sura(103).isDownloaded).isFalse()
    assertThat(state.sura(114).hasLocalFiles).isTrue()
    assertThat(state.sura(114).isDownloaded).isFalse()
    assertThat(fixture.manager.downloadQariInfoFilteringNonDownloadedGappedQaris().first())
      .hasSize(1)

    fixture.presenter.selectEntry(state.sura(103))
    fixture.presenter.onRemoveSelection()
    fixture.confirm(fixture.state().dialog as SheikhDownloadDialog.RemoveConfirmation)
    assertThat(File(temporaryFolder.root, "reciter/103").exists()).isFalse()
    assertThat(partial.exists()).isTrue()
  }

  @Test
  fun gappedOverviewCountsOnlyCompleteSuras() = runBlocking {
    val gappedQari = qari.copy(db = null, hasGaplessAlternative = true)
    (1..3).forEach { audioFile("103/$it.mp3") }
    val partial = audioFile("114/1.mp3")
    val fixture = Fixture(gappedQari)
    val state = fixture.state()
    val overview = AudioManagerPresenter(fixture.manager).downloadedShuyookh { qari ->
      QariItem(
        id = qari.id,
        name = "Reciter",
        url = qari.url,
        opusUrl = qari.opusUrl,
        path = qari.path,
        hasGaplessAlternative = qari.hasGaplessAlternative,
        db = qari.db
      )
    }
    assertThat(overview.first().single().downloadedSuras).isEqualTo(1)
    assertThat(state.sura(114).hasLocalFiles).isTrue()

    fixture.presenter.selectEntry(state.sura(103))
    fixture.presenter.onRemoveSelection()
    fixture.confirm(fixture.state().dialog as SheikhDownloadDialog.RemoveConfirmation)
    assertThat(overview.first().single().downloadedSuras).isEqualTo(0)
    assertThat(partial.exists()).isTrue()
  }

  @Test
  fun localInventorySurvivesCatalogChangesAndExcludesInProgressFiles() {
    audioFile("001.opus")
    audioFile("002.mp3")
    audioFile("002.opus")
    audioFile("003.opus.part")
    val mp3OnlyQari = qari.copy(opusUrl = null)
    val info = audioInfoCommand(mp3OnlyQari).generateQariDownloadInfo(mp3OnlyQari, temporaryFolder.root.path)
    assertThat(info.surasWithLocalFiles).containsExactly(1, 2)
    assertThat(info.fullyDownloadedSuras).containsExactly(2)
  }

  @Test
  fun supportedOpusIsBothLocalAndPlayable() {
    audioFile("001.opus")
    val info = audioInfoCommand(qari, opusSupported = true)
      .generateQariDownloadInfo(qari, temporaryFolder.root.path)
    assertThat(info.surasWithLocalFiles).containsExactly(1)
    assertThat(info.fullyDownloadedSuras).containsExactly(1)
    assertThat(info.isRangeDownloaded(SuraAyah(1, 1), SuraAyah(1, 7))).isTrue()
  }

  private fun audioFile(name: String): File {
    val file = File(temporaryFolder.root, "reciter/$name")
    file.parentFile!!.mkdirs()
    file.writeText("audio")
    return file
  }

  private fun audioInfoCommand(qari: Qari, opusSupported: Boolean = false): AudioInfoCommand {
    val decider = QuranAudioExtensionDecider(object : AudioDecoderCapabilities {
      override fun isOpusSupported() = opusSupported
    })
    return AudioInfoCommand(
      FileSystem.SYSTEM,
      QariUtil(FakePageProvider(listOf(qari))),
      decider,
      GappedAudioInfoCommand(QuranInfo(MadaniDataSource()), FileSystem.SYSTEM),
      GaplessAudioInfoCommand(FileSystem.SYSTEM)
    )
  }

  private fun SheikhUiModel.sura(sura: Int) =
    suraUiModel.filterIsInstance<EntryForQari.SuraForQari>().first { it.sura == sura }

  private inner class Fixture(private val qari: Qari) {
    private val streams = DownloadInfoStreams()
    private val invalidator = AudioCacheInvalidator()
    private val fileManager = AudioDirectoryFileManager(temporaryFolder.root.path)
    val downloader = RecordingDownloader()
    val manager = QariDownloadInfoManager(fileManager, audioInfoCommand(qari), streams, invalidator)
    val presenter = SheikhAudioPresenter(manager, streams, fileManager, invalidator, downloader)

    suspend fun state(): SheikhUiModel = withTimeout(5_000) {
      presenter.sheikhInfo(qari.id).first()
    }

    suspend fun confirm(dialog: SheikhDownloadDialog.RemoveConfirmation) {
      val suras = dialog.entriesToRemove.filterIsInstance<EntryForQari.SuraForQari>().map { it.sura }
      presenter.removeSuras(
        qari.id,
        suras,
        dialog.entriesToRemove.any { it is EntryForQari.DatabaseForQari }
      )
      withTimeout(5_000) {
        manager.downloadedQariInfo().first { downloads ->
          val info = downloads.first { it.qari.id == qari.id }
          suras.none { it in info.surasWithLocalFiles }
        }
      }
    }
  }

  private class RecordingDownloader : Downloader {
    var requestedSuras = emptyList<Int>()
    override fun downloadCompleteSuras(qari: Qari, suras: List<Int>, downloadDatabase: Boolean) {
      requestedSuras = suras
    }
    override fun downloadAudioDatabase(qari: Qari) = error("Unexpected database download")
    override fun cancelDownloads() = error("Unexpected cancellation")
  }

  private class AudioDirectoryFileManager(private val directory: String) : QuranFileManager {
    override fun audioFileDirectory() = directory
    override fun quranImagesDirectory(): File = error("Unused")
    override fun ayahInfoFileDirectory(): File = error("Unused")
    override fun databaseDirectory(): File = error("Unused")
    override fun recitationSessionsDirectory(): String = error("Unused")
    override fun recitationRecordingsDirectory(): String = error("Unused")
    override fun urlForDatabase(qari: Qari): String = error("Unused")
    override fun isVersion(widthParam: String, version: Int): Boolean = error("Unused")
    override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: String) = error("Unused")
    override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: File) = error("Unused")
    override fun copyFromAssetsRelativeRecursive(assetsPath: String, directory: String, destination: String) = error("Unused")
    override fun removeOldArabicDatabase(): Boolean = error("Unused")
    override fun hasArabicSearchDatabase(): Boolean = error("Unused")
    override fun writeVersionFile(widthParam: String, version: Int) = error("Unused")
    override fun removeFilesForWidth(width: Int, directoryLambda: (String) -> String) = error("Unused")
    override fun writeNoMediaFileRelative(widthParam: String) = error("Unused")
    override fun upgradeNonAudioFiles(portraitWidth: String, landscapeWidth: String, totalPages: Int): Boolean = error("Unused")
  }
}
