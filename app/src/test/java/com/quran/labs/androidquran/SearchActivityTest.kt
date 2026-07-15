package com.quran.labs.androidquran

import android.app.SearchManager
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.widget.ListView
import androidx.lifecycle.lifecycleScope
import androidx.loader.content.CursorLoader
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.test.TestDispatcherRule
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class SearchActivityTest {
  @get:Rule
  val dispatcherRule = TestDispatcherRule()

  private lateinit var controller: ActivityController<SearchActivity>
  private lateinit var activity: SearchActivity
  private var stopped = false
  private val cursors = mutableListOf<MatrixCursor>()

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    val application = ApplicationProvider.getApplicationContext<TestApplication>()
    File(application.filesDir, "quran_android").deleteRecursively()
    controller = Robolectric.buildActivity(SearchActivity::class.java, Intent(Intent.ACTION_MAIN))
      .setup()
    activity = controller.get()
  }

  @After
  fun tearDown() {
    if (!stopped) controller.pause().stop()
    controller.destroy()
    cursors.forEach { it.close() }
    QuranSettings.setInstance(null)
  }

  @Test
  fun `English suggestion after Arabic results opens translation`() = runTest {
    addArabicDatabase()
    showArabicResults()

    controller.newIntent(suggestion(8, "mercy"))
    awaitNavigation()

    assertReaderIntent(sura = 2, ayah = 1, translation = true)
  }

  @Test
  fun `Arabic suggestion with its database opens the Arabic page`() = runTest {
    addArabicDatabase()

    controller.newIntent(suggestion(6236, "الناس"))
    awaitNavigation()

    assertReaderIntent(sura = 114, ayah = 6, translation = false)
  }

  @Test
  fun `Arabic suggestion without its database opens translation`() = runTest {
    controller.newIntent(suggestion(7, "الرحمن"))
    awaitNavigation()

    assertReaderIntent(sura = 1, ayah = 7, translation = true)
  }

  @Test
  fun `out of range suggestion identifiers do not navigate`() = runTest {
    for (id in listOf(0, -2, 6237)) {
      controller.newIntent(suggestion(id, "mercy"))
      awaitNavigation()
      assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }
  }

  @Test
  fun `newer suggestion replaces pending navigation`() = runTest {
    addArabicDatabase()

    controller.newIntent(suggestion(1, "الرحمن"))
    controller.newIntent(suggestion(8, "mercy"))
    awaitNavigation()

    assertReaderIntent(sura = 2, ayah = 1, translation = true)
    assertThat(shadowOf(activity).nextStartedActivity).isNull()
  }

  @Test
  fun `stopping the activity cancels pending navigation`() = runTest {
    addArabicDatabase()
    controller.newIntent(suggestion(1, "الرحمن"))

    controller.pause().stop()
    stopped = true
    awaitNavigation()

    assertThat(shadowOf(activity).nextStartedActivity).isNull()
  }

  @Test
  fun `selecting a search result cancels pending suggestion navigation`() = runTest {
    addArabicDatabase()
    showArabicResults()
    controller.newIntent(suggestion(1, "الرحمن"))

    val results = activity.findViewById<ListView>(R.id.results_list)
    results.performItemClick(null, 0, 8)
    awaitNavigation()

    assertReaderIntent(sura = 2, ayah = 1, translation = false)
    assertThat(shadowOf(activity).nextStartedActivity).isNull()
  }

  private fun suggestion(id: Int, query: String) = Intent(Intent.ACTION_VIEW).apply {
    data = Uri.withAppendedPath(QuranDataProvider.SEARCH_URI, id.toString())
    putExtra(SearchManager.USER_QUERY, query)
  }

  private fun addArabicDatabase() {
    val directory = activity.quranFileUtils.getQuranDatabaseDirectory()
    directory.mkdirs()
    File(directory, QuranDataProvider.QURAN_ARABIC_DATABASE).writeText("present")
  }

  private fun showArabicResults() {
    val args = Bundle().apply { putString("EXTRA_QUERY", "الرحمن") }
    activity.onCreateLoader(0, args)
    val cursor = MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
      addRow(arrayOf<Any>(8, 2, 1, "الم"))
    }
    cursors.add(cursor)
    activity.onLoadFinished(CursorLoader(activity), cursor)
  }

  private suspend fun awaitNavigation() {
    activity.lifecycleScope.coroutineContext[Job]?.children?.toList()?.joinAll()
  }

  private fun assertReaderIntent(sura: Int, ayah: Int, translation: Boolean) {
    val intent = shadowOf(activity).nextStartedActivity
    assertThat(intent).isNotNull()
    assertThat(intent.component?.className).isEqualTo(PagerActivity::class.java.name)
    assertThat(intent.getIntExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, -1)).isEqualTo(sura)
    assertThat(intent.getIntExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, -1)).isEqualTo(ayah)
    assertThat(intent.getIntExtra("page", -1)).isEqualTo(activity.quranInfo.getPageFromSuraAyah(sura, ayah))
    assertThat(intent.getBooleanExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, false))
      .isEqualTo(translation)
  }
}
