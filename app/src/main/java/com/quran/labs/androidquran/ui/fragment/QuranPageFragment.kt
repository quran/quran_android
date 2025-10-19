package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.data.model.selection.AyahSelection
import com.quran.data.model.selection.selectionIndicator
import com.quran.data.model.selection.withSelectionIndicator
import com.quran.data.model.selection.withYScroll
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.presenter.quran.QuranPagePresenter
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahImageTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahScrollableImageTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerItem
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.AyahInteractionHandler
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.labs.androidquran.ui.helpers.QuranPage
import com.quran.labs.androidquran.ui.util.PageController
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.view.HighlightingImageView
import com.quran.labs.androidquran.view.QuranImagePageLayout
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.reading.common.ReadingEventPresenter
import dev.zacsweers.metro.Inject
import timber.log.Timber

class QuranPageFragment : Fragment(), PageController, QuranPage, QuranPageScreen,
  AyahInteractionHandler {
  private var pageNumber = 0
  private var ayahTrackerItems: Array<AyahTrackerItem>? = null

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranDisplayData: QuranDisplayData

  @Inject
  lateinit var quranSettings: QuranSettings

  @Inject
  lateinit var quranPagePresenter: QuranPagePresenter

  @Inject
  lateinit var ayahTrackerPresenter: AyahTrackerPresenter

  @Inject
  lateinit var quranScreenInfo: QuranScreenInfo

  @Inject
  lateinit var imageDrawHelpers: Set<@JvmSuppressWildcards ImageDrawHelper>

  @Inject
  lateinit var readingEventPresenter: ReadingEventPresenter

  private var imageView: HighlightingImageView? = null
  private var quranPageLayout: QuranImagePageLayout? = null
  private var ayahCoordinatesError = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
  }

  override fun onResume() {
    super.onResume()
    updateView()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val context = requireContext()
    val quranPageLayout = QuranImagePageLayout(context)
    quranPageLayout.setPageController(this, pageNumber, quranInfo.skip)
    this.quranPageLayout = quranPageLayout
    imageView = quranPageLayout.getImageView()
    return quranPageLayout
  }

  override fun updateView() {
    val quranPageLayout = quranPageLayout
    if (isAdded && quranPageLayout != null) {
      quranPageLayout.updateView(quranSettings)
      if (!quranSettings.highlightBookmarks()) {
        imageView?.unHighlight(HighlightTypes.BOOKMARK)
      }
      quranPagePresenter.refresh()
    }
  }

  override fun getAyahTracker(): AyahTracker {
    return ayahTrackerPresenter
  }

  override fun getAyahTrackerItems(): Array<AyahTrackerItem> {
    val trackerItems = ayahTrackerItems
    return if (trackerItems == null) {
      val imageView = imageView
      val quranPageLayout = quranPageLayout
      val height = quranScreenInfo.getHeight()
      if (imageView != null && quranPageLayout != null) {
        arrayOf(
          if (quranPageLayout.canScroll()) {
            AyahScrollableImageTrackerItem(
              pageNumber,
              height,
              quranInfo,
              quranDisplayData,
              quranPageLayout,
              imageDrawHelpers,
              imageView
            )
          } else {
            AyahImageTrackerItem(
              pageNumber,
              quranInfo,
              quranDisplayData,
              false,
              imageDrawHelpers,
              imageView
            )
          }
        )
      } else {
        emptyArray()
      }
    } else {
      trackerItems
    }
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)

    pageNumber = arguments?.getInt(PAGE_NUMBER_EXTRA) ?: 1
    val pages = intArrayOf(pageNumber)
    (activity as PagerActivity).pagerActivityComponent
      .quranPageComponentFactory()
      .generate(pages)
      .inject(this)
  }

  override fun onStart() {
    super.onStart()
    quranPagePresenter.bind(this)
    ayahTrackerPresenter.bind(this)
  }

  override fun onStop() {
    quranPagePresenter.unbind(this)
    ayahTrackerPresenter.unbind(this)
    super.onStop()
  }

  fun cleanup() {
    Timber.d("cleaning up page %d", pageNumber)
    if (quranPageLayout != null) {
      imageView!!.setImageDrawable(null)
      quranPageLayout = null
    }
  }

  override fun setPageCoordinates(pageCoordinates: PageCoordinates) {
    ayahTrackerPresenter.setPageBounds(pageCoordinates)
  }

  override fun setAyahCoordinatesData(ayahCoordinates: AyahCoordinates) {
    ayahTrackerPresenter.setAyahCoordinates(ayahCoordinates)
    ayahCoordinatesError = false
  }

  override fun setAyahCoordinatesError() {
    ayahCoordinatesError = true
  }

  override fun onScrollChanged(y: Float) {
    val selection = readingEventPresenter.currentAyahSelection()
    if (selection !is AyahSelection.None) {
      val selectionIndicator = selection.selectionIndicator()
      val updatedIndicator =
        selectionIndicator.withYScroll(-y)
      val updatedSelection =
        selection.withSelectionIndicator(updatedIndicator)
      readingEventPresenter.onAyahSelection(updatedSelection)
    }
  }

  override fun setPageDownloadError(@StringRes errorMessage: Int) {
    quranPageLayout?.let {
      it.showError(errorMessage)
      it.setOnClickListener { _: View? -> ayahTrackerPresenter.onPressIgnoringSelectionState() }
    }
  }

  override fun setPageBitmap(page: Int, pageBitmap: Bitmap) {
    imageView?.setImageDrawable(pageBitmap.toDrawable(resources))
  }

  override fun hidePageDownloadError() {
    quranPageLayout?.let {
      it.hideError()
      it.setOnClickListener(null)
      it.isClickable = false
    }
  }

  override fun handleRetryClicked() {
    hidePageDownloadError()
    quranPagePresenter.downloadImages()
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
      ayahTrackerPresenter.onLongPress(suraAyah)
    }
  }

  override fun endAyahMode() {
    if (isVisible) {
      ayahTrackerPresenter.endAyahMode()
    }
  }

  companion object {
    private const val PAGE_NUMBER_EXTRA = "pageNumber"

    fun newInstance(page: Int): QuranPageFragment {
      val f = QuranPageFragment()
      val args = Bundle()
      args.putInt(PAGE_NUMBER_EXTRA, page)
      f.setArguments(args)
      return f
    }
  }
}
