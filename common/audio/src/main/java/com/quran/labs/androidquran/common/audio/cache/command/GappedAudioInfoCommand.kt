package com.quran.labs.androidquran.common.audio.cache.command

import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.common.audio.model.download.PartiallyDownloadedSura
import dev.zacsweers.metro.Inject
import okio.FileSystem
import okio.Path

@Inject
class GappedAudioInfoCommand(
  private val quranInfo: QuranInfo,
  private val fileSystem: FileSystem
) {

  fun gappedDownloads(path: Path, allowedExtensions: List<String>): Pair<List<Int>, List<PartiallyDownloadedSura>> {
    val entries = fileSystem.listOrNull(path) ?: return emptyList<Int>() to emptyList()
    val fullyDownloaded = mutableListOf<Int>()
    val partiallyDownloaded = mutableListOf<PartiallyDownloadedSura>()

    for (directory in entries) {
      val sura = directory.name.toIntOrNull() ?: continue
      if (sura !in 1..114) continue

      val suraEntries = fileSystem.listOrNull(directory) ?: continue
      val ayat = LinkedHashSet<Int>()
      for (entry in suraEntries) {
        val name = entry.name
        for (extension in allowedExtensions) {
          val suffix = ".$extension"
          if (name.endsWith(suffix)) {
            val ayah = name.removeSuffix(suffix).toIntOrNull()
            if (ayah != null && ayah in 1..286) {
              ayat.add(ayah)
            }
            break
          }
        }
      }

      val totalAyat = quranInfo.getNumberOfAyahs(sura)
      if (totalAyat == ayat.size) {
        fullyDownloaded.add(sura)
      } else if (ayat.isNotEmpty()) {
        partiallyDownloaded.add(
          PartiallyDownloadedSura(
            sura,
            totalAyat,
            ayat.toList()
          )
        )
      }
    }

    return fullyDownloaded to partiallyDownloaded
  }
}
