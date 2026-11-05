package com.quran.labs.androidquran.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
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
}
