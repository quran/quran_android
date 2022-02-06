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
import com.quran.labs.androidquran.data.Constants.JUZ2_COUNT
import com.quran.labs.androidquran.data.Constants.SURAS_COUNT
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import javax.inject.Inject

class SuraListFragment : Fragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var quranSettings: QuranSettings

  private lateinit var recyclerView: RecyclerView
  private var numberOfPages = 0
  private var showSuraTranslatedName = false
  private var disposable: Disposable? = null

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
    recyclerView.apply {
      layoutManager = LinearLayoutManager(context)
      itemAnimator = DefaultItemAnimator()
      adapter = QuranListAdapter(requireActivity(), recyclerView, getSuraList(), false)
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
      disposable = (requireActivity() as QuranActivity).latestPageObservable
        .first(Constants.NO_PAGE)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableSingleObserver<Int>() {
          override fun onSuccess(recentPage: Int) {
            if (recentPage != Constants.NO_PAGE) {
              val sura = quranDisplayData.safelyGetSuraOnPage(recentPage)
              val juz = quranInfo.getJuzFromPage(recentPage)
              val position = sura + juz - 1
              recyclerView.scrollToPosition(position)
            }
          }

          override fun onError(e: Throwable) {}
        })

      if (quranSettings.isArabicNames) updateScrollBarPositionHoneycomb()
    }
  }

  override fun onPause() {
    disposable?.dispose()
    super.onPause()
  }

  private fun updateScrollBarPositionHoneycomb() {
    recyclerView.verticalScrollbarPosition = View.SCROLLBAR_POSITION_LEFT
  }

  private fun getSuraList(): Array<QuranRow> {
    var next: Int
    var pos = 0
    var sura = 1
    val elements = arrayOfNulls<QuranRow>(SURAS_COUNT + JUZ2_COUNT)

    val activity: Activity = requireActivity()
    val wantPrefix = activity.resources.getBoolean(R.bool.show_surat_prefix)
    val wantTranslation = quranSettings.isShowSuraTranslatedName
    for (juz in 1..JUZ2_COUNT) {
      val headerTitle = activity.getString(
        R.string.juz2_description,
        QuranUtils.getLocalizedNumber(activity, juz)
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
    val elements = getSuraList()
    (recyclerView.adapter as QuranListAdapter).setElements(elements)
    recyclerView.adapter?.notifyDataSetChanged()
  }

  companion object {
    fun newInstance(): SuraListFragment = SuraListFragment()
  }
}
