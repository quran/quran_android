package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.model.bookmark.ReadingBookmark
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.Constants.JUZ2_COUNT
import com.quran.labs.androidquran.data.Constants.SURAS_COUNT
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter.QuranTouchListener
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.ui.helpers.QuranRowFactory
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SuraListFragment : Fragment(), QuranTouchListener {

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var quranSettings: QuranSettings

  @Inject
  lateinit var readingBookmarksDao: ReadingBookmarksDao

  @Inject
  lateinit var quranRowFactory: QuranRowFactory

  private lateinit var recyclerView: RecyclerView
  private var numberOfPages = 0
  private var showSuraTranslatedName = false
  private var readingBookmark: ReadingBookmark? = null

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
    numberOfPages = quranInfo.numberOfPages
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view: View = inflater.inflate(R.layout.quran_list, container, false)
    recyclerView = view.findViewById(R.id.recycler_view)
    showSuraTranslatedName = quranSettings.isShowSuraTranslatedName
    val quranListAdapter = QuranListAdapter(requireActivity(), recyclerView, getSuraList(), false)
    quranListAdapter.setQuranTouchListener(this)
    recyclerView.apply {
      layoutManager = LinearLayoutManager(context)
      itemAnimator = DefaultItemAnimator()
      adapter = quranListAdapter
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        readingBookmarksDao.readingBookmarkFlow()
          .distinctUntilChanged()
          .collect { updateReadingBookmark(it) }
      }
    }

    ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        // top, left, right are handled by QuranActivity
        view.setPadding(0, 0, 0, insets.bottom)
      }

      // if we return WindowInsetsCompat.CONSUMED, the SnackBar won't
      // be properly positioned on Android 29 and below (will be under
      // the navigation bar).
      windowInsets
    }
    return view
  }

  override fun onResume() {
    super.onResume()
    val activity = requireActivity()
    if (activity is QuranActivity) {
      val newValueOfShowSuraTranslatedName = quranSettings.isShowSuraTranslatedName
      if (showSuraTranslatedName != newValueOfShowSuraTranslatedName) {
        showHideSuraTranslatedName()
        showSuraTranslatedName = newValueOfShowSuraTranslatedName
      }
      viewLifecycleOwner.lifecycleScope.launch {
        val currentReadingBookmark = readingBookmarksDao.readingBookmark()
        updateReadingBookmark(currentReadingBookmark)
        val recentPage = activity.latestPage()
        if (recentPage != Constants.NO_PAGE) {
          val sura = quranDisplayData.safelyGetSuraOnPage(recentPage)
          val juz = quranInfo.getJuzFromPage(recentPage)
          val position = sura + juz - 1 + readingBookmarkOffset()
          recyclerView.scrollToPosition(position)
        }
      }

      if (QuranUtils.isRtl()) {
        updateScrollBarPositionHoneycomb()
      }
    }
  }

  private fun updateScrollBarPositionHoneycomb() {
    recyclerView.verticalScrollbarPosition = View.SCROLLBAR_POSITION_LEFT
  }

  private fun getSuraList(): Array<QuranRow> {
    var next: Int
    var pos = 0
    var sura = 1
    val readingBookmark = readingBookmark
    val elements = arrayOfNulls<QuranRow>(
      SURAS_COUNT + JUZ2_COUNT + if (readingBookmark == null) 0 else READING_BOOKMARK_ROWS
    )

    val activity: Activity = requireActivity()
    if (readingBookmark != null) {
      elements[pos++] = quranRowFactory.fromReadingBookmarkHeader(activity)
      elements[pos++] = quranRowFactory.fromReadingBookmark(activity, readingBookmark)
    }

    val wantPrefix = activity.resources.getBoolean(R.bool.show_surat_prefix)
    val wantTranslation = quranSettings.isShowSuraTranslatedName
    for (juz in 1..JUZ2_COUNT) {
      val headerTitle = activity.getString(
        R.string.juz2_description,
        QuranUtils.getLocalizedNumber(juz)
      )
      val headerBuilder = QuranRow.Builder()
        .withType(QuranRow.HEADER)
        .withText(headerTitle)
        .withPage(quranInfo.getStartingPageForJuz(juz))
      elements[pos++] = headerBuilder.build()
      next = if (juz == JUZ2_COUNT) {
        numberOfPages + 1
      } else quranInfo.getStartingPageForJuz(juz + 1)

      while (sura <= SURAS_COUNT && quranInfo.getPageNumberForSura(sura) < next) {
        val builder = QuranRow.Builder()
          .withText(quranDisplayData.getSuraName(activity, sura, wantPrefix, wantTranslation))
          .withMetadata(quranDisplayData.getSuraListMetaString(activity, sura))
          .withSura(sura)
          .withPage(quranInfo.getPageNumberForSura(sura))
        elements[pos++] = builder.build()
        sura++
      }
    }
    return elements.filterNotNull().toTypedArray()
  }

  private fun showHideSuraTranslatedName() {
    updateSuraList()
  }

  private fun updateReadingBookmark(readingBookmark: ReadingBookmark?) {
    if (this.readingBookmark != readingBookmark) {
      this.readingBookmark = readingBookmark
      updateSuraList()
    }
  }

  private fun updateSuraList() {
    (recyclerView.adapter as QuranListAdapter).setElements(getSuraList())
  }

  private fun readingBookmarkOffset(): Int {
    return if (readingBookmark == null) 0 else READING_BOOKMARK_ROWS
  }

  override fun onClick(row: QuranRow, position: Int) {
    val activity = activity as? QuranActivity
    if (activity != null && row.page != 0) {
      if (row.isAyahBookmark) {
        activity.jumpToAndHighlight(row.page, row.sura, row.ayah)
      } else {
        activity.jumpTo(row.page)
      }
    }
  }

  override fun onLongClick(row: QuranRow, position: Int): Boolean {
    return false
  }

  companion object {
    private const val READING_BOOKMARK_ROWS = 2

    fun newInstance(): SuraListFragment = SuraListFragment()
  }
}
