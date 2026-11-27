package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.util.QuranUtils
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridNumberFormatterTest {

  @After
  fun tearDown() {
    QuranUtils.clearLocalizedNumberCache()
  }

  @Test
  fun `formatPercent renders Arabic-Indic digits and Arabic percent sign for Arabic locale`() {
    val arabicEgypt = Locale.forLanguageTag("ar-EG")
    assertThat(HybridNumberFormatter.formatPercent(50, arabicEgypt)).isEqualTo("٥٠٪")
    assertThat(HybridNumberFormatter.formatPercent(0, arabicEgypt)).isEqualTo("٠٪")
    assertThat(HybridNumberFormatter.formatPercent(100, arabicEgypt)).isEqualTo("١٠٠٪")
  }

  @Test
  fun `formatPercent clamps out-of-range values and uses ASCII percent for English`() {
    assertThat(HybridNumberFormatter.formatPercent(-10, Locale.US)).isEqualTo("0%")
    assertThat(HybridNumberFormatter.formatPercent(50, Locale.US)).isEqualTo("50%")
    assertThat(HybridNumberFormatter.formatPercent(140, Locale.US)).isEqualTo("100%")
  }

  @Test
  fun `formatSurahMeta formats Makki and Madani metadata without middle dot`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val englishFatiha = HybridNumberFormatter.formatSurahMeta(context, isMakki = true, ayahCount = 7)
    assertThat(englishFatiha).doesNotContain("\u00B7")
    assertThat(englishFatiha).contains("Makki")
    assertThat(englishFatiha).contains("7")

    val arConfig = Configuration(context.resources.configuration).apply {
      setLocale(Locale.forLanguageTag("ar-EG"))
    }
    val arContext = context.createConfigurationContext(arConfig)
    val previousDefault = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("ar-EG"))
      QuranUtils.clearLocalizedNumberCache()
      val arabicFatiha = HybridNumberFormatter.formatSurahMeta(arContext, isMakki = true, ayahCount = 7)
      assertThat(arabicFatiha).doesNotContain("\u00B7")
      assertThat(arabicFatiha).contains("مكية")
      assertThat(arabicFatiha).contains("٧")
    } finally {
      Locale.setDefault(previousDefault)
      QuranUtils.clearLocalizedNumberCache()
    }
  }
}
