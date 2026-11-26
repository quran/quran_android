package com.quran.labs.androidquran.common.audio.cache.command

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.audio.Qari
import com.quran.data.source.DisplaySize
import com.quran.data.source.MushafType
import com.quran.data.source.PageProvider
import com.quran.data.source.PageSizeCalculator
import com.quran.data.source.QuranDataSource
import com.quran.labs.androidquran.common.audio.model.download.QariDownloadInfo
import com.quran.labs.androidquran.common.audio.util.AudioDecoderCapabilities
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.common.audio.util.QuranAudioExtensionDecider
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test

class AudioInfoCommandTest {

  private val audioDir = "/quran/audio"

  private val gaplessQari = Qari(
    id = 1,
    nameResource = 1,
    url = "https://example.com/mp3/",
    opusUrl = "https://example.com/opus/",
    path = "gapless_qari",
    hasGaplessAlternative = false,
    db = "gapless_qari.db",
  )

  private val gappedQari = Qari(
    id = 2,
    nameResource = 2,
    url = "https://example.com/gapped/mp3/",
    opusUrl = "https://example.com/gapped/opus/",
    path = "gapped_qari",
    hasGaplessAlternative = false,
    db = null,
  )

  @Test
  fun `generateQariDownloadInfo probes qari path directly without listing root audio directory`() {
    val fakeFs = FakeFileSystem()
    val root = audioDir.toPath()
    fakeFs.createDirectories(root / "gapless_qari")
    fakeFs.createDirectories(root / "other_qari")
    fakeFs.write(root / "gapless_qari" / "001.mp3") { }
    fakeFs.write(root / "gapless_qari" / "002.opus.part") { }

    val countingFs = CountingFileSystem(fakeFs)
    val command = createCommand(countingFs, listOf(gaplessQari, gappedQari), opusSupported = true)

    val info = command.generateQariDownloadInfo(gaplessQari, audioDir)

    assertThat(info).isInstanceOf(QariDownloadInfo.GaplessQariDownloadInfo::class.java)
    val gaplessInfo = info as QariDownloadInfo.GaplessQariDownloadInfo
    assertThat(gaplessInfo.fullyDownloadedSuras).containsExactly(1)
    assertThat(gaplessInfo.partiallyDownloadedSuras).containsExactly(2)
    assertThat(gaplessInfo.surasWithLocalFiles).containsExactly(1)

    // Root audio directory was never listed; qari directory was listed only once
    // because allowedExtensions matches AudioFileUtil.audioExtensions.
    assertThat(countingFs.listCounts[root] ?: 0).isEqualTo(0)
    assertThat(countingFs.listCounts[root / "gapless_qari"]).isEqualTo(1)
  }

  @Test
  fun `generateQariDownloadInfo avoids double scan for gapped qari when all audio extensions allowed`() {
    val fakeFs = FakeFileSystem()
    val root = audioDir.toPath()
    val suraDir = root / "gapped_qari" / "103"
    fakeFs.createDirectories(suraDir)
    fakeFs.write(suraDir / "1.mp3") { }
    fakeFs.write(suraDir / "2.mp3") { }
    fakeFs.write(suraDir / "3.mp3") { }

    val countingFs = CountingFileSystem(fakeFs)
    val command = createCommand(countingFs, listOf(gappedQari), opusSupported = true)

    val info = command.generateQariDownloadInfo(gappedQari, audioDir)

    assertThat(info).isInstanceOf(QariDownloadInfo.GappedQariDownloadInfo::class.java)
    val gappedInfo = info as QariDownloadInfo.GappedQariDownloadInfo
    assertThat(gappedInfo.fullyDownloadedSuras).containsExactly(103)
    assertThat(gappedInfo.surasWithLocalFiles).containsExactly(103)

    assertThat(countingFs.listCounts[root / "gapped_qari"]).isEqualTo(1)
    assertThat(countingFs.listCounts[suraDir]).isEqualTo(1)
  }

  @Test
  fun `generateAllQariDownloadInfo returns empty downloads when qari directory is missing`() {
    val fakeFs = FakeFileSystem()
    fakeFs.createDirectories(audioDir.toPath())
    val command = createCommand(fakeFs, listOf(gaplessQari, gappedQari), opusSupported = false)

    val result = command.generateAllQariDownloadInfo(audioDir)

    assertThat(result).hasSize(2)
    assertThat(result[0].fullyDownloadedSuras).isEmpty()
    assertThat(result[1].fullyDownloadedSuras).isEmpty()
  }

  private fun createCommand(
    fileSystem: FileSystem,
    qaris: List<Qari>,
    opusSupported: Boolean,
  ): AudioInfoCommand {
    val quranInfo = QuranInfo(MadaniDataSource())
    val qariUtil = QariUtil(FakePageProvider(qaris))
    val decider = QuranAudioExtensionDecider(
      object : AudioDecoderCapabilities {
        override fun isOpusSupported(): Boolean = opusSupported
      }
    )
    return AudioInfoCommand(
      fileSystem = fileSystem,
      qariUtil = qariUtil,
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
