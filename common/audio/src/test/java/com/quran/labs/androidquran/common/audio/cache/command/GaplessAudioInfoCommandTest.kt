package com.quran.labs.androidquran.common.audio.cache.command

import com.google.common.truth.Truth.assertThat
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test


class GaplessAudioInfoCommandTest {

  @Test
  fun testGaplessAudio() {
    val qariPath = "/quran/audio/minshawi".toPath()
    val filesystem = FakeFileSystem()
    filesystem.createDirectories(qariPath)
    filesystem.write(qariPath / "001.mp3") { }
    filesystem.write(qariPath / "002.mp3") { }
    filesystem.write(qariPath / "114.mp3") { }

    val gaplessAudioInfoCommand = GaplessAudioInfoCommand(filesystem)
    val downloads = gaplessAudioInfoCommand.gaplessDownloads(qariPath)
    assertThat(downloads.first).hasSize(3)
    assertThat(downloads.second).isEmpty()
    assertThat(downloads.first).containsExactly(1, 2, 114)
  }

  @Test
  fun testGaplessAudioWithPartials() {
    val qariPath = "/quran/audio/minshawi".toPath()
    val filesystem = FakeFileSystem()
    filesystem.createDirectories(qariPath)
    filesystem.write(qariPath / "001.mp3") { }
    filesystem.write(qariPath / "002.mp3") { }
    filesystem.write(qariPath / "114.mp3.part") { }

    val gaplessAudioInfoCommand = GaplessAudioInfoCommand(filesystem)
    val downloads = gaplessAudioInfoCommand.gaplessDownloads(qariPath)
    assertThat(downloads.first).hasSize(2)
    assertThat(downloads.second).hasSize(1)
    assertThat(downloads.first).containsExactly(1, 2)
    assertThat(downloads.second).containsExactly(114)
  }

  @Test
  fun testGaplessAudioWithIllegalFilenames() {
    val qariPath = "/quran/audio/minshawi".toPath()
    val filesystem = FakeFileSystem()
    filesystem.createDirectories(qariPath)
    filesystem.write(qariPath / "test.mp3") { }
    filesystem.write(qariPath / "2.mp3") { }
    filesystem.write(qariPath / "115.mp3") { }
    filesystem.write(qariPath / "114001.mp3.part") { }

    val gaplessAudioInfoCommand = GaplessAudioInfoCommand(filesystem)
    val downloads = gaplessAudioInfoCommand.gaplessDownloads(qariPath)
    assertThat(downloads.first).isEmpty()
    assertThat(downloads.second).isEmpty()
  }
}
