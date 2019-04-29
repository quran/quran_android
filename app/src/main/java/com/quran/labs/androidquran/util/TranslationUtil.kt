package com.quran.labs.androidquran.util

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import com.quran.labs.androidquran.common.QuranText
import com.quran.labs.androidquran.common.TranslationMetadata
import com.quran.labs.androidquran.data.QuranInfo
import dagger.Reusable

@Reusable
open class TranslationUtil(@ColorInt private val color: Int,
                           private val quranInfo: QuranInfo) {

  open fun parseTranslationText(quranText: QuranText): TranslationMetadata {
    val text = quranText.text
    val hyperlinkId = getHyperlinkAyahId(quranText)

    val suraAyah = if (hyperlinkId != null) {
      quranInfo.getSuraAyahFromAyahId(hyperlinkId)
    } else {
      null
    }

    val textToParse = if (suraAyah != null && quranText.extraData != null) {
      quranText.extraData
    } else {
      text
    }

    val withoutFooters = footerRegex.replace(textToParse, "")
    val spannable = SpannableString(withoutFooters)

    ayahRegex.findAll(withoutFooters)
        .forEach {
          val span = ForegroundColorSpan(color)
          val range = it.range
          spannable.setSpan(span, range.start, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    return TranslationMetadata(quranText.sura, quranText.ayah, spannable, suraAyah)
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
