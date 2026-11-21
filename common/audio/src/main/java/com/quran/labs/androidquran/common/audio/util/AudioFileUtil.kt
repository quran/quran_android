package com.quran.labs.androidquran.common.audio.util

import okio.FileSystem
import okio.Path

object AudioFileUtil {

  /** Formats that may exist on disk, regardless of the current catalog or device decoders. */
  val audioExtensions = listOf("mp3", "opus")

  fun filesMatchingSuffixWithSuffixRemoved(fileSystem: FileSystem, path: Path, suffix: String): List<String> {
    return fileNamesMatchingSuffix(fileSystem, path, suffix)
      .map { it.name.removeSuffix(suffix) }
  }

  private fun fileNamesMatchingSuffix(fileSystem: FileSystem, path: Path, suffix: String): List<Path> {
    return fileSystem.listOrNull(path)
      ?.filter { it.name.endsWith(suffix) }
      ?: emptyList()
  }
}
