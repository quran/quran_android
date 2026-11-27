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
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.dao.translation.Translation
import com.quran.labs.androidquran.dao.translation.TranslationItem
import com.quran.labs.androidquran.data.QuranDataProvider
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.hybrid.contract.HybridReaderContract
import com.quran.labs.androidquran.hybrid.search.HighlightSpanRange
import com.quran.labs.androidquran.hybrid.search.HybridSearchScope
import com.quran.labs.androidquran.hybrid.search.SearchTestTags
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

@Config(application = com.quran.labs.androidquran.base.TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class SearchActivityTest {

  @Before
  fun setUp() {
    @Suppress("DEPRECATION")
    ShadowChoreographer.setPostFrameCallbackDelay(1)
    QuranSettings.setInstance(null)
    resetSharedSyncGraph()
    FakeSearchContentProvider.reset()
  }

  @After
  fun tearDown() {
    runCatching {
      val app = androidx.test.core.app.ApplicationProvider
        .getApplicationContext<com.quran.labs.androidquran.base.TestApplication>()
      app.appCoroutineScope.cancel()
    }
    QuranSettings.setInstance(null)
    resetSharedSyncGraph()
    FakeSearchContentProvider.reset()
  }

  private fun resetSharedSyncGraph() {
    runCatching {
      val field = Class.forName("com.quran.shared.pipeline.di.SharedDependencyGraph")
        .getDeclaredField("instance")
      field.isAccessible = true
      field.set(null, null)
    }
    runCatching {
      com.quran.shared.persistence.deleteSharedDatabase()
    }
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
  fun `ACTION_SEARCH populates Compose result cards with highlighted Arabic results and closes cursor`() {
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
      awaitCondition {
        findNodeByTag(activity, SearchTestTags.ayahText(1, 1)) != null
      }

      // Cursor must be drained off-main and closed immediately
      assertThat(createdCursors).hasSize(1)
      assertThat(createdCursors.single().isClosed).isTrue()

      val firstTextNode = findNodeByTag(activity, SearchTestTags.ayahText(1, 1))!!
      assertThat(nodeText(firstTextNode)).isEqualTo("بسم الله الرحمن الرحيم")
      assertThat(highlightRanges(firstTextNode))
        .containsExactly(HighlightSpanRange(4, 8))

      assertThat(findNodeByTag(activity, SearchTestTags.HIT_COUNT_BADGE)).isNotNull()
      assertThat(findNodeByTag(activity, SearchTestTags.WARNING_TEXT)).isNull()
      assertThat(findNodeByTag(activity, SearchTestTags.ACTION_CTA_BUTTON)).isNull()
    }
  }

  @Test
  fun `ACTION_SEARCH with null cursor for non-Arabic query shows get translations button`() {
    registerFakeProvider { _, _ -> null }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "mercy")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      ensureComposeMeasured(activity)

      awaitCondition {
        findNodeByTag(activity, SearchTestTags.ACTION_CTA_BUTTON) != null
      }

      val ctaNode = findNodeByTag(activity, SearchTestTags.ACTION_CTA_BUTTON)!!
      assertThat(nodeText(ctaNode)).isEqualTo(activity.getString(R.string.get_translations))
      assertThat(findNodeByTag(activity, SearchTestTags.ayahCard(1, 1))).isNull()
    }
  }

  @Test
  fun `ACTION_SEARCH with missing Arabic DB shows Arabic warning and download button`() {
    registerFakeProvider { _, _ -> null }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الرحمن")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      ensureComposeMeasured(activity)

      awaitCondition {
        findNodeByTag(activity, SearchTestTags.WARNING_TEXT) != null
      }

      val warningNode = findNodeByTag(activity, SearchTestTags.WARNING_TEXT)!!
      val ctaNode = findNodeByTag(activity, SearchTestTags.ACTION_CTA_BUTTON)!!
      assertThat(nodeText(warningNode))
        .isEqualTo(activity.getString(R.string.no_arabic_search_available))
      assertThat(nodeText(ctaNode))
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
      ensureComposeMeasured(activity)

      awaitCondition {
        findNodeByTag(activity, SearchTestTags.ayahCard(1, 1)) != null
      }

      val secondIntent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الرحيم")
      controller.newIntent(secondIntent)
      awaitCondition {
        findNodeByTag(activity, SearchTestTags.ayahCard(2, 37)) != null
      }

      val secondCard = findNodeByTag(activity, SearchTestTags.ayahCard(2, 37))!!
      performClick(secondCard)
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
      ensureComposeMeasured(activity)

      awaitCondition {
        findNodeByTag(activity, SearchTestTags.ayahText(1, 1))
          ?.let(::highlightRanges) == listOf(HighlightSpanRange(4, 8))
      }

      val secondIntent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الرحمن")
      controller.newIntent(secondIntent)
      awaitCondition {
        findNodeByTag(activity, SearchTestTags.ayahText(1, 1))
          ?.let(::highlightRanges) == listOf(HighlightSpanRange(9, 15))
      }
    }
  }

  @Test
  fun `inline listen action launches PagerActivity with HybridReaderContract autoPlay extras and scope switch renders suras`() {
    registerFakeProvider { _, _ ->
      MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
        addRow(arrayOf<Any>(1, 1, 1, "بسم الله الرحمن الرحيم"))
      }
    }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الله")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      activity.quranSettings.setMigratedLegacyBookmarksToMobileSync()
      activity.readerReadinessTracker.markReady(activity.quranSettings.pageType)
      ensureComposeMeasured(activity)

      awaitCondition {
        findNodeByTag(activity, SearchTestTags.listenButton(1, 1)) != null
      }

      performClick(findNodeByTag(activity, SearchTestTags.listenButton(1, 1))!!)
      shadowOf(Looper.getMainLooper()).idle()

      val playIntent = shadowOf(activity).nextStartedActivity
      assertThat(playIntent).isNotNull()
      assertThat(playIntent.component?.className).isEqualTo(PagerActivity::class.java.name)
      assertThat(playIntent.getIntExtra(HybridReaderContract.EXTRA_HIGHLIGHT_SURA, -1)).isEqualTo(1)
      assertThat(playIntent.getIntExtra(HybridReaderContract.EXTRA_HIGHLIGHT_AYAH, -1)).isEqualTo(1)
      assertThat(playIntent.getBooleanExtra(HybridReaderContract.EXTRA_AUTO_PLAY_AYAH, false)).isTrue()
      assertThat(playIntent.getStringExtra(HybridReaderContract.EXTRA_INITIAL_STUDY_TAB))
        .isEqualTo(HybridReaderContract.StudyTab.RECITATION.extraValue)

      // Switch to Suras scope and verify 114 suras list renders
      performClick(findNodeByTag(activity, SearchTestTags.CLEAR_BUTTON)!!)
      performClick(findNodeByTag(activity, SearchTestTags.SCOPE_SURAS)!!)
      awaitCondition {
        findNodeByTag(activity, SearchTestTags.suraCard(1)) != null
      }
    }
  }

  @Test
  fun `inline bookmark action toggles BookmarksDao and updates button label`() {
    registerFakeProvider { _, _ ->
      MatrixCursor(arrayOf("_id", "sura", "ayah", "text")).apply {
        addRow(arrayOf<Any>(1, 1, 1, "بسم الله الرحمن الرحيم"))
      }
    }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الله")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      awaitCondition {
        findNodeByTag(activity, SearchTestTags.bookmarkButton(1, 1)) != null
      }

      val initialLabel = nodeText(findNodeByTag(activity, SearchTestTags.bookmarkButton(1, 1))!!)
      assertThat(initialLabel).isEqualTo(activity.getString(R.string.hybrid_search_action_bookmark))

      performClick(findNodeByTag(activity, SearchTestTags.bookmarkButton(1, 1))!!)
      awaitCondition {
        nodeText(findNodeByTag(activity, SearchTestTags.bookmarkButton(1, 1))!!) ==
          activity.getString(R.string.hybrid_search_action_bookmarked)
      }

      performClick(findNodeByTag(activity, SearchTestTags.bookmarkButton(1, 1))!!)
      awaitCondition {
        nodeText(findNodeByTag(activity, SearchTestTags.bookmarkButton(1, 1))!!) ==
          activity.getString(R.string.hybrid_search_action_bookmark)
      }
    }
  }

  @Test
  fun `floating bottom nav routes READ to PagerActivity and INDEX to QuranActivity when ready`() {
    registerFakeProvider { _, _ -> null }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الله")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      activity.quranSettings.setMigratedLegacyBookmarksToMobileSync()
      activity.readerReadinessTracker.markReady(activity.quranSettings.pageType)
      ensureComposeMeasured(activity)

      val readLabel = activity.getString(R.string.hybrid_nav_read)
      awaitCondition { findNodeByText(activity, readLabel) != null }
      performClick(findNodeByText(activity, readLabel)!!)
      shadowOf(Looper.getMainLooper()).idle()

      val readerIntent = shadowOf(activity).nextStartedActivity
      assertThat(readerIntent).isNotNull()
      assertThat(readerIntent.component?.className).isEqualTo(PagerActivity::class.java.name)
      assertThat(readerIntent.getIntExtra("page", -1)).isEqualTo(1)

      val indexLabel = activity.getString(R.string.hybrid_nav_index)
      performClick(findNodeByText(activity, indexLabel)!!)
      shadowOf(Looper.getMainLooper()).idle()

      val homeIntent = shadowOf(activity).nextStartedActivity
      assertThat(homeIntent).isNotNull()
      assertThat(homeIntent.component?.className)
        .isEqualTo(com.quran.labs.androidquran.ui.QuranActivity::class.java.name)
      assertThat(
        homeIntent.getStringExtra(com.quran.labs.androidquran.ui.QuranActivity.EXTRA_INITIAL_NAV_TAB)
      ).isEqualTo(HybridNavTab.INDEX.name)
    }
  }

  @Test
  fun `floating bottom nav routes READ to dailyWirdRepository lastPage and BOOKMARKS with EXTRA_INITIAL_NAV_TAB`() {
    registerFakeProvider { _, _ -> null }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الله")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      activity.quranSettings.setMigratedLegacyBookmarksToMobileSync()
      activity.readerReadinessTracker.markReady(activity.quranSettings.pageType)
      try {
        runBlocking {
          activity.dailyWirdRepository.updateLastLocation(
            suraName = "سورة البقرة",
            ayah = 1,
            page = 42,
            juz = 3
          )
        }
        ensureComposeMeasured(activity)

        val readLabel = activity.getString(R.string.hybrid_nav_read)
        awaitCondition { findNodeByText(activity, readLabel) != null }
        performClick(findNodeByText(activity, readLabel)!!)
        shadowOf(Looper.getMainLooper()).idle()

        val readerIntent = shadowOf(activity).nextStartedActivity
        assertThat(readerIntent).isNotNull()
        assertThat(readerIntent.component?.className).isEqualTo(PagerActivity::class.java.name)
        assertThat(readerIntent.getIntExtra("page", -1)).isEqualTo(42)

        val bookmarksLabel = activity.getString(R.string.hybrid_nav_bookmarks)
        performClick(findNodeByText(activity, bookmarksLabel)!!)
        shadowOf(Looper.getMainLooper()).idle()

        val bookmarksIntent = shadowOf(activity).nextStartedActivity
        assertThat(bookmarksIntent).isNotNull()
        assertThat(bookmarksIntent.component?.className)
          .isEqualTo(com.quran.labs.androidquran.ui.QuranActivity::class.java.name)
        assertThat(
          bookmarksIntent.getStringExtra(com.quran.labs.androidquran.ui.QuranActivity.EXTRA_INITIAL_NAV_TAB)
        ).isEqualTo(HybridNavTab.BOOKMARKS.name)
      } finally {
        runBlocking {
          activity.dailyWirdRepository.updateLastLocation(
            suraName = "",
            ayah = 1,
            page = 1,
            juz = 1
          )
        }
      }
    }
  }

  @Test
  fun `tafsir LazyColumn renders duplicate translator hits cleanly`() {
    registerFakeProvider { uri, _ ->
      if (uri == QuranDataProvider.SEARCH_TAFSIR_URI) {
        MatrixCursor(
          arrayOf("_id", "sura", "ayah", "text", "translator_name", "translation_id", "language_code")
        ).apply {
          addRow(arrayOf<Any>(1L, 1, 1, "تفسير أول للرحمن", "مجمع الملك فهد", 10L, "ar"))
          addRow(arrayOf<Any>(1L, 1, 1, "تفسير ثان للرحمن", "مجمع الملك فهد", 11L, "ar"))
        }
      } else {
        null
      }
    }

    val intent = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "الرحمن")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().start().resume().visible().get()
      val provider = QuranDataProvider()
      (activity.application as QuranApplication).applicationComponent.inject(provider)

      val dbDir = activity.quranFileUtils.getQuranDatabaseDirectory()
      dbDir.mkdirs()
      val file1 = File(dbDir, "t10.db").apply { createNewFile() }
      val file2 = File(dbDir, "t11.db").apply { createNewFile() }

      try {
        runBlocking {
          provider.translationsDBAdapter.writeTranslationUpdates(
            listOf(
              TranslationItem(
                translation = Translation(
                  id = 10,
                  minimumVersion = 1,
                  currentVersion = 1,
                  displayName = "تفسير ١",
                  downloadType = "translation",
                  fileName = file1.name,
                  fileUrl = "https://example.com/10",
                  saveTo = file1.name,
                  languageCode = "ar",
                  translator = "مجمع الملك فهد"
                ),
                localVersion = 1,
                displayOrder = 1
              ),
              TranslationItem(
                translation = Translation(
                  id = 11,
                  minimumVersion = 1,
                  currentVersion = 1,
                  displayName = "تفسير ٢",
                  downloadType = "translation",
                  fileName = file2.name,
                  fileUrl = "https://example.com/11",
                  saveTo = file2.name,
                  languageCode = "ar",
                  translator = "مجمع الملك فهد"
                ),
                localVersion = 1,
                displayOrder = 2
              )
            )
          )
        }

        activity.hybridSearchEngine.submitQuery("الرحمن", scopeOverride = HybridSearchScope.TAFSIR)
        awaitCondition {
          activity.hybridSearchEngine.uiState.value.tafsirHits.size == 2 &&
            findAllNodesByTag(activity, SearchTestTags.tafsirCard(1, 1)).size == 2
        }

        ensureComposeMeasured(activity)
        val cards = findAllNodesByTag(activity, SearchTestTags.tafsirCard(1, 1))
        assertThat(cards).hasSize(2)
        assertThat(cards.map { nodeText(it) }.joinToString("\n")).contains("تفسير أول للرحمن")
        assertThat(cards.map { nodeText(it) }.joinToString("\n")).contains("تفسير ثان للرحمن")
      } finally {
        runBlocking {
          provider.translationsDBAdapter.deleteTranslationByFileName(file1.name)
          provider.translationsDBAdapter.deleteTranslationByFileName(file2.name)
        }
        file1.delete()
        file2.delete()
      }
    }
  }

  private fun ensureComposeMeasured(activity: SearchActivity) {
    val mainLooper = shadowOf(Looper.getMainLooper())
    Choreographer.getInstance().postFrameCallback { }
    mainLooper.idleFor(Duration.ofMillis(20))
    val decor = activity.window.decorView
    forceLayoutTree(decor)
    decor.measure(
      View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(1604, View.MeasureSpec.EXACTLY)
    )
    decor.layout(0, 0, 720, 1604)
    Choreographer.getInstance().postFrameCallback { }
    mainLooper.idleFor(Duration.ofMillis(20))
  }

  private fun forceLayoutTree(view: View) {
    view.forceLayout()
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        forceLayoutTree(view.getChildAt(i))
      }
    }
  }

  private fun findNodeByTag(activity: SearchActivity, tag: String): View? {
    ensureComposeMeasured(activity)
    return findViewRecursive(activity.rootView, tag)
  }

  private fun findAllNodesByTag(activity: SearchActivity, tag: String): List<View> {
    ensureComposeMeasured(activity)
    val matches = mutableListOf<View>()
    fun collect(view: View) {
      if (view.visibility == View.VISIBLE && view.tag == tag) {
        matches.add(view)
      }
      if (view is ViewGroup && view.visibility == View.VISIBLE) {
        for (i in 0 until view.childCount) {
          collect(view.getChildAt(i))
        }
      }
    }
    collect(activity.rootView)
    return matches
  }

  private fun findNodeByText(activity: SearchActivity, text: String): View? {
    ensureComposeMeasured(activity)
    fun walk(view: View): View? {
      if (view.visibility != View.VISIBLE) return null
      if (view.isClickable && nodeText(view) == text) {
        return view
      }
      if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
          walk(view.getChildAt(i))?.let { return it }
        }
      }
      return null
    }
    return walk(activity.rootView)
  }

  private fun findViewRecursive(view: View, tag: String): View? {
    if (view.visibility != View.VISIBLE) return null
    if (view.tag == tag) return view
    if (view is ViewGroup) {
      for (i in 0 until view.childCount) {
        findViewRecursive(view.getChildAt(i), tag)?.let { return it }
      }
    }
    return null
  }

  private fun nodeText(view: View): String {
    val sb = StringBuilder()
    fun collect(v: View) {
      if (v.visibility != View.VISIBLE) return
      if (v is TextView) {
        sb.append(v.text ?: "")
      } else if (v is ViewGroup) {
        for (i in 0 until v.childCount) {
          collect(v.getChildAt(i))
        }
      }
    }
    collect(view)
    return sb.toString()
  }

  private fun highlightRanges(view: View): List<HighlightSpanRange> {
    val spanned = (view as? TextView)?.text as? Spanned ?: return emptyList()
    return spanned.getSpans(0, spanned.length, ForegroundColorSpan::class.java)
      .map { HighlightSpanRange(spanned.getSpanStart(it), spanned.getSpanEnd(it)) }
      .sortedBy { it.start }
  }

  private fun performClick(view: View) {
    assertThat(view.performClick()).isTrue()
  }

  private fun registerFakeProvider(handler: (Uri, Array<out String>?) -> Cursor?) {
    FakeSearchContentProvider.queryHandler = handler
    Robolectric.buildContentProvider(FakeSearchContentProvider::class.java)
      .create(QuranDataProvider.SEARCH_URI.authority!!)
  }

  private fun awaitCondition(timeoutMs: Long = 6000L, predicate: () -> Boolean) {
    val mainLooper = shadowOf(Looper.getMainLooper())
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!predicate() && System.currentTimeMillis() < deadline) {
      Choreographer.getInstance().postFrameCallback { }
      mainLooper.idleFor(Duration.ofMillis(20))
      Thread.sleep(10)
    }
    Choreographer.getInstance().postFrameCallback { }
    mainLooper.idleFor(Duration.ofMillis(20))
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
