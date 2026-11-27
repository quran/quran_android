package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.util.QuranUtils
import java.text.NumberFormat
import java.util.Locale

object HybridNumberFormatter {

  fun formatNumber(value: Int, locale: Locale? = null): String {
    if (locale == null) {
      return QuranUtils.getLocalizedNumber(value)
    }
    return NumberFormat.getIntegerInstance(locale).apply {
      isGroupingUsed = false
    }.format(value)
  }

  fun formatPercent(percent: Int, locale: Locale? = null): String {
    val clamped = percent.coerceIn(0, 100)
    val resolvedLocale = locale ?: QuranUtils.getCurrentLocale()
    val digits = formatNumber(clamped, locale)
    val suffix = if (resolvedLocale.language == "ar") "٪" else "%"
    return digits + suffix
  }

  fun formatSurahMeta(context: Context, isMakki: Boolean, ayahCount: Int): String {
    val origin = context.getString(if (isMakki) R.string.makki else R.string.madani)
    val verses = context.resources.getQuantityString(
      R.plurals.verses,
      ayahCount,
      QuranUtils.getLocalizedNumber(ayahCount)
    )
    return context.getString(R.string.hybrid_hub_sura_meta_format, origin, verses)
  }
}
