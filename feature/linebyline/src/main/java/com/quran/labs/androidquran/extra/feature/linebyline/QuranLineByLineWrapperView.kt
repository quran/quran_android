package com.quran.labs.androidquran.extra.feature.linebyline

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ScrollView
import com.quran.data.page.provider.di.inject
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.common.drawing.R
import com.quran.labs.androidquran.common.ui.core.BitmapPoolTrimRegistry
import com.quran.labs.androidquran.common.ui.core.PooledPageView
import com.quran.labs.androidquran.extra.feature.linebyline.model.MissingLineByLineImagesException
import com.quran.labs.androidquran.extra.feature.linebyline.presenter.QuranLineByLinePresenter
import com.quran.labs.androidquran.extra.feature.linebyline.resource.ImageBitmapUtil
import com.quran.labs.androidquran.extra.feature.linebyline.ui.QuranLineByLineCanvasView
import com.quran.labs.androidquran.extra.feature.linebyline.ui.shouldAnimateScrollTo
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale

private val SHARED_AYAH_NUMBER_FORMATTER: NumberFormat by lazy {
  NumberFormat.getIntegerInstance(Locale.forLanguageTag("ar-EG"))
}

class QuranLineByLineWrapperView(
  context: Context,
  private var currentPage: Int,
  private val dualScreenMode: Boolean = false
) : FrameLayout(context), PooledPageView {
  @Volatile
  private var didRequestFallback = false
  private var isCleanedUp = false
  private var trimmedWhileAttached = false
  private var viewScope: CoroutineScope? = null
  private var pageLoadJob: Job? = null
  private var cachedSuraHeaderBitmap: Bitmap? = null

  private val trimCleaner: () -> Unit = {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      releaseTransientBitmapsForStop()
    } else {
      post { releaseTransientBitmapsForStop() }
    }
  }

  private val restoreCallback: () -> Unit = {
    if (isAttachedToWindow && !isCleanedUp && trimmedWhileAttached) {
      trimmedWhileAttached = false
      startLoading()
    }
  }

  @Inject
  lateinit var imageBitmapUtil: ImageBitmapUtil
  @Inject
  lateinit var quranLineByLinePresenter: QuranLineByLinePresenter
  @Inject
  lateinit var pageProvider: PageProvider

  private val isScrollable: Boolean
  private val scrollView: ScrollView?
  private val canvasView: QuranLineByLineCanvasView

  init {
    inject(currentPage)
    val orientation = resources.configuration.orientation
    isScrollable = !dualScreenMode && orientation == Configuration.ORIENTATION_LANDSCAPE

    // Sura headers are composited directly into PageInfo.compositedPageBitmap on Dispatchers.IO.
    cachedSuraHeaderBitmap = null
    canvasView = QuranLineByLineCanvasView(
      context = context,
      pageNumber = currentPage,
      pageContentType = pageProvider.getPageContentType(),
      isScrollable = isScrollable,
      ayahNumberFormatter = SHARED_AYAH_NUMBER_FORMATTER,
      onClick = quranLineByLinePresenter::onClick,
      onPagePositioned = quranLineByLinePresenter::onPagePositioned,
      onSelectionStart = { x, y ->
        viewScope?.launch { quranLineByLinePresenter.startSelection(x, y) }
      },
      onSelectionModified = { offsetX, offsetY ->
        viewScope?.launch { quranLineByLinePresenter.modifySelectionRange(offsetX, offsetY) }
      },
      onSelectionEnd = {
        viewScope?.launch { quranLineByLinePresenter.endSelection() }
      }
    )

    if (isScrollable) {
      val sv = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        setOnScrollChangeListener { _, _, _, _, _ ->
          canvasView.reportPagePositionIfNeeded()
        }
        addView(
          canvasView,
          LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
      }
      scrollView = sv
      addView(sv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    } else {
      scrollView = null
      addView(canvasView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    isCleanedUp = false
    startLoading()
  }

  override fun onDetachedFromWindow() {
    stopAndCleanup()
    super.onDetachedFromWindow()
  }

  override fun cleanup() {
    stopAndCleanup()
  }

  override fun rebindPage(page: Int) {
    if (currentPage == page && !isCleanedUp && pageLoadJob?.isActive == true) {
      return
    }
    stopAndCleanup()
    currentPage = page
    didRequestFallback = false
    isCleanedUp = false
    trimmedWhileAttached = false
    cachedSuraHeaderBitmap = null
    scrollView?.scrollTo(0, 0)
    canvasView.rebindPage(page)
    quranLineByLinePresenter.rebindPage(page)
    if (isAttachedToWindow) {
      startLoading()
    }
  }

  private fun startLoading() {
    if (pageLoadJob?.isActive == true) return
    trimmedWhileAttached = false
    BitmapPoolTrimRegistry.register(trimCleaner)
    BitmapPoolTrimRegistry.registerRestorer(restoreCallback)
    val scope = viewScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main).also { viewScope = it }
    quranLineByLinePresenter.attach()

    canvasView.setPageInfo(quranLineByLinePresenter.emptyState(), cachedSuraHeaderBitmap)

    pageLoadJob = scope.launch {
      quranLineByLinePresenter.loadPage()
        .catch { throwable ->
          if (throwable is MissingLineByLineImagesException) {
            if (!didRequestFallback) {
              Timber.e(throwable, "Missing line image")
              withContext(Dispatchers.Main) {
                if (quranLineByLinePresenter.fallbackToImageType()) {
                  didRequestFallback = true
                  val activity = resolveActivity(context)
                  finishAndRestart(activity)
                }
              }
            }
            emit(quranLineByLinePresenter.emptyState())
          } else {
            throw throwable
          }
        }
        .collect { pageInfo ->
          if (isCleanedUp) return@collect
          canvasView.setPageInfo(pageInfo, cachedSuraHeaderBitmap)
          val sv = scrollView
          if (sv != null && shouldAnimateScrollTo(pageInfo.targetScrollPosition, sv.scrollY)) {
            sv.smoothScrollTo(0, pageInfo.targetScrollPosition)
          }
        }
    }
  }

  private fun releaseTransientBitmapsForStop() {
    if (isCleanedUp) return
    trimmedWhileAttached = true
    pageLoadJob?.cancel()
    pageLoadJob = null
    canvasView.clearCompositedBitmap()
    quranLineByLinePresenter.releaseCompositedBitmaps()
  }

  private fun stopAndCleanup() {
    isCleanedUp = true
    trimmedWhileAttached = false
    BitmapPoolTrimRegistry.unregister(trimCleaner)
    BitmapPoolTrimRegistry.unregisterRestorer(restoreCallback)
    pageLoadJob?.cancel()
    pageLoadJob = null
    viewScope?.cancel()
    viewScope = null
    canvasView.clearPageInfo()
    quranLineByLinePresenter.detach()
  }

  private tailrec fun resolveActivity(context: Context): Activity? {
    return context as? Activity
        ?: if (context is ContextWrapper) {
          resolveActivity(context.baseContext)
        } else {
          null
        }
  }

  private fun finishAndRestart(activity: Activity?) {
    if (activity != null) {
      val restartIntent = Intent(activity.intent)
      activity.finish()
      activity.startActivity(restartIntent)
    }
  }
}
