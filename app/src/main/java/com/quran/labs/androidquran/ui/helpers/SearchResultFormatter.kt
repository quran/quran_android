package com.quran.labs.androidquran.ui.helpers

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.SearchResultFormatter as FastSearchResultFormatter

object SearchResultFormatter {
  const val ARABIC_SEARCH_TEXT_SIZE_SP: Float = 20f
  const val DEFAULT_SEARCH_TEXT_SIZE_SP: Float = 16f
  const val ARABIC_SEARCH_LINE_SPACING_MULTIPLIER: Float = 1.38f
  const val DEFAULT_SEARCH_LINE_SPACING_MULTIPLIER: Float = 1.25f

  fun isArabicResult(text: CharSequence): Boolean {
    return QuranUtils.doesStringContainArabic(text.toString())
  }

  fun formatResult(htmlText: String, @ColorInt highlightColor: Int): Spannable {
    val fast = FastSearchResultFormatter.format(htmlText, overrideHighlightColor = highlightColor)
    return if (fast is Spannable) fast else SpannableStringBuilder(fast)
  }

  fun recolorHighlightSpans(spanned: Spanned, @ColorInt highlightColor: Int): Spannable {
    val builder = SpannableStringBuilder(spanned)
    val existingSpans = builder.getSpans(0, builder.length, ForegroundColorSpan::class.java)
    for (span in existingSpans) {
      val start = builder.getSpanStart(span)
      val end = builder.getSpanEnd(span)
      val flags = builder.getSpanFlags(span)
      builder.removeSpan(span)
      if (start >= 0 && end > start) {
        builder.setSpan(ForegroundColorSpan(highlightColor), start, end, flags)
      }
    }
    return builder
  }

  fun applyTypography(textView: TextView, isArabic: Boolean, arabicTypeface: Typeface?) {
    if (isArabic) {
      textView.typeface = arabicTypeface ?: Typeface.DEFAULT
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ARABIC_SEARCH_TEXT_SIZE_SP)
      textView.setLineSpacing(0f, ARABIC_SEARCH_LINE_SPACING_MULTIPLIER)
      textView.textDirection = View.TEXT_DIRECTION_RTL
    } else {
      textView.typeface = Typeface.DEFAULT
      textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_SEARCH_TEXT_SIZE_SP)
      textView.setLineSpacing(0f, DEFAULT_SEARCH_LINE_SPACING_MULTIPLIER)
      textView.textDirection = View.TEXT_DIRECTION_LOCALE
    }
  }
}
