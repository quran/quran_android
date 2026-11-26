package com.quran.labs.androidquran.hybrid.hub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.labs.androidquran.model.translation.ArabicDatabaseUtils
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.presenter.data.JuzListPresenter
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Provider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridIndexDataSourceTest {

  private lateinit var context: Context
  private lateinit var quranInfo: QuranInfo
  private lateinit var dataSource: HybridIndexDataSource

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    quranInfo = QuranInfo(MadaniDataSource())
    val displayData = QuranDisplayData(quranInfo)
    val settings = QuranSettings.getInstance(context)
    val arabicDb = ArabicDatabaseUtils(context, quranInfo, FakeQuranFileUtils.create(context))
    val juzPresenter = JuzListPresenter(quranInfo, arabicDb)
    dataSource = HybridIndexDataSource(
      quranInfo = quranInfo,
      quranDisplayData = displayData,
      settings = settings,
      juzListPresenter = Provider { juzPresenter }
    )
  }

  @Test
  fun `buildSuras produces 114 surahs with accurate pages juz and zero middle dots`() {
    val suras = dataSource.buildSuras(context)
    assertThat(suras).hasSize(114)

    val fatiha = suras.first()
    assertThat(fatiha.sura).isEqualTo(1)
    assertThat(fatiha.name).isEqualTo("Surah Al-Fātihah")
    assertThat(fatiha.startPage).isEqualTo(1)
    assertThat(fatiha.juz).isEqualTo(1)
    assertThat(fatiha.isMakki).isTrue()
    assertThat(fatiha.ayahCount).isEqualTo(7)

    val baqarah = suras[1]
    assertThat(baqarah.sura).isEqualTo(2)
    assertThat(baqarah.startPage).isEqualTo(2)
    assertThat(baqarah.isMakki).isFalse()
    assertThat(baqarah.ayahCount).isEqualTo(286)

    val nas = suras.last()
    assertThat(nas.sura).isEqualTo(114)
    assertThat(nas.startPage).isEqualTo(604)
    assertThat(nas.juz).isEqualTo(30)

    suras.forEach { sura ->
      assertThat(sura.name).doesNotContain("\u00B7")
      assertThat(sura.metaText).doesNotContain("\u00B7")
      assertThat(sura.startPage).isIn(1..604)
      assertThat(sura.juz).isIn(1..30)
    }
  }

  @Test
  fun `buildJuzs produces 30 juzs backed by QuranInfo quarters`() {
    val quarters = dataSource.loadResourceQuartersOrEmpty(context)
    assertThat(quarters).hasLength(240)

    val juzs = dataSource.buildJuzs(context, quarters)
    assertThat(juzs).hasSize(30)

    assertThat(juzs[0].juz).isEqualTo(1)
    assertThat(juzs[0].startPage).isEqualTo(1)
    assertThat(juzs[0].startAyah).isEqualTo(1)
    assertThat(juzs[0].previewText).isNotEmpty()

    assertThat(juzs[1].juz).isEqualTo(2)
    assertThat(juzs[1].startPage).isEqualTo(22)
    assertThat(juzs[1].startAyah).isEqualTo(142)

    assertThat(juzs[29].juz).isEqualTo(30)
    assertThat(juzs[29].startPage).isEqualTo(582)

    juzs.forEach { juz ->
      assertThat(juz.title).doesNotContain("\u00B7")
      assertThat(juz.previewText).doesNotContain("\u00B7")
    }
  }

  @Test
  fun `buildHizbs produces 60 hizbs mapped to quarter indices and valid pages`() {
    val quarters = dataSource.loadResourceQuartersOrEmpty(context)
    val hizbs = dataSource.buildHizbs(context, quarters)
    assertThat(hizbs).hasSize(60)

    // Hizb 1 -> quarter 0 (1:1, page 1, Juz 1)
    assertThat(hizbs[0].hizb).isEqualTo(1)
    assertThat(hizbs[0].juz).isEqualTo(1)
    assertThat(hizbs[0].startAyah).isEqualTo(1)
    assertThat(hizbs[0].startPage).isEqualTo(1)

    // Hizb 2 -> quarter 4 (2:75, Juz 1)
    val q4 = quranInfo.getQuarterByIndex(4)
    assertThat(hizbs[1].hizb).isEqualTo(2)
    assertThat(hizbs[1].juz).isEqualTo(1)
    assertThat(hizbs[1].startAyah).isEqualTo(q4.ayah)
    assertThat(hizbs[1].startPage).isEqualTo(quranInfo.getPageFromSuraAyah(q4.sura, q4.ayah))

    // Hizb 60 -> quarter 236 (87:1, Juz 30)
    val q236 = quranInfo.getQuarterByIndex(236)
    assertThat(hizbs[59].hizb).isEqualTo(60)
    assertThat(hizbs[59].juz).isEqualTo(30)
    assertThat(hizbs[59].startAyah).isEqualTo(q236.ayah)
    assertThat(hizbs[59].startPage).isEqualTo(quranInfo.getPageFromSuraAyah(q236.sura, q236.ayah))

    hizbs.forEach { hizb ->
      assertThat(hizb.juz).isIn(1..30)
      assertThat(hizb.startPage).isIn(1..604)
      assertThat(hizb.previewText).isNotEmpty()
      assertThat(hizb.previewText).doesNotContain("\u00B7")
    }
  }
}
