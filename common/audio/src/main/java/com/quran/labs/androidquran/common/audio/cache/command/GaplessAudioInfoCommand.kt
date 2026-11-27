package com.quran.labs.androidquran.common.audio.cache.command

import dev.zacsweers.metro.Inject
import okio.FileSystem
import okio.Path

@Inject
class GaplessAudioInfoCommand(private val fileSystem: FileSystem) {

  fun gaplessDownloads(path: Path, allowedExtensions: List<String>): Pair<List<Int>, List<Int>> {
    val entries = fileSystem.listOrNull(path) ?: return emptyList<Int>() to emptyList()
    val full = LinkedHashSet<Int>()
    val partial = LinkedHashSet<Int>()

    for (entry in entries) {
      val name = entry.name
      if (name.endsWith(".part")) {
        val withoutPart = name.removeSuffix(".part")
        val sura = extractSuraNumber(withoutPart, allowedExtensions)
        if (sura != null) {
          partial.add(sura)
        }
      } else {
        val sura = extractSuraNumber(name, allowedExtensions)
        if (sura != null) {
          full.add(sura)
        }
      }
    }
    return full.toList() to partial.toList()
  }

  private fun extractSuraNumber(filename: String, allowedExtensions: List<String>): Int? {
    for (extension in allowedExtensions) {
      val suffix = ".$extension"
      if (filename.endsWith(suffix)) {
        val base = filename.removeSuffix(suffix)
        if (base.length == 3) {
          val sura = base.toIntOrNull()
          if (sura != null && sura in 1..114) {
            return sura
          }
        }
        return null
      }
    }
    return null
  }
}
