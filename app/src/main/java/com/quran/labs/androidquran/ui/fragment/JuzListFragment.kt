package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.data.QuranFileConstants
import com.quran.labs.androidquran.presenter.data.JuzListPresenter
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.ui.helpers.QuranRow.Builder
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.JuzView
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment that displays a list of all Juz (using [QuranListAdapter], each divided into
 * 8 parts (with headings for each Juz).
 * When a Juz part is selected (or a Juz heading), [QuranActivity.jumpTo] is called to
 * jump to that page.
 */
class JuzListFragment : Fragment() {
  private var recyclerView: RecyclerView? = null
  private var adapter: QuranListAdapter? = null
  private var pendingScrollPosition: Int? = null

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var juzListPresenter: Provider<JuzListPresenter>

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.quran_list, container, false)

    val context = requireContext()
    val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view).apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(context)
      itemAnimator = DefaultItemAnimator()
    }

    val adapter = QuranListAdapter(context, recyclerView, emptyArray(), false)
    recyclerView.adapter = adapter
    this.recyclerView = recyclerView
    this.adapter = adapter

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

  override fun onDestroyView() {
    pendingScrollPosition = null
    adapter = null
    recyclerView = null
    super.onDestroyView()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Off-screen on initial QuranActivity launch; defer starting background row generation past
    // the first frame so it does not compete with SuraListFragment rendering.
    view.post {
      if (isAdded && getView() != null) {
        viewLifecycleOwner.lifecycleScope.launch {
          fetchJuz2List()
        }
      }
    }
  }

  override fun onResume() {
    val activity = requireActivity()
    if (activity is QuranActivity) {
      view?.post {
        if (isAdded && view != null) {
          viewLifecycleOwner.lifecycleScope.launch {
            val recentPage = activity.latestPage()
            if (recentPage != Constants.NO_PAGE) {
              val juz = quranInfo.getJuzFromPage(recentPage)
              val position = (juz - 1) * 9
              scrollToJuzPosition(position)
            }
          }
        }
      }
    }

    if (QuranUtils.isRtl()) {
      updateScrollBarPositionHoneycomb()
    }
    super.onResume()
  }

  internal fun scrollToJuzPosition(position: Int) {
    if (position < 0) {
      return
    }
    val currentAdapter = adapter
    val currentRecycler = recyclerView
    if (currentAdapter != null && currentRecycler != null && currentAdapter.itemCount > position) {
      pendingScrollPosition = null
      currentRecycler.scrollToPosition(position)
    } else {
      pendingScrollPosition = position
    }
  }

  private fun updateScrollBarPositionHoneycomb() {
    recyclerView?.verticalScrollbarPosition = View.SCROLLBAR_POSITION_LEFT
  }

  private suspend fun fetchJuz2List() {
    val hostContext = context ?: return
    val rows = withContext(Dispatchers.Default) {
      val quarters = if (QuranFileConstants.FETCH_QUARTER_NAMES_FROM_DATABASE) {
        juzListPresenter().quarters().toTypedArray()
      } else {
        hostContext.resources.getStringArray(R.array.quarter_prefix_array)
      }
      if (quarters.isEmpty()) {
        emptyArray()
      } else {
        buildJuz2Rows(hostContext, quarters)
      }
    }

    if (isAdded && rows.isNotEmpty()) {
      applyJuzRows(rows)
    }
  }

  internal fun applyJuzRows(rows: Array<QuranRow>) {
    val currentAdapter = adapter ?: return
    currentAdapter.setElements(rows)
    val scrollTarget = pendingScrollPosition
    if (scrollTarget != null && scrollTarget in rows.indices) {
      pendingScrollPosition = null
      recyclerView?.scrollToPosition(scrollTarget)
    }
  }

  private fun buildJuz2Rows(hostContext: Context, quarters: Array<String>): Array<QuranRow> {
    val elements = arrayOfNulls<QuranRow>(Constants.JUZ2_COUNT * (8 + 1))
    var ctr = 0
    for (i in 0 until 8 * Constants.JUZ2_COUNT) {
      val pos = quranInfo.getQuarterByIndex(i)
      val page = quranInfo.getPageFromSuraAyah(pos.sura, pos.ayah)
      if (i % 8 == 0) {
        val juz = 1 + i / 8
        val juzTitle = quranDisplayData.getJuzDisplayString(hostContext, juz)
        val builder = Builder()
          .withType(QuranRow.HEADER)
          .withText(juzTitle)
          .withPage(quranInfo.getStartingPageForJuz(juz))
        elements[ctr++] = builder.build()
      }
      val metadata = hostContext.getString(
        R.string.sura_ayah_notification_str,
        quranDisplayData.getSuraName(hostContext, pos.sura, false), pos.ayah
      )
      val juzTextWithEllipsis = quarters[i] + "..."
      val builder = Builder()
        .withText(juzTextWithEllipsis)
        .withMetadata(metadata)
        .withPage(page)
        .withJuzType(ENTRY_TYPES[i % 4])
      if (i % 4 == 0) {
        val overlayText = QuranUtils.getLocalizedNumber(1 + i / 4)
        builder.withJuzOverlayText(overlayText)
      }
      elements[ctr++] = builder.build()
    }
    return elements.filterNotNull().toTypedArray()
  }

  companion object {
    private val ENTRY_TYPES = intArrayOf(
      JuzView.TYPE_JUZ, JuzView.TYPE_QUARTER,
      JuzView.TYPE_HALF, JuzView.TYPE_THREE_QUARTERS
    )

    fun newInstance(): JuzListFragment {
      return JuzListFragment()
    }
  }
}
