package com.quran.labs.androidquran

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.QuranSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class SearchActivityTest {

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    FakeSearchContentProvider.reset()
  }

  @After
  fun tearDown() {
    QuranSettings.setInstance(null)
    FakeSearchContentProvider.reset()
  }

  @Test
  fun `search suggestion before migration opens setup without a pending destination`() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://search/1"))
      .putExtra(SearchManager.USER_QUERY, "mercy")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().get()
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(activity.quranSettings.haveMigratedLegacyBookmarksToMobileSync()).isFalse()
      val launched = shadowOf(activity).nextStartedActivity
      assertThat(launched.component?.className).isEqualTo(QuranDataActivity::class.java.name)
      assertThat(launched.action).isNull()
      assertThat(launched.extras).isNull()
      assertThat(activity.isFinishing).isTrue()
    }
  }

  @Test
  fun `ACTION_SEARCH populates RecyclerView with highlighted Arabic results and closes cursor`() {
    val createdCursors = mutableListOf<MatrixCursor>()
    registerFakeProvider { uri, selectionArgs ->
      assertThat(uri.getBooleanQueryParameter("raw", false)).isTrue()
      assertThat(selectionArgs?.firstOrNull()).isEqualTo("الله")
      MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
        addRow(arrayOf<Any>(1, 1, 1, "بسم الله الرحمن الرحيم"))
        addRow(arrayOf<Any>(2, 2, 255, "الله لا إله إلا هو"))
        createdCursors.add(this)
      }
    }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الله")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      val recyclerView = activity.findViewById<RecyclerView>(R.id.results_list)
      awaitCondition { (recyclerView.adapter?.itemCount ?: 0) == 2 }

      // Cursor must be drained off-main and closed immediately
      assertThat(createdCursors).hasSize(1)
      assertThat(createdCursors.single().isClosed).isTrue()

      // Bind first visible item and verify direct Arabic highlighting span
      recyclerView.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
      )
      recyclerView.layout(0, 0, 720, 1200)
      val firstHolder = recyclerView.findViewHolderForAdapterPosition(0)
      assertThat(firstHolder).isNotNull()
      val verseText = firstHolder!!.itemView.findViewById<TextView>(R.id.verseText).text
      assertThat(verseText.toString()).isEqualTo("بسم الله الرحمن الرحيم")
      val spans = (verseText as Spanned).getSpans(0, verseText.length, ForegroundColorSpan::class.java)
      assertThat(spans).hasLength(1)
      assertThat(verseText.getSpanStart(spans[0])).isEqualTo(4)
      assertThat(verseText.getSpanEnd(spans[0])).isEqualTo(8)

      val messageView = activity.findViewById<TextView>(R.id.search_area)
      assertThat(messageView.text.toString()).contains("الله")
      assertThat(activity.findViewById<View>(R.id.search_warning).visibility).isEqualTo(View.GONE)
      assertThat(activity.findViewById<View>(R.id.btnGetTranslations).visibility).isEqualTo(View.GONE)
    }
  }

  @Test
  fun `ACTION_SEARCH with null cursor for non-Arabic query shows get translations button`() {
    registerFakeProvider { _, _ -> null }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "mercy")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().get()
      val btn = activity.findViewById<Button>(R.id.btnGetTranslations)
      awaitCondition { btn.visibility == View.VISIBLE }

      assertThat(btn.text.toString()).isEqualTo(activity.getString(R.string.get_translations))
      val recyclerView = activity.findViewById<RecyclerView>(R.id.results_list)
      assertThat(recyclerView.adapter?.itemCount).isEqualTo(0)
    }
  }

  @Test
  fun `ACTION_SEARCH with missing Arabic DB shows Arabic warning and download button`() {
    registerFakeProvider { _, _ -> null }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الرحمن")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().get()
      val warning = activity.findViewById<TextView>(R.id.search_warning)
      val btn = activity.findViewById<Button>(R.id.btnGetTranslations)
      awaitCondition { warning.visibility == View.VISIBLE }

      assertThat(warning.text.toString())
        .isEqualTo(activity.getString(R.string.no_arabic_search_available))
      assertThat(btn.visibility).isEqualTo(View.VISIBLE)
      assertThat(btn.text.toString())
        .isEqualTo(activity.getString(R.string.get_arabic_search_db))
    }
  }

  @Test
  fun `onNewIntent replaces previous search results and item click jumps to reader when ready`() {
    registerFakeProvider { _, selectionArgs ->
      val q = selectionArgs?.firstOrNull() ?: ""
      MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
        if (q == "الله") {
          addRow(arrayOf<Any>(1, 1, 1, "بسم الله الرحمن الرحيم"))
        } else if (q == "الرحيم") {
          addRow(arrayOf<Any>(2, 1, 3, "الرحمن الرحيم"))
          addRow(arrayOf<Any>(3, 2, 37, "إنه هو التواب الرحيم"))
        }
      }
    }

    val firstIntent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الله")
    Robolectric.buildActivity(SearchActivity::class.java, firstIntent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      activity.quranSettings.setMigratedLegacyBookmarksToMobileSync()
      activity.readerReadinessTracker.markReady(activity.quranSettings.pageType)

      val recyclerView = activity.findViewById<RecyclerView>(R.id.results_list)
      awaitCondition { (recyclerView.adapter?.itemCount ?: 0) == 1 }

      val secondIntent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الرحيم")
      controller.newIntent(secondIntent)
      awaitCondition { (recyclerView.adapter?.itemCount ?: 0) == 2 }

      recyclerView.measure(
        View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
      )
      recyclerView.layout(0, 0, 720, 1200)
      recyclerView.findViewHolderForAdapterPosition(1)!!.itemView.performClick()
      shadowOf(Looper.getMainLooper()).idle()

      val launched = shadowOf(activity).nextStartedActivity
      assertThat(launched).isNotNull()
      assertThat(launched.component?.className).isEqualTo(PagerActivity::class.java.name)
      assertThat(launched.getIntExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, -1)).isEqualTo(2)
      assertThat(launched.getIntExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, -1)).isEqualTo(37)
    }
  }

  @Test
  fun `searching new Arabic query rebinds shared ayah with updated highlight span`() {
    registerFakeProvider { _, _ ->
      MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
        addRow(arrayOf<Any>(1, 1, 1, "بسم الله الرحمن الرحيم"))
      }
    }

    val firstIntent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الله")
    Robolectric.buildActivity(SearchActivity::class.java, firstIntent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      val recyclerView = activity.findViewById<RecyclerView>(R.id.results_list)
      awaitCondition { (recyclerView.adapter?.itemCount ?: 0) == 1 }

      fun layoutAndGetSpanRange(): Pair<Int, Int> {
        recyclerView.measure(
          View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY)
        )
        recyclerView.layout(0, 0, 720, 1200)
        val holder = recyclerView.findViewHolderForAdapterPosition(0)!!
        val spanned = holder.itemView.findViewById<TextView>(R.id.verseText).text as Spanned
        val span = spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java).single()
        return spanned.getSpanStart(span) to spanned.getSpanEnd(span)
      }

      // First query "الله" highlights indices [4, 8)
      assertThat(layoutAndGetSpanRange()).isEqualTo(4 to 8)

      // Second query "الرحمن" returns the exact same ayah 1:1; DiffUtil must rebind highlight to [9, 15)
      val secondIntent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الرحمن")
      controller.newIntent(secondIntent)
      awaitCondition {
        layoutAndGetSpanRange() == (9 to 15)
      }
      assertThat(layoutAndGetSpanRange()).isEqualTo(9 to 15)
    }
  }

  private fun registerFakeProvider(handler: (Uri, Array<out String>?) -> Cursor?) {
    FakeSearchContentProvider.queryHandler = handler
    Robolectric.buildContentProvider(FakeSearchContentProvider::class.java)
      .create(QuranDataProvider.SEARCH_URI.authority!!)
  }

  private fun awaitCondition(timeoutMs: Long = 3000L, predicate: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!predicate() && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      Thread.sleep(10)
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertThat(predicate()).isTrue()
  }

  class FakeSearchContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?
    ): Cursor? = queryHandler(uri, selectionArgs)

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
      @Volatile
      var queryHandler: (Uri, Array<out String>?) -> Cursor? = { _, _ -> null }

      fun reset() {
        queryHandler = { _, _ -> null }
      }
    }
  }
}
