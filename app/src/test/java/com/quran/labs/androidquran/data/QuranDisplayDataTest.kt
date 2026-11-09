package com.quran.labs.androidquran.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.QuranUtils
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = Application::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranDisplayDataTest {
  private val quranDisplayData = QuranDisplayData(QuranInfo(MadaniDataSource()))

  @Test
  fun `page labels include the sura continuing from the previous page`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertThat(quranDisplayData.getAllSuraNamesForPageString(context, 293))
      .isEqualTo("Surah Al-Isrāʾ, Al-Kahf")
    assertThat(quranDisplayData.getSuraPageString(context, 293))
      .isEqualTo("Surah Al-Isrāʾ, Al-Kahf (Page 293)")
  }

  @Test
  fun `the last page includes all three suras in order`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertThat(quranDisplayData.getAllSuraNamesForPageString(context, 604))
      .isEqualTo("Surah Al-Ikhlāṣ, Al-Falaq, An-Nās")
    assertThat(quranDisplayData.getSuraPageString(context, 604))
      .isEqualTo("Surah Al-Ikhlāṣ, Al-Falaq, An-Nās (Page 604)")
  }

  @Test
  fun `single sura pages exclude suras starting on the next page`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertThat(quranDisplayData.getAllSuraNamesForPageString(context, 1)).isEqualTo("Surah Al-Fātihah")
    assertThat(quranDisplayData.getAllSuraNamesForPageString(context, 42)).isEqualTo("Surah Al-Baqarah")
    assertThat(quranDisplayData.getAllSuraNamesForPageString(context, 292)).isEqualTo("Surah Al-Isrāʾ")
    assertThat(quranDisplayData.getAllSuraNamesForPageString(context, 294)).isEqualTo("Surah Al-Kahf")
  }

  @Test
  @Config(qualifiers = "ar")
  fun `multiple sura names use localized names and punctuation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertThat(quranDisplayData.getAllSuraNamesForPageString(context, 293))
      .isEqualTo("سُورَةُ الإِسۡرَاءِ، الكَهۡفِ")
  }

  @Test
  fun `invalid page descriptions keep the page only fallback`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertThat(quranDisplayData.getSuraPageString(context, 0)).isEqualTo("Page 0")
    assertThat(quranDisplayData.getSuraPageString(context, 605)).isEqualTo("Page 605")
  }

  @Test
  fun `notification title shows the ayah range`() {
    val context = ApplicationProvider.getApplicationContext<Context>()

    assertThat(
      quranDisplayData.getNotificationTitle(context, SuraAyah(18, 1), SuraAyah(18, 10), false)
    ).isEqualTo("Surah Al-Kahf (1-10)")
  }

  @Test
  @Config(qualifiers = "ar")
  fun `notification title localizes the ayah numbers`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val one = QuranUtils.getLocalizedNumber(1)
    val ten = QuranUtils.getLocalizedNumber(10)
    // only meaningful where the locale actually uses non-latin digits
    assumeTrue(ten != "10")

    val title =
      quranDisplayData.getNotificationTitle(context, SuraAyah(18, 1), SuraAyah(18, 10), false)
    assertThat(title).endsWith(" ($one-$ten)")
    assertThat(title).doesNotContain("(1-10)")
  }

  @Test
  @Config(qualifiers = "ar")
  fun `notification title localizes ayah numbers across suras`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val five = QuranUtils.getLocalizedNumber(5)
    val ten = QuranUtils.getLocalizedNumber(10)
    assumeTrue(ten != "10")

    val title =
      quranDisplayData.getNotificationTitle(context, SuraAyah(18, 5), SuraAyah(19, 10), false)
    assertThat(title).contains(" ($five) - ")
    assertThat(title).endsWith(" ($ten)")
    assertThat(title).doesNotContain("(5)")
  }
}
