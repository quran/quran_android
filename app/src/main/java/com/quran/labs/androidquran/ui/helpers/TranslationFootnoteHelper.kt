package com.quran.labs.androidquran.ui.helpers

import android.text.SpannableString
import android.text.SpannableStringBuilder

object TranslationFootnoteHelper {

  fun footnoteCognizantText(
    data: CharSequence?,
    footnotes: List<IntRange>,
    spannableStringBuilder: SpannableStringBuilder,
    expandedFootnotes: List<Int>,
    collapsedFootnoteSpannableStyler: ((Int) -> SpannableString),
    expandedFootnoteSpannableStyler: ((SpannableStringBuilder, Int, Int) -> SpannableStringBuilder)
  ): CharSequence {
    return if (data != null) {
      val ranges = footnotes.sortedByDescending { it.last }
      ranges.foldIndexed(spannableStringBuilder) { index, builder, range ->
        val number = ranges.size - index
        if (number !in expandedFootnotes) {
          builder.replace(
            range.first,
            range.last + 1,
            collapsedFootnoteSpannableStyler(number)
          )
        } else {
          expandedFootnoteSpannableStyler(builder, range.first, range.last + 1)
        }
      }
    } else {
      ""
    }
  }
}
