package com.quran.labs.androidquran.common.mapper

import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.data.model.SuraAyah
import com.quran.data.model.VerseRange
import com.quran.data.pageinfo.mapper.AyahMapper
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import dev.zacsweers.metro.Inject
import kotlin.math.abs

/**
 * Madani to Kufi Mapper
 * Kufi counting is 6236 verses as is found in most Hafs mus7afs.
 * Madani counting is 6214 verses as is found in some Warsh/Qaloon mus7afs.
 */
class MadaniToKufiMapper @Inject constructor(private val quranInfo: QuranInfo): AyahMapper {
  private val map = MadaniToKufiMap.translationMap
  private val reversedMap: Map<Int, List<MadaniToKufiOperator>> by lazy {
    map.mapValues { (_, suraMappings) -> suraMappings.map { reverseOperation(it) } }
  }
  private val kufiHafsMushafMap = MadaniDataSource().numberOfAyahsForSuraArray

  override fun mapAyah(suraAyah: SuraAyah): List<SuraAyah> {
    val suraMappings = map[suraAyah.sura] ?: error("Sura mapping not found for ${suraAyah.sura}")
    return mapInternal(suraAyah, suraMappings)
  }

  override fun reverseMapAyah(suraAyah: SuraAyah): List<SuraAyah> {
    val suraMappings = reversedMap[suraAyah.sura]
      ?: error("Sura mapping not found for ${suraAyah.sura}")
    return mapInternal(suraAyah, suraMappings)
  }

  private fun mapInternal(suraAyah: SuraAyah, suraMappings: List<MadaniToKufiOperator>): List<SuraAyah> {
    var operation: MadaniToKufiOperator? = null
    for (i in suraMappings.indices) {
      val candidate = suraMappings[i]
      if (candidate.appliesToSuraAyah(suraAyah.sura, suraAyah.ayah)) {
        if (operation != null) {
          error("Multiple operations found for $suraAyah")
        }
        operation = candidate
      }
    }

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
    // versesInRange is inclusive of both endpoints, matching QuranInfo.getVerseRangeForPage
    val ayahsInRange = 1 + abs(getAyahId(end) - getAyahId(start))
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
    val kufiByKey = HashMap<Long, QuranText>(kufiData.size * 2)
    for (i in kufiData.indices) {
      val item = kufiData[i]
      val key = suraAyahKey(item.sura, item.ayah)
      if (!kufiByKey.containsKey(key)) {
        kufiByKey[key] = item
      }
    }

    val result = ArrayList<QuranText>(kufiData.size)
    for (sura in targetVerseRange.startSura..targetVerseRange.endingSura) {
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

      for (nonKufiAyah in startAyah..maxAyah) {
        result.add(reverseMapText(sura, nonKufiAyah, kufiByKey))
      }
    }
    return result
  }

  private fun suraAyahKey(sura: Int, ayah: Int): Long {
    return (sura.toLong() shl 32) or (ayah.toLong() and 0xFFFFFFFFL)
  }

  private fun reverseMapText(
    sura: Int,
    nonKufiAyah: Int,
    kufiByKey: Map<Long, QuranText>
  ): QuranText {
    val mapped = mapAyah(SuraAyah(sura, nonKufiAyah))
    val text = if (mapped.size == 1) {
      val mappedSuraAyah = mapped[0]
      val item = kufiByKey[suraAyahKey(mappedSuraAyah.sura, mappedSuraAyah.ayah)]
        ?: throw NoSuchElementException("Missing kufi data for $mappedSuraAyah")
      item.extraData ?: item.text
    } else {
      // this will swallow ayah links and just inline all mapped ayahs for now
      mapped.joinToString("\n") { mappedSuraAyah ->
        val item = kufiByKey[suraAyahKey(mappedSuraAyah.sura, mappedSuraAyah.ayah)]
          ?: throw NoSuchElementException("Missing kufi data for $mappedSuraAyah")
        item.extraData ?: item.text
      }
    }
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
