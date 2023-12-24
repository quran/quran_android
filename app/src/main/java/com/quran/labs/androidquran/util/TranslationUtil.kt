package com.quran.labs.androidquran.util

import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.labs.androidquran.common.TranslationMetadata
import dagger.Reusable

@Reusable
open class TranslationUtil(private val quranInfo: QuranInfo) {

  open fun parseTranslationText(quranText: QuranText, translationId: Int): TranslationMetadata {
    val text = quranText.text
    val hyperlinkId = getHyperlinkAyahId(quranText)

    val suraAyah = if (hyperlinkId != null) {
      quranInfo.getSuraAyahFromAyahId(hyperlinkId)
    } else {
      null
    }

    val linkPage = if (suraAyah != null) {
      quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
    } else { null }

    val extraData = quranText.extraData
    val textToParse = if (suraAyah != null && extraData != null) {
      extraData
    } else {
      text
    }

    val ayat = ayahRegex.findAll(textToParse).map { it.range }.toList()
    val footnotes = footerRegex.findAll(textToParse).map { it.range }.toList()

    return TranslationMetadata(
      quranText.sura,
      quranText.ayah,
      textToParse,
      translationId,
      suraAyah,
      linkPage,
      ayat,
      footnotes
    )
  }

  companion object {
    private val ayahRegex = """([«{﴿][\s\S]*?[﴾}»])""".toRegex()
    private val footerRegex = """\[\[[\s\S]*?]]""".toRegex()
    const val MINIMUM_PROCESSING_VERSION = 5

    @JvmStatic
    fun getHyperlinkAyahId(quranText: QuranText): Int? {
      val text = quranText.text
      if (text.length < 5) {
        return text.toIntOrNull()
      }
      return null
    }
  }
}
