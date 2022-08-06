package com.quran.labs.androidquran.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.core.worker.WorkerTaskFactory
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranPartialPageChecker
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

class PartialPageCheckingWorker(private val context: Context,
                                private val params: WorkerParameters,
                                private val quranInfo: QuranInfo,
                                private val quranFileUtils: QuranFileUtils,
                                private val quranScreenInfo: QuranScreenInfo,
                                private val quranSettings: QuranSettings,
                                private val quranPartialPageChecker: QuranPartialPageChecker
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = coroutineScope {
    Timber.d("PartialPageCheckingWorker")
    val requestedPageType = params.inputData.getString(WorkerConstants.PAGE_TYPE)
    if (requestedPageType != quranSettings.pageType) {
      Timber.e(IllegalStateException(
          "PageType different than expected: $requestedPageType, found ${quranSettings.pageType}"))
      Result.success()
    } else if (!quranSettings.didCheckPartialImages(requestedPageType)) {
      val numberOfPages = quranInfo.numberOfPages

      // prepare page widths and paths
      val width = quranScreenInfo.widthParam
      val pagesDirectory = quranFileUtils.getQuranImagesDirectory(context, width)!!
      val tabletWidth = quranScreenInfo.tabletWidthParam
      val tabletPagesDirectory = quranFileUtils.getQuranImagesDirectory(context, tabletWidth)!!

      // compute the partial page sets
      val partialPages =
        quranPartialPageChecker.checkPages(pagesDirectory, numberOfPages, width)
          .map { PartialPage(width, it) }
      Timber.d("Found %d partial images for width %s", partialPages.size, width)

      val tabletPartialPages = if (width == tabletWidth) {
        emptyList()
      } else {
        quranPartialPageChecker.checkPages(tabletPagesDirectory, numberOfPages, tabletWidth)
            .map { PartialPage(tabletWidth, it) }
      }
      Timber.d("Found %d partial images for tablet width %s", tabletPartialPages.size, tabletWidth)

      val allPartialPages = partialPages + tabletPartialPages
      if (allPartialPages.size > PARTIAL_PAGE_LIMIT) {
        Timber.e(IllegalStateException("Too many partial pages found"),
            "found ${allPartialPages.size} partial images")
        // still delete the partial images just because ¯\_(ツ)_/¯
      }

      val deletionSucceeded = try {
        // iterate through each one and delete the partial pages
        allPartialPages.firstOrNull {
          val path = if (it.width == width) pagesDirectory else tabletPagesDirectory
          !deletePage(path, it.page)
        } == null
      } catch (ioException: IOException) {
        Timber.e(ioException)
        false
      }

      if (!deletionSucceeded) {
        Timber.d("PartialPageCheckingWorker - partial deletion failure, retrying..")
        Result.retry()
      } else {
        Timber.d("PartialPageCheckingWorker - partial success!")
        quranSettings.setCheckedPartialImages(requestedPageType)
        Result.success()
      }
    } else {
      Result.success()
    }
  }

  data class PartialPage(val width: String, val page: Int)

  private fun deletePage(directory: String, page: Int): Boolean {
    val pageName = QuranFileUtils.getPageFileName(page)
    return File(directory, pageName).let { file ->
      if (file.exists()) {
        file.delete()
      } else {
        true
      }
    }
  }

  class Factory @Inject constructor(
    private val quranInfo: QuranInfo,
    private val quranFileUtils: QuranFileUtils,
    private val quranScreenInfo: QuranScreenInfo,
    private val quranSettings: QuranSettings,
    private val quranPartialPageChecker: QuranPartialPageChecker
  ) : WorkerTaskFactory {
    override fun makeWorker(
      appContext: Context,
      workerParameters: WorkerParameters
    ): ListenableWorker {
      return PartialPageCheckingWorker(
          appContext, workerParameters, quranInfo, quranFileUtils, quranScreenInfo, quranSettings,
          quranPartialPageChecker
      )
    }
  }

  companion object {
    private const val PARTIAL_PAGE_LIMIT = 50
  }
}
