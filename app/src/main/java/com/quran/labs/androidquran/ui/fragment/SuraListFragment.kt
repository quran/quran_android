package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.core.QuranInfo
import com.quran.data.dao.ReadingBookmarksDao
import com.quran.data.model.bookmark.EmptyReadingBookmark
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
  private var readingBookmarks: List<ReadingBookmark> = emptyList()

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
        readingBookmarksDao.readingBookmarksFlow()
          .distinctUntilChanged()
          .collect { updateReadingBookmarks(it) }
      }
    }
    // top, left, right insets are handled by QuranActivity; the bottom inset is owned by the
    // bottom navigation bar, so the list itself needs no inset padding.
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
        readingBookmarks = placedReadingBookmarks(readingBookmarksDao.readingBookmarks())
        updateSuraList()
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
    val readingBookmarks = readingBookmarks
    val elements = arrayOfNulls<QuranRow>(SURAS_COUNT + JUZ2_COUNT + readingBookmarkOffset())

    val activity: Activity = requireActivity()
    if (readingBookmarks.isNotEmpty()) {
      elements[pos++] = quranRowFactory.fromReadingBookmarkHeader(activity, readingBookmarks.size)
      for (readingBookmark in readingBookmarks) {
        elements[pos++] = quranRowFactory.fromReadingBookmark(activity, readingBookmark)
      }
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

  private fun updateReadingBookmarks(readingBookmarks: List<ReadingBookmark>) {
    val placed = placedReadingBookmarks(readingBookmarks)
    if (this.readingBookmarks != placed) {
      this.readingBookmarks = placed
      updateSuraList()
    }
  }

  private fun placedReadingBookmarks(
    readingBookmarks: List<ReadingBookmark>
  ): List<ReadingBookmark> {
    return readingBookmarks
      .filterNot { readingBookmark -> readingBookmark is EmptyReadingBookmark }
      .sortedBy { readingBookmark -> readingBookmark.slot }
  }

  private fun updateSuraList() {
    (recyclerView.adapter as QuranListAdapter).setElements(getSuraList())
  }

  private fun readingBookmarkOffset(): Int {
    return if (readingBookmarks.isEmpty()) 0 else readingBookmarks.size + 1
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
    fun newInstance(): SuraListFragment = SuraListFragment()
  }
}
