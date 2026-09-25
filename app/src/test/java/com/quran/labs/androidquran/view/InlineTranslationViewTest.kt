package com.quran.labs.androidquran.view

import android.content.Context
import android.preference.PreferenceManager
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.common.TranslationMetadata
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.mobile.translation.model.LocalTranslation
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class InlineTranslationViewTest {
  private lateinit var context: Context

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    QuranSettings.setInstance(null)
  }

  @After
  fun teardown() {
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    QuranSettings.setInstance(null)
  }

  @Test
  fun setAyahsAppliesSeparateAyahAndTranslationTextSizes() {
    PreferenceManager.getDefaultSharedPreferences(context).edit()
      .putInt(Constants.PREF_AYAH_TEXT_SIZE, AYAH_TEXT_SIZE_SP)
      .putInt(Constants.PREF_TRANSLATION_TEXT_SIZE, TRANSLATION_TEXT_SIZE_SP)
      .commit()

    val view = InlineTranslationView(context)
    view.setAyahs(arrayOf(localTranslation()), listOf(quranAyahInfo()))

    val rows = view.getChildAt(0) as LinearLayout
    val ayahHeader = rows.getChildAt(0) as TextView
    val translationText = rows.getChildAt(1) as TextView

    assertThat(ayahHeader.textSize).isWithin(0.01f).of(AYAH_TEXT_SIZE_SP.toPx())
    assertThat(translationText.textSize).isWithin(0.01f).of(TRANSLATION_TEXT_SIZE_SP.toPx())
  }

  private fun localTranslation() = LocalTranslation(
    filename = "quran.en.test.db",
    translator = "Test Translator",
    languageCode = "en"
  )

  private fun quranAyahInfo() = QuranAyahInfo(
    1,
    1,
    "Arabic text",
    listOf(TranslationMetadata(1, 1, "Translation text")),
    1
  )

  private fun Int.toPx(): Float {
    return TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_SP,
      toFloat(),
      context.resources.displayMetrics
    )
  }

  private companion object {
    const val AYAH_TEXT_SIZE_SP = 18
    const val TRANSLATION_TEXT_SIZE_SP = 24
  }
}
