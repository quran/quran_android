package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.quran.QuranPagePresenter
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahImageTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahSplitConsolidationTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.AyahInteractionHandler
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTranslationTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.NoOpImageTrackerItem
import com.quran.labs.androidquran.presenter.translation.TranslationPresenter
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.QuranPage
import com.quran.labs.androidquran.ui.translation.TranslationView
import com.quran.labs.androidquran.ui.util.PageController
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.view.HighlightingImageView
import com.quran.labs.androidquran.view.QuranImagePageLayout
import com.quran.labs.androidquran.view.QuranTranslationPageLayout
import com.quran.labs.androidquran.view.TabletView
import com.quran.mobile.translation.model.LocalTranslation
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.page.common.factory.PageViewFactory
import com.quran.page.common.factory.PageViewFactoryProvider
import com.quran.reading.common.ReadingEventPresenter
import dagger.Lazy
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class TabletFragment : Fragment(), PageController, TranslationPresenter.TranslationScreen,
  QuranPage, QuranPageScreen, AyahInteractionHandler {
  object Mode {
    const val ARABIC = 1
    const val TRANSLATION = 2
  }

  private var mode = 0
  private var pageNumber = 0
  private var translationScrollPosition = 0
  private var ayahCoordinatesError = false
  private var isSplitScreen = false
  private var isQuranOnRight = true
  private var leftTranslation: TranslationView? = null
  private var rightTranslation: TranslationView? = null
  private var leftImageView: HighlightingImageView? = null
  private var rightImageView: HighlightingImageView? = null
  private val compositeDisposable = CompositeDisposable()
  private var ayahTrackerItemsStorage: Array<AyahTrackerItem>? = null
  private var splitTranslationView: TranslationView? = null
  private var splitImageView: HighlightingImageView? = null
  private var lastLongPressPage = -1

  private lateinit var mainView: TabletView

  @Inject lateinit var quranSettings: QuranSettings
  @Inject lateinit var ayahTrackerPresenter: AyahTrackerPresenter
  @Inject lateinit var quranPagePresenter: Lazy<QuranPagePresenter>
  @Inject lateinit var translationPresenter: Lazy<TranslationPresenter>
  @Inject lateinit var quranScreenInfo: QuranScreenInfo
  @Inject lateinit var quranInfo: QuranInfo
  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var imageDrawHelpers: Set<@JvmSuppressWildcards ImageDrawHelper>
  @Inject lateinit var readingEventPresenter: ReadingEventPresenter
  @Inject lateinit var pageProviderFactoryProvider: PageViewFactoryProvider

  private var pageViewFactory: PageViewFactory? = null
  private var isCustomArabicPageType = false

  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      translationScrollPosition = savedInstanceState.getInt(
        SI_RIGHT_TRANSLATION_SCROLL_POSITION
      )
    }
    pageViewFactory =
      pageProviderFactoryProvider.providePageViewFactory(quranSettings.pageType)
    isCustomArabicPageType = pageViewFactory != null
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?, savedInstanceState: Bundle?
  ): View {
    val context: Context? = activity
    mainView = TabletView(context)
    if (mode == Mode.ARABIC) {
      mainView.init(
        TabletView.QURAN_PAGE,
        TabletView.QURAN_PAGE,
        pageViewFactory,
        pageNumber + 1,
        pageNumber
      )
      if (mainView.leftPage is QuranImagePageLayout) {
        leftImageView = (mainView.leftPage as QuranImagePageLayout).getImageView()
        rightImageView = (mainView.rightPage as QuranImagePageLayout).getImageView()
      }
      mainView.setPageController(this, pageNumber + 1, pageNumber, quranInfo.skip)
    } else if (mode == Mode.TRANSLATION) {
      if (!isSplitScreen) {
        mainView.init(
          TabletView.TRANSLATION_PAGE,
          TabletView.TRANSLATION_PAGE,
          pageViewFactory,
          pageNumber + 1,
          pageNumber
        )
        val leftTranslation = (mainView.leftPage as QuranTranslationPageLayout).translationView
        val rightTranslation = (mainView.rightPage as QuranTranslationPageLayout).translationView
        val pagerActivity = context as PagerActivity
        leftTranslation.setTranslationClickedListener { pagerActivity.toggleActionBar() }
        rightTranslation.setTranslationClickedListener { pagerActivity.toggleActionBar() }
        this.leftTranslation = leftTranslation
        this.rightTranslation = rightTranslation
        mainView.setPageController(this, pageNumber + 1, pageNumber, quranInfo.skip)
      } else {
        initSplitMode()
      }
    }
    return mainView
  }

  private fun initSplitMode() {
    val skip = quranInfo.skip
    isQuranOnRight = (pageNumber + skip) % 2 == 1
    val leftPageType =
      if (isQuranOnRight) TabletView.TRANSLATION_PAGE else TabletView.QURAN_PAGE
    val rightPageType =
      if (isQuranOnRight) TabletView.QURAN_PAGE else TabletView.TRANSLATION_PAGE
    mainView.init(leftPageType, rightPageType, pageViewFactory, pageNumber, pageNumber)
    if (isQuranOnRight) {
      splitTranslationView =
        (mainView.leftPage as QuranTranslationPageLayout).translationView
      splitImageView = if (mainView.rightPage is QuranImagePageLayout) {
        (mainView.rightPage as QuranImagePageLayout).getImageView()
      } else {
        null
      }
    } else {
      splitImageView = if (mainView.leftPage is QuranImagePageLayout) {
        (mainView.leftPage as QuranImagePageLayout).getImageView()
      } else {
        null
      }
      splitTranslationView = (mainView.rightPage as QuranTranslationPageLayout).translationView
    }
    val pagerActivity = activity as PagerActivity
    splitTranslationView?.setTranslationClickedListener { pagerActivity.toggleActionBar() }
    mainView.setPageController(this, pageNumber, quranInfo.skip)
  }

  override fun onStart() {
    super.onStart()
    ayahTrackerPresenter.bind(this)
    if (mode == Mode.ARABIC) {
      if (!isCustomArabicPageType) {
        quranPagePresenter.get().bind(this)
      }
    } else {
      if (isSplitScreen) {
        translationPresenter.get().bind(this)
        if (!isCustomArabicPageType) {
          quranPagePresenter.get().bind(this)
        }
      } else {
        translationPresenter.get().bind(this)
      }
    }
  }

  override fun onPause() {
    if (mode == Mode.TRANSLATION) {
      val rightTranslation = rightTranslation
      val splitTranslationView = splitTranslationView
      if (isSplitScreen && splitTranslationView != null) {
        translationScrollPosition = splitTranslationView.findFirstCompletelyVisibleItemPosition()
      } else if (rightTranslation != null) {
        translationScrollPosition = rightTranslation.findFirstCompletelyVisibleItemPosition()
      }
    }
    super.onPause()
  }

  override fun onStop() {
    ayahTrackerPresenter.unbind(this)
    if (mode == Mode.ARABIC) {
      quranPagePresenter.get().unbind(this)
    } else {
      if (isSplitScreen) {
        translationPresenter.get().unbind(this)
        quranPagePresenter.get().unbind(this)
      } else {
        translationPresenter.get().unbind(this)
      }
    }
    super.onStop()
  }

  override fun onResume() {
    super.onResume()
    updateView()
    if (mode == Mode.TRANSLATION) {
      if (isSplitScreen) {
        splitTranslationView?.refresh(quranSettings)
      } else {
        rightTranslation?.refresh(quranSettings)
        leftTranslation?.refresh(quranSettings)
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    if (mode == Mode.TRANSLATION) {
      if (isSplitScreen) {
        outState.putInt(
          SI_RIGHT_TRANSLATION_SCROLL_POSITION,
          splitTranslationView!!.findFirstCompletelyVisibleItemPosition()
        )
      } else {
        outState.putInt(
          SI_RIGHT_TRANSLATION_SCROLL_POSITION,
          rightTranslation!!.findFirstCompletelyVisibleItemPosition()
        )
      }
    }
    super.onSaveInstanceState(outState)
  }

  override fun updateView() {
    if (isAdded) {
      mainView.updateView(quranSettings)
    }
  }

  override fun getAyahTracker(): AyahTracker {
    return ayahTrackerPresenter
  }

  override fun getAyahTrackerItems(): Array<AyahTrackerItem> {
    val cachedTrackerItems = ayahTrackerItemsStorage
    return if (cachedTrackerItems == null) {
      if (mode == Mode.ARABIC) {
        val leftImageView = leftImageView
        val rightImageView = rightImageView
        if (leftImageView != null && rightImageView != null) {
          val left = if (quranInfo.isValidPage(pageNumber + 1)) {
            AyahImageTrackerItem(
              pageNumber + 1,
              quranInfo,
              quranDisplayData,
              false,
              imageDrawHelpers,
              leftImageView
            )
          } else {
            NoOpImageTrackerItem(pageNumber + 1)
          }

          val right = AyahImageTrackerItem(
            pageNumber,
            quranInfo,
            quranDisplayData,
            true,
            imageDrawHelpers,
            rightImageView
          )
          arrayOf(right, left)
        } else {
          emptyArray()
        }
      } else if (mode == Mode.TRANSLATION) {
        if (isSplitScreen) {
          val splitTranslationView = splitTranslationView!!
          val (translationItem, imageItem) = if (isQuranOnRight) {
            val translationItem = AyahTranslationTrackerItem(pageNumber, quranInfo, splitTranslationView)
            val splitImageView = splitImageView
            if (splitImageView != null) {
              translationItem to AyahImageTrackerItem(
                pageNumber,
                quranInfo,
                quranDisplayData,
                true,
                imageDrawHelpers,
                splitImageView
              )
            } else {
              translationItem to null
            }
          } else {
            val translationItem = AyahTranslationTrackerItem(pageNumber, quranInfo, splitTranslationView)
            val splitImageView = splitImageView
            if (splitImageView != null) {
              translationItem to AyahImageTrackerItem(pageNumber,
                quranInfo,
                quranDisplayData,
                false,
                imageDrawHelpers,
                splitImageView
              )
            } else {
              translationItem to null
            }
          }

          val splitItem = if (imageItem == null) {
            AyahTranslationTrackerItem(pageNumber, quranInfo, splitTranslationView)
          } else {
            AyahSplitConsolidationTrackerItem(pageNumber, imageItem, translationItem)
          }
          arrayOf(splitItem)
        } else {
          val left = AyahTranslationTrackerItem(pageNumber + 1, quranInfo, leftTranslation!!)
          val right = AyahTranslationTrackerItem(pageNumber, quranInfo, rightTranslation!!)
          arrayOf(right, left)
        }
      } else {
        emptyArray()
      }
    } else {
      cachedTrackerItems
    }
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    val arguments = requireArguments()
    pageNumber = arguments.getInt(FIRST_PAGE_EXTRA)
    mode = arguments.getInt(MODE_EXTRA, Mode.ARABIC)
    isSplitScreen = arguments.getBoolean(IS_SPLIT_SCREEN, false)
    val pages =
      if (isSplitScreen && mode == Mode.TRANSLATION) intArrayOf(pageNumber) else intArrayOf(
        pageNumber,
        pageNumber + 1
      )
    (activity as PagerActivity).getPagerActivityComponent()
      .quranPageComponentFactory()
      .generate(pages)
      .inject(this)
  }

  override fun onDetach() {
    compositeDisposable.clear()
    scope.cancel()
    super.onDetach()
  }

  override fun setPageDownloadError(@StringRes errorMessage: Int) {
    mainView.showError(errorMessage)
    mainView.setOnClickListener { ayahTrackerPresenter.onPressIgnoringSelectionState() }
  }

  override fun setPageBitmap(page: Int, pageBitmap: Bitmap) {
    if (isSplitScreen && mode == Mode.TRANSLATION) {
      splitImageView!!.setImageDrawable(BitmapDrawable(resources, pageBitmap))
    } else {
      val imageView: ImageView? = if (page == pageNumber) rightImageView else leftImageView
      imageView?.setImageDrawable(BitmapDrawable(resources, pageBitmap))
    }
  }

  override fun hidePageDownloadError() {
    mainView.hideError()
    mainView.setOnClickListener(null)
    mainView.isClickable = false
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    if (mode == Mode.TRANSLATION) {
      scope.launch {
        translationPresenter.get().refresh()
      }
    }
  }

  override fun setVerses(
    page: Int,
    translations: Array<LocalTranslation>,
    verses: List<QuranAyahInfo>
  ) {
    if (isSplitScreen) {
      splitTranslationView?.setVerses(quranDisplayData, translations, verses)
    } else {
      if (page == pageNumber) {
        rightTranslation?.setVerses(quranDisplayData, translations, verses)
      } else if (page == pageNumber + 1) {
        leftTranslation?.setVerses(quranDisplayData, translations, verses)
      }
    }
  }

  override fun updateScrollPosition() {
    if (isSplitScreen) {
      splitTranslationView?.setScrollPosition(translationScrollPosition)
    } else {
      rightTranslation?.setScrollPosition(translationScrollPosition)
    }
  }

  fun refresh() {
    if (mode == Mode.TRANSLATION) {
      scope.launch {
        translationPresenter.get().refresh()
      }
    }
  }

  fun cleanup() {
    Timber.d("cleaning up page %d", pageNumber)
    leftImageView?.setImageDrawable(null)
    rightImageView?.setImageDrawable(null)
    splitImageView?.setImageDrawable(null)
  }

  override fun setPageCoordinates(pageCoordinates: PageCoordinates) {
    ayahTrackerPresenter.setPageBounds(pageCoordinates)
  }

  override fun setAyahCoordinatesError() {
    ayahCoordinatesError = true
  }

  override fun setAyahCoordinatesData(coordinates: AyahCoordinates) {
    ayahTrackerPresenter.setAyahCoordinates(coordinates)
  }

  override fun handleTouchEvent(
    event: MotionEvent,
    eventType: AyahSelectedListener.EventType,
    page: Int
  ): Boolean {
    return isVisible && ayahTrackerPresenter.handleTouchEvent(
      requireActivity(), event, eventType,
      page, ayahCoordinatesError
    )
  }

  override fun handleLongPress(suraAyah: SuraAyah) {
    if (isVisible) {
      val page = quranInfo.getPageFromSuraAyah(suraAyah.sura, suraAyah.ayah)
      if (page != lastLongPressPage) {
        ayahTrackerPresenter.endAyahMode()
      }
      lastLongPressPage = page
      ayahTrackerPresenter.onLongPress(suraAyah)
    }
  }

  override fun handleRetryClicked() {
    hidePageDownloadError()
    quranPagePresenter.get().downloadImages()
  }

  override fun onScrollChanged(y: Float) {
    if (isVisible) {
      val views = arrayOf(rightTranslation, leftTranslation)
      for (view in views) {
        if (view != null) {
          val ayahSelection = readingEventPresenter.currentAyahSelection()
          if (ayahSelection is AyahSelection.Ayah) {
            val (suraAyah) = ayahSelection
            readingEventPresenter.onAyahSelection(
              AyahSelection.Ayah(
                suraAyah,
                view.getToolbarPosition(suraAyah.sura, suraAyah.ayah)
              )
            )
          }
        }
      }
    }
  }

  override fun endAyahMode() {
    if (isVisible) {
      ayahTrackerPresenter.endAyahMode()
    }
  }

  companion object {
    private const val FIRST_PAGE_EXTRA = "pageNumber"
    private const val MODE_EXTRA = "mode"
    private const val IS_SPLIT_SCREEN = "splitScreenMode"
    private const val SI_RIGHT_TRANSLATION_SCROLL_POSITION =
      "SI_RIGHT_TRANSLATION_SCROLL_POSITION"

    fun newInstance(firstPage: Int, mode: Int, isSplitScreen: Boolean): TabletFragment {
      val f = TabletFragment()
      val args = Bundle()
      args.putInt(FIRST_PAGE_EXTRA, firstPage)
      args.putInt(MODE_EXTRA, mode)
      args.putBoolean(IS_SPLIT_SCREEN, isSplitScreen)
      f.setArguments(args)
      return f
    }
  }
}
