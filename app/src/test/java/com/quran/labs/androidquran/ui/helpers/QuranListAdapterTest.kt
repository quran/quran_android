package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.core.QuranInfo
import com.quran.data.model.bookmark.PageReadingBookmark
import com.quran.data.model.bookmark.ReadingBookmarkType
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.pages.data.madani.MadaniDataSource
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant
import androidx.appcompat.R as AppCompatR

/** The list rows inflate in every layout variant (the Arabic one has its own). */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranListAdapterTest {

  private fun readingBookmarkRow(): View {
    // the rows are inflated in an activity, with its (AppCompat) theme
    val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), AppCompatR.style.Theme_AppCompat_DayNight)
    val quranInfo = QuranInfo(MadaniDataSource())
    val row = QuranRowFactory(quranInfo, QuranDisplayData(quranInfo))
      .fromReadingBookmark(context, PageReadingBookmark(ReadingBookmarkType.TEAL, 42, Instant.fromEpochSeconds(0)))
    val recyclerView = RecyclerView(context).apply { layoutManager = LinearLayoutManager(context) }
    val adapter = QuranListAdapter(context, recyclerView, arrayOf(row), true).apply { setShowProgress(true) }
    val parent = FrameLayout(context)
    val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
    adapter.onBindViewHolder(holder, 0)
    return holder.itemView
  }

  @Test
  fun aReadingBookmarkRowHasItsProgressButton() {
    assertThat(readingBookmarkRow().findViewById<View>(R.id.progressButton).visibility).isEqualTo(View.VISIBLE)
  }

  @Test
  @Config(qualifiers = "ar")
  fun aReadingBookmarkRowHasItsProgressButtonInArabic() {
    assertThat(readingBookmarkRow().findViewById<View>(R.id.progressButton).visibility).isEqualTo(View.VISIBLE)
  }
}
