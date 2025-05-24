package com.quran.labs.androidquran.common.audio.cache.command

import com.quran.labs.androidquran.common.audio.util.AudioFileUtil
import okio.FileSystem
import okio.Path
import javax.inject.Inject

class GaplessAudioInfoCommand @Inject constructor(private val fileSystem: FileSystem) {

  fun gaplessDownloads(path: Path, allowedExtensions: List<String>): Pair<List<Int>, List<Int>> {
    return fullGaplessDownloads(path, allowedExtensions) to partialGaplessDownloads(path, allowedExtensions)
  }

  private fun fullGaplessDownloads(path: Path, allowedExtensions: List<String>): List<Int> {
    val paths = allowedExtensions.flatMap { extension ->
      AudioFileUtil.filesMatchingSuffixWithSuffixRemoved(fileSystem, path, ".$extension")
    }

    return paths
      .filter { it.length == 3 }
      .mapNotNull { it.toIntOrNull() }
      .filter { it in 1..114 }
      .distinct()
  }

  private fun partialGaplessDownloads(path: Path, allowedExtensions: List<String>): List<Int> {
    val paths = allowedExtensions.flatMap { extension ->
      AudioFileUtil.filesMatchingSuffixWithSuffixRemoved(fileSystem, path, ".$extension.part")
    }

    return paths
      .filter { it.length == 3 }
      .mapNotNull { it.toIntOrNull() }
      .filter { it in 1..114 }
      .distinct()
  }
}
