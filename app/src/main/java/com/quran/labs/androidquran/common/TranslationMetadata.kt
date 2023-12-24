package com.quran.labs.androidquran.common

import android.text.SpannableString
import android.text.SpannableStringBuilder
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.ui.helpers.TranslationFootnoteHelper

data class TranslationMetadata(
  val sura: Int,
  val ayah: Int,
  val text: String,
  val localTranslationId: Int? = null,
  val link: SuraAyah? = null,
  val linkPageNumber: Int? = null,
  val ayat: List<IntRange> = emptyList(),
  val footnotes: List<IntRange> = emptyList()
) {

  fun footnoteCognizantText(
    spannableStringBuilder: SpannableStringBuilder,
    expandedFootnotes: List<Int>,
    collapsedFootnoteSpannableStyler: ((Int) -> SpannableString),
    expandedFootnoteSpannableStyler: ((SpannableStringBuilder, Int, Int) -> SpannableStringBuilder)
  ): CharSequence {
    return TranslationFootnoteHelper.footnoteCognizantText(
      text,
      footnotes,
      spannableStringBuilder,
      expandedFootnotes,
      collapsedFootnoteSpannableStyler,
      expandedFootnoteSpannableStyler
    )
  }
}
