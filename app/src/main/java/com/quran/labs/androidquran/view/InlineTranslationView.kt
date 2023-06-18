package com.quran.labs.androidquran.view

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StyleRes
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.util.QuranSettings

class InlineTranslationView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : ScrollView(context, attrs, defStyle) {
  private var leftRightMargin = 0
  private var topBottomMargin = 0

  @StyleRes
  private var textStyle = 0
  private var fontSize = 0
  private var footerSpacerHeight = 0

  private lateinit var linearLayout: LinearLayout

  private var ayat: List<QuranAyahInfo>? = null
  private var translations: Array<LocalTranslation>? = null

  init {
    init(context)
  }

  private fun init(context: Context) {
    isFillViewport = true
    linearLayout = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
    }
    addView(linearLayout, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    leftRightMargin = resources.getDimensionPixelSize(R.dimen.translation_left_right_margin)
    topBottomMargin = resources.getDimensionPixelSize(R.dimen.translation_top_bottom_margin)
    footerSpacerHeight = resources.getDimensionPixelSize(R.dimen.translation_footer_spacer)
    initResources()
  }

  private fun initResources() {
    val settings = QuranSettings.getInstance(context)
    fontSize = settings.translationTextSize
    textStyle = R.style.TranslationText
  }

  fun refresh() {
    val ayat = ayat
    val translations = translations
    if (ayat != null && translations != null) {
      initResources()
      setAyahs(translations, ayat)
    }
  }

  fun setAyahs(translations: Array<LocalTranslation>, ayat: List<QuranAyahInfo>) {
    linearLayout.removeAllViews()
    if (ayat.isNotEmpty() && ayat[0].texts.size > 0) {
      this.ayat = ayat
      this.translations = translations
      var i = 0
      val ayatSize = ayat.size
      while (i < ayatSize) {
        addTextForAyah(translations, ayat[i])
        i++
      }
      addFooterSpacer()
      scrollTo(0, 0)
    }
  }

  private fun addFooterSpacer() {
    val params = LinearLayout.LayoutParams(
      LayoutParams.MATCH_PARENT, footerSpacerHeight
    )
    val view = View(context)
    linearLayout.addView(view, params)
  }

  private fun addTextForAyah(translations: Array<LocalTranslation>, ayah: QuranAyahInfo) {
    var params = LinearLayout.LayoutParams(
      LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
    )
    params.setMargins(leftRightMargin, topBottomMargin, leftRightMargin, topBottomMargin)
    val suraNumber = ayah.sura
    val ayahNumber = ayah.ayah
    val ayahHeader = TextView(context)
    ayahHeader.setTextColor(Color.WHITE)
    ayahHeader.textSize = fontSize.toFloat()
    ayahHeader.setTypeface(null, Typeface.BOLD)
    ayahHeader.text = context.resources.getString(R.string.sura_ayah, suraNumber, ayahNumber)
    linearLayout.addView(ayahHeader, params)
    val ayahView = TextView(context)
    ayahView.setTextAppearance(context, textStyle)
    ayahView.setTextColor(Color.WHITE)
    ayahView.textSize = fontSize.toFloat()

    // translation
    val showHeader = translations.size > 1
    val builder = SpannableStringBuilder()
    for (i in translations.indices) {
      val (_, _, translationText) = ayah.texts[i]
      if (!TextUtils.isEmpty(translationText)) {
        if (showHeader) {
          if (i > 0) {
            builder.append("\n\n")
          }
          val start = builder.length
          builder.append(translations[i].getTranslatorName())
          builder.setSpan(
            StyleSpan(Typeface.BOLD),
            start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
          )
          builder.append("\n\n")
        }

        // irrespective of whether it's a link or not, show the text
        builder.append(translationText)
      }
    }
    ayahView.append(builder)
    params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    params.setMargins(leftRightMargin, topBottomMargin, leftRightMargin, topBottomMargin)
    ayahView.setTextIsSelectable(true)
    linearLayout.addView(ayahView, params)
  }
}
