package com.quran.labs.androidquran.common.mapper

import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import javax.inject.Inject

/**
 * Madani to Kufi Mapper
 * Kufi counting is 6236 verses as is found in most Hafs mus7afs.
 * Madani counting is 6214 verses as is found in some Warsh/Qaloon mus7afs.
 */
class MadaniToKufiMapper @Inject constructor(private val quranInfo: QuranInfo): AyahMapper {
  private val map = MadaniToKufiMap.translationMap
  private val kufiHafsMushafMap = MadaniDataSource().numberOfAyahsForSuraArray

  override fun mapAyah(suraAyah: SuraAyah): List<SuraAyah> {
    val suraMappings = map[suraAyah.sura] ?: error("Sura mapping not found for ${suraAyah.sura}")
    return mapInternal(suraAyah, suraMappings)
  }

  override fun reverseMapAyah(suraAyah: SuraAyah): List<SuraAyah> {
    val suraMappings = map[suraAyah.sura] ?: error("Sura mapping not found for ${suraAyah.sura}")
    val reversedMappings = suraMappings.map { reverseOperation(it) }
    return mapInternal(suraAyah, reversedMappings)
  }

  private fun mapInternal(suraAyah: SuraAyah, suraMappings: List<MadaniToKufiOperator>): List<SuraAyah> {
    val operations = suraMappings.filter { it.appliesToSuraAyah(suraAyah.sura, suraAyah.ayah) }
    if (operations.size > 1) {
      error("Multiple operations found for $suraAyah")
    }

    val operation = operations.firstOrNull()
    return if (operation == null) {
      listOf(suraAyah)
    } else {
      applyOperation(operation, suraAyah)
    }
  }

  override fun mapRange(verseRange: VerseRange): VerseRange {
    val startAyah = mapAyah(SuraAyah(verseRange.startSura, verseRange.startAyah))
    val endAyah = mapAyah(SuraAyah(verseRange.endingSura, verseRange.endingAyah))

    val start = startAyah.minOrNull()!!
    val end = endAyah.maxOrNull()!!
    val ayahsInRange = getAyahId(end) - getAyahId(start)
    return VerseRange(start.sura, start.ayah, end.sura, end.ayah, ayahsInRange)
  }

  private fun getAyahId(suraAyah: SuraAyah): Int {
    var ayahId = 0
    for (i in 0 until suraAyah.sura - 1) {
      // note - this is explicitly using kufi map from the madani hafs mushaf
      ayahId += kufiHafsMushafMap[i]
    }
    ayahId += suraAyah.ayah
    return ayahId
  }

  override fun mapKufiData(
    targetVerseRange: VerseRange,
    kufiData: List<QuranText>
  ): List<QuranText> {
    return (targetVerseRange.startSura..targetVerseRange.endingSura).flatMap { sura ->
      // the start ayah is startAyah if it's startSura, endingAyah if endingSura, and 1 otherwise
      // since it's a sura in between this particular range of suras.
      val startAyah = when (sura) {
        targetVerseRange.startSura -> targetVerseRange.startAyah
        else -> 1
      }

      // the max ayah is endingAyah if we're the endingSura. otherwise we don't really know, so
      // we get it from quranInfo (which, in this case, won't be kufi counting, otherwise why
      // would be mapping from kufi to kufi?)
      val maxAyah = if (targetVerseRange.endingSura == sura) {
        targetVerseRange.endingAyah
      } else {
        quranInfo.getNumberOfAyahs(sura)
      }

      (startAyah..maxAyah).map { nonKufiAyah ->
        reverseMapText(sura, nonKufiAyah, kufiData)
      }
    }
  }

  private fun reverseMapText(sura: Int, nonKufiAyah: Int, kufiData: List<QuranText>): QuranText {
    val mapped = mapAyah(SuraAyah(sura, nonKufiAyah))
    val mappedData = mapped.map { mappedSuraAyah ->
      kufiData.first { it.sura == mappedSuraAyah.sura && it.ayah == mappedSuraAyah.ayah }
    }

    // this will swallow ayah links and just inline all mapped ayahs for now
    val text = mappedData.joinToString("\n") { it.extraData ?: it.text }
    return QuranText(sura, nonKufiAyah, text, null)
  }

  private fun reverseOperation(
    operation: MadaniToKufiOperator,
  ): MadaniToKufiOperator {
    return when (operation) {
      is RangeOffsetOperator -> RangeOffsetOperator(
        operation.sura,
        operation.startAyah + operation.offset,
        operation.endAyah + operation.offset,
        -operation.offset
      )
      // technically, there could be a third one here, but in practice, there aren't.
      is JoinOperator -> SplitOperator(
        operation.sura,
        operation.targetAyah,
        operation.startAyah,
        operation.endAyah
      )
      is SplitOperator -> JoinOperator(
        operation.sura,
        operation.firstAyah,
        operation.lastAyah(),
        operation.ayah
      )
    }
  }

  private fun applyOperation(operation: MadaniToKufiOperator, suraAyah: SuraAyah): List<SuraAyah> {
    return when (operation) {
      is RangeOffsetOperator -> {
        listOf(suraAyah.copy(ayah = suraAyah.ayah + operation.offset))
      }
      is JoinOperator -> {
        listOf(SuraAyah(operation.sura, operation.targetAyah))
      }
      is SplitOperator -> {
        val thirdList = if (operation.thirdAyah != null) listOf(
            SuraAyah(operation.sura, operation.thirdAyah)
        ) else emptyList()

        val first = SuraAyah(operation.sura, operation.firstAyah)
        val second = SuraAyah(operation.sura, operation.secondAyah)
        listOf(first, second) + thirdList
      }
    }
  }
}
