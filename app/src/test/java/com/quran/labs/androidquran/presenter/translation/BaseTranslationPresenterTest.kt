package com.quran.labs.androidquran.presenter.translation

import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranText
import com.quran.data.model.VerseRange
import com.quran.data.pageinfo.common.MadaniDataSource
import com.quran.labs.androidquran.common.TranslationMetadata
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.model.translation.TranslationModel
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.util.TranslationUtil
import com.quran.mobile.translation.model.LocalTranslation
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class BaseTranslationPresenterTest {

  private lateinit var presenter: BaseTranslationPresenter<TestPresenter>

  @Before
  fun setupTest() {
    presenter = BaseTranslationPresenter(
        Mockito.mock(TranslationModel::class.java),
        Mockito.mock(TranslationsDBAdapter::class.java),
        object : TranslationUtil(QuranInfo(MadaniDataSource())) {
          override fun parseTranslationText(quranText: QuranText, translationId: Int): TranslationMetadata {
            return TranslationMetadata(quranText.sura, quranText.ayah, quranText.text, translationId)
          }
        },
        QuranInfo(MadaniDataSource())
    )
  }

  @Test
  fun testGetTranslationNames() {
    val databases = listOf("one.db", "two.db")
    val map = object : HashMap<String, LocalTranslation>() {
      init {
        put("one.db", LocalTranslation(1, "one.db", "One", "First", null, "", null, 1, 2))
        put("two.db", LocalTranslation(2, "two.db", "Two", "Second", null, "", null, 1, 2))
        put("three.db", LocalTranslation(3, "three.db", "Three", "Third", null, "", null, 1, 2))
      }
    }

    val translations = presenter.getTranslations(databases, map)
    assertThat(translations).hasLength(2)
    assertThat(translations[0].translator).isEqualTo("First")
    assertThat(translations[1].translator).isEqualTo("Second")
  }

  @Test
  fun testHashlessGetTranslationNames() {
    val databases = listOf("one.db", "two.db")
    val map = HashMap<String, LocalTranslation>()

    val translations = presenter.getTranslations(databases, map)
    assertThat(translations).hasLength(2)
    assertThat(translations[0].filename).isEqualTo(databases[0])
    assertThat(translations[1].filename).isEqualTo(databases[1])
  }

  @Test
  fun testCombineAyahDataOneVerse() {
    val verseRange = VerseRange(1, 1, 1, 1, 1)
    val arabic = listOf(QuranText(1, 1, "first ayah"))
    val info = presenter.combineAyahData(verseRange, arabic,
        listOf(listOf(QuranText(1, 1, "translation"))),
        arrayOf(LocalTranslation(1, "one.db", "One", "First", null, "", null, 1, 2)))

    assertThat(info).hasSize(1)
    val first = info[0]
    assertThat(first.sura).isEqualTo(1)
    assertThat(first.ayah).isEqualTo(1)
    assertThat(first.texts).hasSize(1)
    assertThat(first.arabicText).isEqualTo("first ayah")
    assertThat(first.texts[0].text).isEqualTo("translation")
    assertThat(first.texts[0].localTranslationId).isEqualTo(1)
  }

  @Test
  fun testCombineAyahDataOneVerseEmpty() {
    val verseRange = VerseRange(1, 1, 1, 1, 1)
    val arabic = emptyList<QuranText>()
    val info = presenter.combineAyahData(verseRange, arabic, emptyList(), emptyArray())
    assertThat(info).hasSize(0)
  }

  @Test
  fun testCombineAyahDataOneVerseNoArabic() {
    val verseRange = VerseRange(1, 1, 1, 1, 1)
    val arabic = emptyList<QuranText>()
    val info = presenter.combineAyahData(verseRange, arabic,
        listOf(listOf(QuranText(1, 1, "translation"))), emptyArray())

    assertThat(info).hasSize(1)
    val first = info[0]
    assertThat(first.sura).isEqualTo(1)
    assertThat(first.ayah).isEqualTo(1)
    assertThat(first.texts).hasSize(1)
    assertThat(first.arabicText).isNull()
    assertThat(first.texts[0].text).isEqualTo("translation")
  }

  @Test
  fun testCombineAyahDataArabicEmptyTranslations() {
    val verseRange = VerseRange(1, 1, 1, 2, 2)
    val arabic = listOf(
        QuranText(1, 1, "first ayah"),
        QuranText(1, 2, "second ayah")
    )
    val info = presenter.combineAyahData(verseRange, arabic, ArrayList(), emptyArray())
    assertThat(info).hasSize(2)
    assertThat(info[0].sura).isEqualTo(1)
    assertThat(info[0].ayah).isEqualTo(1)
    assertThat(info[0].texts).hasSize(0)
    assertThat(info[0].arabicText).isEqualTo("first ayah")
    assertThat(info[1].sura).isEqualTo(1)
    assertThat(info[1].ayah).isEqualTo(2)
    assertThat(info[1].texts).hasSize(0)
    assertThat(info[1].arabicText).isEqualTo("second ayah")
  }

  @Test
  fun testEnsureProperTranslations() {
    val verseRange = VerseRange(1, 1, 1, 2, 2)

    val text = listOf(QuranText(1, 1, "bismillah"))
    val result = presenter.ensureProperTranslations(verseRange, text)
    assertThat(result).hasSize(2)

    val first = result[0]
    assertThat(first.sura).isEqualTo(1)
    assertThat(first.ayah).isEqualTo(1)
    assertThat(first.text).isEqualTo("bismillah")

    val second = result[1]
    assertThat(second.sura).isEqualTo(1)
    assertThat(second.ayah).isEqualTo(2)
    assertThat(second.text).isEmpty()
  }

  private class TestPresenter : Presenter<Any> {
    override fun bind(what: Any) {}

    override fun unbind(what: Any) {}
  }
}
