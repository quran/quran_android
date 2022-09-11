package com.quran.labs.androidquran.common.audio.cache.command

import com.quran.labs.androidquran.common.audio.util.AudioFileUtil
import okio.FileSystem
import okio.Path
import javax.inject.Inject

class GaplessAudioInfoCommand @Inject constructor(private val fileSystem: FileSystem) {

  fun gaplessDownloads(path: Path): Pair<List<Int>, List<Int>> {
    return fullGaplessDownloads(path) to partialGaplessDownloads(path)
  }

  private fun fullGaplessDownloads(path: Path): List<Int> {
    val paths = AudioFileUtil.filesMatchingSuffixWithSuffixRemoved(fileSystem, path, ".mp3")
    return paths
      .filter { it.length == 3 }
      .mapNotNull { it.toIntOrNull() }
      .filter { it in 1..114 }
  }

  private fun partialGaplessDownloads(path: Path): List<Int> {
    val paths = AudioFileUtil.filesMatchingSuffixWithSuffixRemoved(fileSystem, path, ".mp3.part")
    return paths
      .filter { it.length == 3 }
      .mapNotNull { it.toIntOrNull() }
      .filter { it in 1..114 }
  }
}
