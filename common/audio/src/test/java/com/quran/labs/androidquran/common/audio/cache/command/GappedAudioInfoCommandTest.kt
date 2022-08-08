package com.quran.labs.androidquran.common.audio.cache.command

import com.google.common.truth.Truth
import com.quran.data.core.QuranInfo
import com.quran.data.pageinfo.common.MadaniDataSource
import com.quran.labs.androidquran.common.audio.model.PartiallyDownloadedSura
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test

class GappedAudioInfoCommandTest {

  @Test
  fun testGappedAudio() {
    val qariPath = "/quran/audio/minshawi".toPath()
    val filesystem = FakeFileSystem()
    filesystem.createDirectories(qariPath)
    filesystem.write(qariPath / "103001.mp3") { }
    filesystem.write(qariPath / "103002.mp3") { }
    filesystem.write(qariPath / "103003.mp3") { }

    val quranInfo = QuranInfo(MadaniDataSource())
    val gaplessAudioInfoCommand = GappedAudioInfoCommand(quranInfo, filesystem)
    val downloads = gaplessAudioInfoCommand.gappedDownloads(qariPath)
    Truth.assertThat(downloads.first).hasSize(1)
    Truth.assertThat(downloads.second).isEmpty()
    Truth.assertThat(downloads.first).containsExactly(103)
  }

  @Test
  fun testGappedAudioWithPartials() {
    val qariPath = "/quran/audio/minshawi".toPath()
    val filesystem = FakeFileSystem()
    filesystem.createDirectories(qariPath)
    filesystem.write(qariPath / "103001.mp3") { }
    filesystem.write(qariPath / "103002.mp3") { }
    filesystem.write(qariPath / "103003.mp3") { }
    filesystem.write(qariPath / "114001.mp3") { }

    val quranInfo = QuranInfo(MadaniDataSource())
    val gappedAudioInfoCommand = GappedAudioInfoCommand(quranInfo, filesystem)
    val downloads = gappedAudioInfoCommand.gappedDownloads(qariPath)
    Truth.assertThat(downloads.first).hasSize(1)
    Truth.assertThat(downloads.second).hasSize(1)
    Truth.assertThat(downloads.first).containsExactly(103)
    Truth.assertThat(downloads.second).hasSize(1)
    Truth.assertThat(downloads.second.first()).isEqualTo(
      PartiallyDownloadedSura(114, 6, listOf(1))
    )
  }

  @Test
  fun testGappedAudioWithIllegalFilenames() {
    val qariPath = "/quran/audio/minshawi".toPath()
    val filesystem = FakeFileSystem()
    filesystem.createDirectories(qariPath)
    filesystem.write(qariPath / "test.mp3") { }
    filesystem.write(qariPath / "2.mp3") { }
    filesystem.write(qariPath / "115.mp3") { }
    filesystem.write(qariPath / "114001.mp3.part") { }
    filesystem.write(qariPath / "115001.mp3") { }

    val quranInfo = QuranInfo(MadaniDataSource())
    val gappedAudioInfoCommand = GappedAudioInfoCommand(quranInfo, filesystem)
    val downloads = gappedAudioInfoCommand.gappedDownloads(qariPath)
    Truth.assertThat(downloads.first).isEmpty()
    Truth.assertThat(downloads.second).isEmpty()
  }
}
