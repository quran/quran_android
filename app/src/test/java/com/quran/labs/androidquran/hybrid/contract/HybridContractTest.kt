package com.quran.labs.androidquran.hybrid.contract

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class HybridContractTest {

  @Test
  fun `HybridNavTab exposes 4 ordered tabs with distinct localized labels`() {
    assertThat(HybridNavTab.entries).containsExactly(
      HybridNavTab.READ,
      HybridNavTab.INDEX,
      HybridNavTab.SEARCH,
      HybridNavTab.BOOKMARKS
    ).inOrder()

    val context = ApplicationProvider.getApplicationContext<Context>()
    val englishLabels = HybridNavTab.entries.map { context.getString(it.labelRes) }
    assertThat(englishLabels).containsExactly("Read", "Index", "Search", "Saved").inOrder()
    assertThat(englishLabels.distinct()).hasSize(4)

    val arabicConfig = Configuration(context.resources.configuration).apply {
      setLocale(Locale.forLanguageTag("ar"))
    }
    val arabicContext = context.createConfigurationContext(arabicConfig)
    val arabicLabels = HybridNavTab.entries.map { arabicContext.getString(it.labelRes) }
    assertThat(arabicLabels).containsExactly("القراءة", "الفهرس", "بحث", "المحفوظات").inOrder()
    assertThat(arabicLabels.distinct()).hasSize(4)
  }

  @Test
  fun `HybridReaderContract applies and parses study tabs and ayah highlight extras`() {
    val intent = HybridReaderContract.applyToIntent(
      intent = Intent(),
      sura = 2,
      ayah = 255,
      initialStudyTab = HybridReaderContract.StudyTab.TAFSIR,
      autoPlayAyah = true
    )

    assertThat(intent.getIntExtra(HybridReaderContract.EXTRA_HIGHLIGHT_SURA, -1)).isEqualTo(2)
    assertThat(intent.getIntExtra(HybridReaderContract.EXTRA_HIGHLIGHT_AYAH, -1)).isEqualTo(255)
    assertThat(intent.getStringExtra(HybridReaderContract.EXTRA_INITIAL_STUDY_TAB)).isEqualTo("tafsir")
    assertThat(intent.getBooleanExtra(HybridReaderContract.EXTRA_AUTO_PLAY_AYAH, false)).isTrue()

    for (tab in HybridReaderContract.StudyTab.entries) {
      assertThat(HybridReaderContract.StudyTab.fromExtra(tab.extraValue)).isEqualTo(tab)
    }
    assertThat(HybridReaderContract.StudyTab.fromExtra("unknown")).isNull()
    assertThat(HybridReaderContract.StudyTab.fromExtra(null)).isNull()
  }

  @Test
  fun `DefaultHybridSearchHost implements HybridSearchHost and hybrid strings resolve cleanly`() {
    val host: HybridSearchHost = DefaultHybridSearchHost
    assertThat(host).isNotNull()

    val context = ApplicationProvider.getApplicationContext<Context>()
    assertThat(context.getString(R.string.hybrid_search_scope_ayahs)).isEqualTo("Ayahs")
    assertThat(context.getString(R.string.hybrid_reader_tab_recitation)).isEqualTo("Recitation")
    assertThat(context.getString(R.string.hybrid_hub_daily_wird)).isEqualTo("Daily Wird")
  }
}
