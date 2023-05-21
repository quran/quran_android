package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.JuzView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fragment that displays a list of all Juz (using [QuranListAdapter], each divided into
 * 8 parts (with headings for each Juz).
 * When a Juz part is selected (or a Juz heading), [QuranActivity.jumpTo] is called to
 * jump to that page.
 */
class JuzListFragment : Fragment() {
  private var recyclerView: RecyclerView? = null
  private var disposable: Disposable? = null
  private var adapter: QuranListAdapter? = null
  private val mainScope: CoroutineScope = MainScope()

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var juzListPresenter: JuzListPresenter

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
    return view
  }

  override fun onDestroyView() {
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

    mainScope.launch {
      fetchJuz2List()
    }
  }

  override fun onPause() {
    disposable?.dispose()
    super.onPause()
  }

  override fun onResume() {
    val activity = requireActivity()
    if (activity is QuranActivity) {
      disposable = activity.latestPageObservable
        .first(Constants.NO_PAGE)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableSingleObserver<Int>() {
          override fun onSuccess(recentPage: Int) {
            if (recentPage != Constants.NO_PAGE) {
              val juz = quranInfo.getJuzFromPage(recentPage)
              val position = (juz - 1) * 9
              recyclerView?.scrollToPosition(position)
            }
          }

          override fun onError(e: Throwable) {}
        })
    }

    val settings = QuranSettings.getInstance(activity)
    if (settings.isArabicNames) {
      updateScrollBarPositionHoneycomb()
    }
    super.onResume()
  }

  private fun updateScrollBarPositionHoneycomb() {
    recyclerView?.verticalScrollbarPosition = View.SCROLLBAR_POSITION_LEFT
  }

  private suspend fun fetchJuz2List() {
    val quarters = if (QuranFileConstants.FETCH_QUARTER_NAMES_FROM_DATABASE) {
      juzListPresenter.quarters().toTypedArray()
    } else {
      val context = context
      if (context != null) {
        val res = context.resources
        res.getStringArray(R.array.quarter_prefix_array)
      } else {
        emptyArray<String>()
      }
    }

    if (isAdded && quarters.isNotEmpty()) {
      updateJuz2List(quarters)
    }
  }

  private fun updateJuz2List(quarters: Array<String>) {
    val activity: Activity = activity ?: return

    val elements = arrayOfNulls<QuranRow>(Constants.JUZ2_COUNT * (8 + 1))
    var ctr = 0
    for (i in 0 until 8 * Constants.JUZ2_COUNT) {
      val pos = quranInfo.getQuarterByIndex(i)
      val page = quranInfo.getPageFromSuraAyah(pos.sura, pos.ayah)
      if (i % 8 == 0) {
        val juz = 1 + i / 8
        val juzTitle = activity.getString(
          R.string.juz2_description,
          QuranUtils.getLocalizedNumber(activity, juz)
        )
        val builder = Builder()
          .withType(QuranRow.HEADER)
          .withText(juzTitle)
          .withPage(quranInfo.getStartingPageForJuz(juz))
        elements[ctr++] = builder.build()
      }
      val metadata = getString(
        R.string.sura_ayah_notification_str,
        quranDisplayData.getSuraName(activity, pos.sura, false), pos.ayah
      )
      val juzTextWithEllipsis = quarters[i] + "..."
      val builder = Builder()
        .withText(juzTextWithEllipsis)
        .withMetadata(metadata)
        .withPage(page)
        .withJuzType(ENTRY_TYPES[i % 4])
      if (i % 4 == 0) {
        val overlayText = QuranUtils.getLocalizedNumber(activity, 1 + i / 4)
        builder.withJuzOverlayText(overlayText)
      }
      elements[ctr++] = builder.build()
    }
    adapter?.setElements(elements.filterNotNull().toTypedArray())
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
