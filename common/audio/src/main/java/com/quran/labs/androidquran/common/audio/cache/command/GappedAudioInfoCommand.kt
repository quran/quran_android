package com.quran.labs.androidquran.common.audio.cache.command

import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.common.audio.util.AudioFileUtil
import okio.FileSystem
import okio.Path
import javax.inject.Inject

class GappedAudioInfoCommand @Inject constructor(
  private val quranInfo: QuranInfo,
  private val fileSystem: FileSystem
) {

  fun gappedDownloads(path: Path): Pair<List<Int>, List<Int>> {
    val gappedDownloads = AudioFileUtil.filesMatchingSuffixWithSuffixRemoved(fileSystem, path, ".mp3")
    val gappedSuras = gappedDownloads
      .filter { it.length == 6 }
      .groupBy { it.take(3).toIntOrNull() ?: -1 }
      .filterKeys { it in 1..114 }
      .mapValues { entry ->
        entry.value.mapNotNull {
            item -> item.takeLast(3).toIntOrNull()
        }
        .filter { it in 1..286 }
      }
      .mapValues { it.value.size }
      .toList()
    val (fullyDownloaded, partiallyDownloaded) = gappedSuras.partition {
        (sura, downloadedAyat) -> quranInfo.getNumberOfAyahs(sura) == downloadedAyat
    }
    return fullyDownloaded.map { it.first } to partiallyDownloaded.map { it.first }
  }
}
