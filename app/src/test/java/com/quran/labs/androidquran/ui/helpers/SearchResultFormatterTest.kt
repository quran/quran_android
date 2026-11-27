package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.ui.util.TypefaceManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class SearchResultFormatterTest {

  @Test
  fun `detects Arabic versus non-Arabic search results accurately`() {
    assertThat(SearchResultFormatter.isArabicResult("بسم الله الرحمن الرحيم")).isTrue()
    assertThat(SearchResultFormatter.isArabicResult("In the name of Allah, the Entirely Merciful")).isFalse()
  }

  @Test
  fun `recolors legacy font highlight spans to theme color without altering Arabic text`() {
    val legacyHoloBlue = -13388315 // #FF33B5E5
    val themeHighlight = Color.parseColor("#8C640A")
    val html = "بسم <font color=\"$legacyHoloBlue\">الله</font> الرحمن الرحيم <b>...</b> <font color=\"$legacyHoloBlue\">الله</font> لا إله إلا هو"

    val formatted = SearchResultFormatter.formatResult(html, themeHighlight)

    assertThat(formatted.toString()).isEqualTo("بسم الله الرحمن الرحيم ... الله لا إله إلا هو")

    val fgSpans = formatted.getSpans(0, formatted.length, ForegroundColorSpan::class.java)
    assertThat(fgSpans).hasLength(2)
    fgSpans.forEach { span ->
      assertThat(span.foregroundColor).isEqualTo(themeHighlight)
      val start = formatted.getSpanStart(span)
      val end = formatted.getSpanEnd(span)
      assertThat(formatted.subSequence(start, end).toString()).isEqualTo("الله")
    }

    val bgSpans = formatted.getSpans(0, formatted.length, BackgroundColorSpan::class.java)
    assertThat(bgSpans).isEmpty()
  }

  @Test
  fun `applies Uthmani Arabic typeface for Arabic rows and resets recycled rows for translations`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val arabicTypeface = TypefaceManager.getSearchArabicTypeface(context)
    assertThat(arabicTypeface).isNotNull()

    val textView = TextView(context)
    val density = context.resources.displayMetrics.scaledDensity

    SearchResultFormatter.applyTypography(textView, isArabic = true, arabicTypeface = arabicTypeface)
    assertThat(textView.typeface).isSameInstanceAs(arabicTypeface)
    assertThat(textView.textSize).isWithin(0.01f).of(SearchResultFormatter.ARABIC_SEARCH_TEXT_SIZE_SP * density)
    assertThat(textView.lineSpacingMultiplier).isWithin(0.01f).of(SearchResultFormatter.ARABIC_SEARCH_LINE_SPACING_MULTIPLIER)
    assertThat(textView.textDirection).isEqualTo(View.TEXT_DIRECTION_RTL)

    // Simulate ListView recycling the same TextView for a non-Arabic translation result
    SearchResultFormatter.applyTypography(textView, isArabic = false, arabicTypeface = arabicTypeface)
    assertThat(textView.typeface).isSameInstanceAs(Typeface.DEFAULT)
    assertThat(textView.textSize).isWithin(0.01f).of(SearchResultFormatter.DEFAULT_SEARCH_TEXT_SIZE_SP * density)
    assertThat(textView.lineSpacingMultiplier).isWithin(0.01f).of(SearchResultFormatter.DEFAULT_SEARCH_LINE_SPACING_MULTIPLIER)
    assertThat(textView.textDirection).isEqualTo(View.TEXT_DIRECTION_LOCALE)
  }
}
