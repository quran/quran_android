package com.quran.labs.androidquran.common.audio.cache

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranFileManager
import com.quran.data.core.QuranInfo
import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.common.audio.cache.command.AudioInfoCommand
import com.quran.labs.androidquran.common.audio.cache.command.GaplessAudioInfoCommand
import com.quran.labs.androidquran.common.audio.cache.command.GappedAudioInfoCommand
import com.quran.labs.androidquran.common.audio.model.download.AudioDownloadMetadata
import com.quran.labs.androidquran.common.audio.util.AudioDecoderCapabilities
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.common.audio.util.QuranAudioExtensionDecider
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.mobile.common.download.DownloadInfo
import com.quran.mobile.common.download.DownloadInfoStreams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class QariDownloadInfoManagerTest {

  private val audioDir = "/quran/audio"
  private val qari = Qari(
    id = 1,
    nameResource = 1,
    url = "https://example.com/mp3/",
    opusUrl = null,
    path = "test_qari",
    hasGaplessAlternative = false,
    db = "test_qari.db",
  )

  @Test
  fun `coalesces FileDownloaded bursts into single rescan after window`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val fakeFs = FakeFileSystem()
    val qariPath = audioDir.toPath() / qari.path
    fakeFs.createDirectories(qariPath)
    val countingFs = CountingFileSystem(fakeFs)
    val streams = DownloadInfoStreams()
    val invalidator = AudioCacheInvalidator()

    val manager = QariDownloadInfoManager.forTest(
      quranFileManager = FakeQuranFileManager(audioDir),
      audioInfoCommand = createAudioInfoCommand(countingFs),
      downloadInfoStreams = streams,
      audioCacheInvalidator = invalidator,
      scope = backgroundScope,
      ioDispatcher = dispatcher,
      coalesceWindowMs = 300L,
    )

    runCurrent()
    val initialListCount = countingFs.listCounts[qariPath] ?: 0
    assertThat(manager.downloadedQariInfo().first().first().fullyDownloadedSuras).isEmpty()

    // Simulate burst of 5 downloaded files
    for (sura in 1..5) {
      val name = sura.toString().padStart(3, '0') + ".mp3"
      fakeFs.write(qariPath / name) { }
      streams.emitEvent(
        DownloadInfo.FileDownloaded(
          key = "k$sura",
          type = 0,
          metadata = AudioDownloadMetadata(qari.id),
          filename = name,
          sura = sura,
          ayah = null,
        )
      )
      runCurrent()
    }

    // Before coalesce window expires, no rescan has happened yet
    assertThat(countingFs.listCounts[qariPath] ?: 0).isEqualTo(initialListCount)

    // After coalesce window expires, a single rescan picks up all 5 files
    advanceTimeBy(300L)
    runCurrent()

    assertThat(manager.downloadedQariInfo().first().first().fullyDownloadedSuras)
      .containsExactly(1, 2, 3, 4, 5)
    // Only 1 refresh cycle (2 list calls because mp3 != [mp3, opus]) instead of 5 cycles (10 list calls)
    assertThat((countingFs.listCounts[qariPath] ?: 0) - initialListCount).isEqualTo(2)
  }

  @Test
  fun `DownloadBatchSuccess flushes pending FileDownloaded immediately`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val fakeFs = FakeFileSystem()
    val qariPath = audioDir.toPath() / qari.path
    fakeFs.createDirectories(qariPath)
    val streams = DownloadInfoStreams()
    val invalidator = AudioCacheInvalidator()

    val manager = QariDownloadInfoManager.forTest(
      quranFileManager = FakeQuranFileManager(audioDir),
      audioInfoCommand = createAudioInfoCommand(fakeFs),
      downloadInfoStreams = streams,
      audioCacheInvalidator = invalidator,
      scope = backgroundScope,
      ioDispatcher = dispatcher,
      coalesceWindowMs = 5_000L,
    )

    runCurrent()
    fakeFs.write(qariPath / "001.mp3") { }
    streams.emitEvent(
      DownloadInfo.FileDownloaded(
        key = "k1",
        type = 0,
        metadata = AudioDownloadMetadata(qari.id),
        filename = "001.mp3",
        sura = 1,
        ayah = null,
      )
    )
    runCurrent()

    streams.emitEvent(
      DownloadInfo.DownloadBatchSuccess(
        key = "batch1",
        type = 0,
        metadata = AudioDownloadMetadata(qari.id),
      )
    )
    runCurrent()

    // Flushed immediately without waiting 5_000ms
    assertThat(manager.downloadedQariInfo().first().first().fullyDownloadedSuras).containsExactly(1)
  }

  @Test
  fun `AudioCacheInvalidator refreshes qari immediately`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val fakeFs = FakeFileSystem()
    val qariPath = audioDir.toPath() / qari.path
    fakeFs.createDirectories(qariPath)
    fakeFs.write(qariPath / "001.mp3") { }
    val streams = DownloadInfoStreams()
    val invalidator = AudioCacheInvalidator()

    val manager = QariDownloadInfoManager.forTest(
      quranFileManager = FakeQuranFileManager(audioDir),
      audioInfoCommand = createAudioInfoCommand(fakeFs),
      downloadInfoStreams = streams,
      audioCacheInvalidator = invalidator,
      scope = backgroundScope,
      ioDispatcher = dispatcher,
      coalesceWindowMs = 5_000L,
    )

    runCurrent()
    assertThat(manager.downloadedQariInfo().first().first().fullyDownloadedSuras).containsExactly(1)

    fakeFs.delete(qariPath / "001.mp3")
    invalidator.invalidateCacheForQari(qari.id)
    runCurrent()

    assertThat(manager.downloadedQariInfo().first().first().fullyDownloadedSuras).isEmpty()
  }

  @Test
  fun `populateCache falls back to qariUtil catalog with empty downloads when audioFileDirectory is null`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val fakeFs = FakeFileSystem()
    val gappedQari = Qari(
      id = 2,
      nameResource = 2,
      url = "https://example.com/gapped/",
      opusUrl = null,
      path = "2",
      hasGaplessAlternative = false,
      db = null,
    )
    val qaris = listOf(qari, gappedQari)
    val qariUtil = QariUtil(FakePageProvider(qaris))
    val streams = DownloadInfoStreams()
    val invalidator = AudioCacheInvalidator()

    val manager = QariDownloadInfoManager.forTest(
      quranFileManager = FakeQuranFileManager(null),
      audioInfoCommand = createAudioInfoCommand(fakeFs, qaris),
      downloadInfoStreams = streams,
      audioCacheInvalidator = invalidator,
      scope = backgroundScope,
      ioDispatcher = dispatcher,
      coalesceWindowMs = 300L,
      qariUtil = qariUtil,
    )

    runCurrent()

    val info = manager.downloadedQariInfo().first()
    assertThat(info.map { it.qari.id }).containsExactly(1, 2).inOrder()
    assertThat(info.all { it.fullyDownloadedSuras.isEmpty() && it.surasWithLocalFiles.isEmpty() }).isTrue()
    val filtered = manager.downloadQariInfoFilteringNonDownloadedGappedQaris().first()
    assertThat(filtered.map { it.qari.id }).containsExactly(1, 2).inOrder()
  }

  private fun createAudioInfoCommand(
    fileSystem: FileSystem,
    qaris: List<Qari> = listOf(qari)
  ): AudioInfoCommand {
    val quranInfo = QuranInfo(MadaniDataSource())
    val decider = QuranAudioExtensionDecider(
      object : AudioDecoderCapabilities {
        override fun isOpusSupported(): Boolean = false
      }
    )
    return AudioInfoCommand(
      fileSystem = fileSystem,
      qariUtil = QariUtil(FakePageProvider(qaris)),
      audioExtensionDecider = decider,
      gappedAudioInfoCommand = GappedAudioInfoCommand(quranInfo, fileSystem),
      gaplessAudioInfoCommand = GaplessAudioInfoCommand(fileSystem),
    )
  }

  private class CountingFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
    val listCounts = mutableMapOf<Path, Int>()

    override fun listOrNull(dir: Path): List<Path>? {
      listCounts[dir] = (listCounts[dir] ?: 0) + 1
      return super.listOrNull(dir)
    }
  }

  private class FakeQuranFileManager(private val audioDir: String?) : QuranFileManager {
    override fun quranImagesDirectory(): File = File("/images")
    override fun ayahInfoFileDirectory(): File = File("/ayahinfo")
    override fun audioFileDirectory(): String? = audioDir
    override fun databaseDirectory(): File = File("/db")
    override fun recitationSessionsDirectory(): String = "/recitation/sessions"
    override fun recitationRecordingsDirectory(): String = "/recitation/recordings"
    override fun urlForDatabase(qari: Qari): String = ""
    override fun isVersion(widthParam: String, version: Int): Boolean = true
    override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: String) {}
    override fun copyFromAssetsRelative(assetsPath: String, filename: String, destination: File) {}
    override fun copyFromAssetsRelativeRecursive(assetsPath: String, directory: String, destination: String) {}
    override fun removeOldArabicDatabase(): Boolean = true
    override fun hasArabicSearchDatabase(): Boolean = true
    override fun writeVersionFile(widthParam: String, version: Int) {}
    override fun removeFilesForWidth(width: Int, directoryLambda: (String) -> String) {}
    override fun writeNoMediaFileRelative(widthParam: String) {}
    override fun upgradeNonAudioFiles(portraitWidth: String, landscapeWidth: String, totalPages: Int): Boolean = true
  }

  private class FakePageProvider(private val qaris: List<Qari>) : PageProvider {
    override fun getDataSource(): QuranDataSource = MadaniDataSource()
    override fun getPageSizeCalculator(displaySize: DisplaySize): PageSizeCalculator {
      throw UnsupportedOperationException()
    }
    override fun getImageVersion(): Int = 1
    override fun mushafType(): MushafType = MushafType.MADANI_1405
    override fun getImagesBaseUrl(): String = ""
    override fun getImagesZipBaseUrl(): String = ""
    override fun getPatchBaseUrl(): String = ""
    override fun getAyahInfoBaseUrl(): String = ""
    override fun getDatabasesBaseUrl(): String = ""
    override fun getAudioDatabasesBaseUrl(): String = ""
    override fun getAudioDirectoryName(): String = "audio"
    override fun getDatabaseDirectoryName(): String = "db"
    override fun getAyahInfoDirectoryName(): String = "ayahinfo"
    override fun getImagesDirectoryName(): String = "images"
    override fun getPreviewTitle(): Int = 0
    override fun getPreviewDescription(): Int = 0
    override fun getQaris(): List<Qari> = qaris
    override fun getDefaultQariId(): Int = qaris.firstOrNull()?.id ?: 0
  }
}
