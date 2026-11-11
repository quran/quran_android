package com.quran.mobile.feature.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.quran.shared.persistence.model.NoteRange
import java.text.NumberFormat
import com.quran.mobile.common.ui.core.R as UiCoreR

@Immutable
internal class QuranVerses(private val ayahCounts: List<Int>) {
  fun contains(sura: Int, ayah: Int): Boolean = ayah >= 1 && ayah <= (ayahCounts.getOrNull(sura - 1) ?: 0)
}

@Composable
internal fun rememberNumberFormat(): NumberFormat {
  val locale = LocalConfiguration.current.locales[0]
  return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

@Composable
internal fun verseName(verse: String, verses: QuranVerses): String {
  val numbers = verse.split(':').mapNotNull { it.toIntOrNull() }
  return if (numbers.size != 2) {
    verse
  } else {
    val (sura, ayah) = numbers
    val coordinate = coordinate(sura, ayah)
    if (!verses.contains(sura, ayah)) {
      coordinate
    } else {
      suraVerse(sura, coordinate)
    }
  }
}

@Composable
internal fun verseRangeName(range: NoteRange, verses: QuranVerses): String {
  val start = coordinate(range.startSura, range.startAyah)
  val end = coordinate(range.endSura, range.endAyah)
  val isSingleVerse = range.startSura == range.endSura && range.startAyah == range.endAyah
  val isValid = verses.contains(range.startSura, range.startAyah) &&
    verses.contains(range.endSura, range.endAyah) &&
    (range.startSura < range.endSura || (range.startSura == range.endSura && range.startAyah <= range.endAyah))
  return when {
    !isValid -> if (isSingleVerse) start else "$start–$end"
    isSingleVerse -> suraVerse(range.startSura, start)
    else -> stringResource(
      R.string.quran_sync_verse_range,
      suraVerse(range.startSura, start),
      if (range.startSura == range.endSura) end else suraVerse(range.endSura, end)
    )
  }
}

@Composable
private fun coordinate(sura: Int, ayah: Int): String {
  val numbers = rememberNumberFormat()
  return numbers.format(sura) + ":" + numbers.format(ayah)
}

@Composable
private fun suraVerse(sura: Int, coordinate: String): String {
  val suraName = stringArrayResource(UiCoreR.array.sura_names)[sura - 1]
  return suraName + stringResource(R.string.quran_sync_separator) + coordinate
}
