package com.quran.labs.androidquran.ui.helpers

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.window.layout.WindowMetricsCalculator
import com.quran.data.di.ActivityScope
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.di.qualifier.ActivityContext
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import timber.log.Timber

@ActivityScope
class QuranPageLoader @Inject internal constructor(
  @ActivityContext private val activityContext: Context,
  @ApplicationContext private val appContext: Context,
  private val okHttpClient: OkHttpClient,
  private val imageWidth: String,
  private val quranScreenInfo: QuranScreenInfo,
  private val quranFileUtils: QuranFileUtils
) : QuranPageLoaderInterface {

  private fun loadImage(pageNumber: Int, maxTargetWidth: Int): Response {
    var response: Response? = null
    var oom: OutOfMemoryError? = null
    try {
      response = QuranDisplayHelper.getQuranPage(
          okHttpClient, appContext, imageWidth, pageNumber, quranFileUtils, maxTargetWidth
      )
    } catch (me: OutOfMemoryError) {
      Timber.w("out of memory exception loading page $pageNumber, $imageWidth")
      oom = me
    }

    if (response == null ||
        response.bitmap == null &&
        response.errorCode != Response.ERROR_SD_CARD_NOT_FOUND
    ) {
      if (quranScreenInfo.isDualPageMode) {
        Timber.w("tablet got bitmap null, trying alternate width...")

        val param = quranScreenInfo.widthParam.let { widthParam ->
          if (widthParam == imageWidth) {
            quranScreenInfo.tabletWidthParam
          } else {
            widthParam
          }
        }

        response = QuranDisplayHelper.getQuranPage(
            okHttpClient, appContext, param, pageNumber, quranFileUtils, maxTargetWidth
        )

        if (response.bitmap == null) {
          Timber.w("bitmap still null, giving up... [%d]", response.errorCode)
        }
      }
      Timber.w("got response back as null... [%d]", response?.errorCode)
    }

    if ((response == null || response.bitmap == null) && oom != null) {
      throw oom
    }
    response!!.setPageData(pageNumber)
    return response
  }

  override fun loadPages(pages: Array<Int>): Observable<Response> {
    val isDualPageLayout = (activityContext as? PagerActivity)?.isDualPageLayout()
      ?: (pages.size > 1 || QuranUtils.isDualPages(activityContext, quranScreenInfo, false))
    val targetPageWidth = resolveTargetPageWidth(activityContext, isDualPageLayout)
    return Observable.fromArray(*pages)
        .flatMap { page: Int ->
          Observable.fromCallable { loadImage(page, targetPageWidth) }
              .subscribeOn(Schedulers.io())
        }
  }

  companion object {
    @VisibleForTesting
    internal fun resolveWindowWidthPx(context: Context): Int {
      if (context is Activity) {
        val metricsWidth = try {
          WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(context)
            .bounds
            .width()
        } catch (_: RuntimeException) {
          0
        }
        if (metricsWidth > 0) {
          return metricsWidth
        }
        val decorWidth = context.window?.decorView?.width ?: 0
        if (decorWidth > 0) {
          return decorWidth
        }
      }
      return context.resources.displayMetrics.widthPixels
    }

    @VisibleForTesting
    internal fun resolveTargetPageWidth(context: Context, isDualPageLayout: Boolean): Int {
      val windowWidth = resolveWindowWidthPx(context)
      if (windowWidth <= 0) {
        return 0
      }
      return if (isDualPageLayout) {
        (windowWidth + 1) / 2
      } else {
        windowWidth
      }
    }
  }
}
