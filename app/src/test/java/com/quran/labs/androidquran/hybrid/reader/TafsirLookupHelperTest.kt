package com.quran.labs.androidquran.hybrid.reader

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.fakes.FakeTranslationModel
import com.quran.labs.androidquran.fakes.FakeTranslationsDBAdapter
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TafsirLookupHelperTest {

  private lateinit var dbAdapter: FakeTranslationsDBAdapter
  private lateinit var translationModel: FakeTranslationModel
  private lateinit var quranInfo: QuranInfo
  private lateinit var translationUtil: TranslationUtil

  @Before
  fun setUp() {
    dbAdapter = FakeTranslationsDBAdapter()
    translationModel = FakeTranslationModel()
    quranInfo = QuranInfo(MadaniDataSource())
    translationUtil = TranslationUtil(quranInfo)
  }

  @Test
  fun `empty installed translations returns TafsirLoadState EMPTY cleanly`() = runTest {
    val helper = TafsirLookupHelper(
      translationsDBAdapter = dbAdapter,
      translationModel = translationModel,
      translationUtil = translationUtil,
      arabicTextLoader = { "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ" },
      ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    val result = helper.loadTafsirForAyah(
      sura = 1,
      ayah = 1,
      suraName = "سورة الفاتحة"
    )

    assertThat(result.loadState).isEqualTo(TafsirLoadState.EMPTY)
    assertThat(result.availableSources).isEmpty()
    assertThat(result.ayahArabicText).isEqualTo("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ")
  }

  @Test
  fun `selectPreferredSource prioritizes Al-Tafsir Al-Muyassar over English and generic Arabic`() = runTest {
    val english = LocalTranslation(
      id = 1L,
      filename = "en.sahih",
      name = "Sahih International",
      languageCode = "en"
    )
    val otherArabic = LocalTranslation(
      id = 2L,
      filename = "ar.jalalayn",
      name = "تفسير الجلالين",
      languageCode = "ar"
    )
    val muyassar = LocalTranslation(
      id = 3L,
      filename = "ar.muyassar",
      name = "التفسير الميسر",
      languageCode = "ar",
      minimumVersion = 5
    )

    dbAdapter.setTranslations(listOf(english, otherArabic, muyassar))
    translationModel.setTranslationText(
      VerseRange(1, 1, 1, 1, 1),
      "ar.muyassar",
      listOf(
        QuranText(
          1,
          1,
          "أبتدئ قراءة القرآن باسم الله مستعيناً به [[ملحوظة]]"
        )
      )
    )

    val helper = TafsirLookupHelper(
      translationsDBAdapter = dbAdapter,
      translationModel = translationModel,
      translationUtil = translationUtil,
      ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    val result = helper.loadTafsirForAyah(
      sura = 1,
      ayah = 1,
      suraName = "سورة الفاتحة",
      activeFilenames = setOf("en.sahih")
    )

    assertThat(result.loadState).isEqualTo(TafsirLoadState.LOADED)
    assertThat(result.selectedFilename).isEqualTo("ar.muyassar")
    assertThat(result.sourceName).isEqualTo("التفسير الميسر")
    assertThat(result.fullText).isEqualTo("أبتدئ قراءة القرآن باسم الله مستعيناً به")
    assertThat(result.availableSources).hasSize(3)
  }

  @Test
  fun `explicit preferredFilename overrides default Muyassar priority and cache invalidation reloads`() = runTest {
    val muyassar = LocalTranslation(id = 1L, filename = "ar.muyassar", name = "التفسير الميسر", languageCode = "ar")
    val jalalayn = LocalTranslation(id = 2L, filename = "ar.jalalayn", name = "تفسير الجلالين", languageCode = "ar")
    dbAdapter.setTranslations(listOf(muyassar, jalalayn))

    val range = VerseRange(1, 2, 1, 2, 1)
    translationModel.setTranslationText(range, "ar.jalalayn", listOf(QuranText(1, 2, "Text v1")))

    val helper = TafsirLookupHelper(
      translationsDBAdapter = dbAdapter,
      translationModel = translationModel,
      translationUtil = translationUtil,
      ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    val first = helper.loadTafsirForAyah(1, 2, "سورة الفاتحة", preferredFilename = "ar.jalalayn")
    assertThat(first.selectedFilename).isEqualTo("ar.jalalayn")
    assertThat(first.fullText).isEqualTo("Text v1")

    translationModel.setTranslationText(range, "ar.jalalayn", listOf(QuranText(1, 2, "Text v2")))
    val cached = helper.loadTafsirForAyah(1, 2, "سورة الفاتحة", preferredFilename = "ar.jalalayn")
    assertThat(cached.fullText).isEqualTo("Text v1")

    helper.invalidateCache()
    val reloaded = helper.loadTafsirForAyah(1, 2, "سورة الفاتحة", preferredFilename = "ar.jalalayn")
    assertThat(reloaded.fullText).isEqualTo("Text v2")
  }

  @Test
  fun `Muyassar metadata with English name prefers Arabic translatorForeign when available`() = runTest {
    val muyassar = LocalTranslation(
      id = 9L,
      filename = "ar.muyassar",
      name = "Arabic Muyassar Tafseer",
      translatorForeign = "التفسير الميسر",
      languageCode = "ar"
    )
    dbAdapter.setTranslations(listOf(muyassar))
    val range = VerseRange(2, 77, 2, 77, 1)
    translationModel.setTranslationText(range, "ar.muyassar", listOf(QuranText(2, 77, "تفسير الآية")))

    val helper = TafsirLookupHelper(
      translationsDBAdapter = dbAdapter,
      translationModel = translationModel,
      translationUtil = translationUtil,
      ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    val loaded = helper.loadTafsirForAyah(2, 77, "سورة البقرة")
    assertThat(loaded.sourceName).isEqualTo("التفسير الميسر")
    assertThat(TafsirLookupHelper.isMuyassarName("ar.muyassar", "Arabic Muyassar Tafseer")).isTrue()
    assertThat(TafsirLookupHelper.hasArabicScript("Arabic Muyassar Tafseer")).isFalse()
    assertThat(TafsirLookupHelper.hasArabicScript("التفسير الميسر")).isTrue()
  }

  @Test
  fun `known Arabic tafsirs with English names and blank translatorForeign localize cleanly to Arabic titles`() = runTest {
    val cases = listOf(
      Triple("ar.muyassar", "Arabic Muyassar Tafseer", "التفسير الميسر"),
      Triple("ar.katheer", "Arabic Ibn Kathir Tafseer", "تفسير ابن كثير"),
      Triple("ar.saadi", "Arabic Saadi Tafseer", "تفسير السعدي"),
      Triple("ar.baghawy", "Arabic Baghawy Tafseer", "تفسير البغوي"),
      Triple("ar.jalalayn", "Arabic Jalalayn Tafseer", "تفسير الجلالين"),
      Triple("ar.qurtubi", "Arabic Qurtubi Tafseer", "تفسير القرطبي"),
      Triple("ar.tabari", "Arabic Tabari Tafseer", "تفسير الطبري"),
      Triple("ar.waseet", "Arabic Waseet Tafseer", "التفسير الوسيط"),
      Triple("ar.tanweer", "Arabic Tanweer Tafseer", "التحرير والتنوير")
    )

    val locals = cases.mapIndexed { idx, (file, rawName, _) ->
      LocalTranslation(
        id = (idx + 1).toLong(),
        filename = file,
        name = rawName,
        translatorForeign = "",
        languageCode = "ar"
      )
    } + LocalTranslation(
      id = 99L,
      filename = "en.ibnkathir",
      name = "English Ibn Kathir Tafseer",
      translatorForeign = "",
      languageCode = "en"
    )
    dbAdapter.setTranslations(locals)
    val helper = TafsirLookupHelper(
      translationsDBAdapter = dbAdapter,
      translationModel = translationModel,
      translationUtil = translationUtil,
      ioDispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    val loaded = helper.loadTafsirForAyah(1, 1, "سورة الفاتحة")
    val byFilename = loaded.availableSources.associateBy { it.filename }
    for ((file, _, expectedArabic) in cases) {
      assertThat(byFilename.getValue(file).displayName).isEqualTo(expectedArabic)
    }
    // Explicit English tafsir must retain its English title
    assertThat(byFilename.getValue("en.ibnkathir").displayName).isEqualTo("English Ibn Kathir Tafseer")
  }
}
