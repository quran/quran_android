package com.quran.labs.androidquran.util

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorInt
import com.quran.labs.androidquran.common.QuranText
import com.quran.labs.androidquran.common.TranslationMetadata
import dagger.Reusable

@Reusable
open class TranslationUtil(@ColorInt private val color: Int) {
  val ayahRegex = """([«{﴿][\s\S]*?[﴾}»])""".toRegex()
  val footerRegex = """\[\[[\s\S]*?]]""".toRegex()


  open fun parseTranslationText(quranText: QuranText): TranslationMetadata {
    val text = quranText.text
    val withoutFooters = footerRegex.replace(text, "")
    val spannable = SpannableString(withoutFooters)

    ayahRegex.findAll(withoutFooters)
        .forEach {
          val span = ForegroundColorSpan(color)
          val range = it.range
          spannable.setSpan(span, range.start, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    return TranslationMetadata(quranText.sura, quranText.ayah, spannable)
  }
}
