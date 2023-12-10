package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.AyahInteractionHandler
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTranslationTrackerItem
import com.quran.labs.androidquran.presenter.translation.TranslationPresenter
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.QuranPage
import com.quran.labs.androidquran.ui.translation.TranslationView
import com.quran.labs.androidquran.ui.util.PageController
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.view.QuranTranslationPageLayout
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class TranslationFragment : Fragment(), AyahInteractionHandler, QuranPage,
  TranslationPresenter.TranslationScreen, PageController {
  private var pageNumber = 0
  private var scrollPosition = 0
  private var ayahTrackerItems: Array<AyahTrackerItem>? = null

  private lateinit var mainView: QuranTranslationPageLayout
  private lateinit var translationView: TranslationView

  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var presenter: TranslationPresenter
  @Inject lateinit var ayahTrackerPresenter: AyahTrackerPresenter
  @Inject lateinit var ayahSelectedListener: AyahSelectedListener
  @Inject lateinit var readingEventPresenter: ReadingEventPresenter

  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      scrollPosition = savedInstanceState.getInt(SI_SCROLL_POSITION)
    }
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    val context: Context? = activity
    mainView = QuranTranslationPageLayout(context)
    mainView.setPageController(this, pageNumber, quranInfo.skip)
    translationView = mainView.translationView
    translationView.setTranslationClickedListener {
      val activity: Activity? = activity
      (activity as? PagerActivity?)?.toggleActionBar()
    }
    return mainView
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)

    val arguments = arguments
    pageNumber = arguments?.getInt(PAGE_NUMBER_EXTRA) ?: -1
    val pages = intArrayOf(pageNumber)
    (activity as? PagerActivity)?.getPagerActivityComponent()
      ?.quranPageComponentFactory()
      ?.generate(pages)
      ?.inject(this)
  }

  override fun onDetach() {
    scope.cancel()
    super.onDetach()
  }

  override fun updateView() {
    if (isAdded) {
      mainView.updateView(quranSettings)
      refresh()
    }
  }

  override fun getAyahTracker(): AyahTracker {
    return ayahTrackerPresenter
  }

  override fun getAyahTrackerItems(): Array<AyahTrackerItem> {
    val items = ayahTrackerItems
    return if (items == null) {
      val elements: Array<AyahTrackerItem> = arrayOf(
        AyahTranslationTrackerItem(pageNumber, quranInfo, translationView)
      )
      ayahTrackerItems = elements
      elements
    } else {
      items
    }
  }

  override fun onResume() {
    super.onResume()
    ayahTrackerPresenter.bind(this)
    presenter.bind(this)
    updateView()
  }

  override fun onPause() {
    ayahTrackerPresenter.unbind(this)
    presenter.unbind(this)
    super.onPause()
  }

  override fun setVerses(
    page: Int,
    translations: Array<LocalTranslation>,
    verses: List<QuranAyahInfo>
  ) {
    translationView.setVerses(quranDisplayData, translations, verses)
  }

  override fun updateScrollPosition() {
    translationView.setScrollPosition(scrollPosition)
  }

  fun refresh() {
    scope.launch {
      presenter.refresh()
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    scrollPosition = translationView.findFirstCompletelyVisibleItemPosition()
    outState.putInt(SI_SCROLL_POSITION, scrollPosition)
    super.onSaveInstanceState(outState)
  }

  override fun handleTouchEvent(
    event: MotionEvent,
    eventType: AyahSelectedListener.EventType,
    page: Int
  ): Boolean {
    return false
  }

  override fun handleRetryClicked() {}
  override fun onScrollChanged(y: Float) {
    if (isVisible) {
      val ayahSelection = readingEventPresenter.currentAyahSelection()
      if (ayahSelection is AyahSelection.Ayah) {
        val (suraAyah) = ayahSelection
        readingEventPresenter.onAyahSelection(
          AyahSelection.Ayah(
            suraAyah,
            translationView.getToolbarPosition(suraAyah.sura, suraAyah.ayah)
          )
        )
      }
    }
  }

  override fun handleLongPress(suraAyah: SuraAyah) {
    if (isVisible) {
      readingEventPresenter.onAyahSelection(
        AyahSelection.Ayah(
          suraAyah,
          translationView.getToolbarPosition(suraAyah.sura, suraAyah.ayah)
        )
      )
    }
  }

  override fun endAyahMode() {
    if (isVisible) {
      ayahTrackerPresenter.endAyahMode()
    }
  }

  companion object {
    private const val PAGE_NUMBER_EXTRA = "pageNumber"
    private const val SI_SCROLL_POSITION = "SI_SCROLL_POSITION"
    fun newInstance(page: Int): TranslationFragment {
      val f = TranslationFragment()
      val args = Bundle()
      args.putInt(PAGE_NUMBER_EXTRA, page)
      f.setArguments(args)
      return f
    }
  }
}
