package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.di.ActivityScope
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject

@ActivityScope
class QuranPageLoader @Inject internal constructor(
  private val appContext: Context,
  private val okHttpClient: OkHttpClient,
  private val imageWidth: String,
  private val quranScreenInfo: QuranScreenInfo,
  private val quranFileUtils: QuranFileUtils
) {

  private fun loadImage(pageNumber: Int): Response? {
    var response: Response? = null
    var oom: OutOfMemoryError? = null
    try {
      response = QuranDisplayHelper.getQuranPage(
          okHttpClient, appContext, imageWidth, pageNumber, quranFileUtils
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
            okHttpClient, appContext, param, pageNumber, quranFileUtils
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

  fun loadPages(vararg pages: Int?): Observable<Response?> {
    return Observable.fromArray<Int>(*pages)
        .flatMap { page: Int ->
          Observable.fromCallable { loadImage(page) }
        }
        .subscribeOn(Schedulers.io())
  }
}
