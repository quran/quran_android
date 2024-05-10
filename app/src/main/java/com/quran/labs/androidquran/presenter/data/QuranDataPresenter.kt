package com.quran.labs.androidquran.presenter.data

import android.content.Context
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy.KEEP
import androidx.work.NetworkType.CONNECTED
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.quran.common.upgrade.LocalDataUpgrade
import com.quran.data.core.QuranInfo
import com.quran.data.model.QuranDataStatus
import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.worker.AudioUpdateWorker
import com.quran.labs.androidquran.worker.MissingPageDownloadWorker
import com.quran.labs.androidquran.worker.PartialPageCheckingWorker
import com.quran.labs.androidquran.worker.WorkerConstants
import com.quran.mobile.di.qualifier.ApplicationContext
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject

class QuranDataPresenter @Inject internal constructor(
  @ApplicationContext val appContext: Context,
  val quranInfo: QuranInfo,
  val quranScreenInfo: QuranScreenInfo,
  private val quranPageProvider: PageProvider,
  val quranFileUtils: QuranFileUtils,
  private val localDataUpgrade: LocalDataUpgrade
) : Presenter<QuranDataActivity> {

  private var activity: QuranDataActivity? = null
  private var checkPagesDisposable: Disposable? = null
  private var cachedPageType: String? = null
  private var lastCachedResult: QuranDataStatus? = null

  private val quranSettings = QuranSettings.getInstance(appContext)

  @UiThread
  fun checkPages() {
    val lastCachedResult = lastCachedResult
    if (quranFileUtils.getQuranBaseDirectory(appContext) == null) {
      activity?.onStorageNotAvailable()
    } else if (lastCachedResult != null && cachedPageType == quranSettings.pageType) {
      activity?.onPagesChecked(lastCachedResult)
    } else if (checkPagesDisposable == null) {
      val pages = quranInfo.numberOfPages
      val pageType = quranSettings.pageType
      checkPagesDisposable =
          supportLegacyPages(pages)
              .andThen(actuallyCheckPages(pages))
              .flatMap { Single.fromCallable { localDataUpgrade.processData(it) } }
              .map { checkPatchStatus(it) }
              .flatMap { Single.fromCallable { localDataUpgrade.processPatch(it) } }
              .doOnSuccess {
                try {
                  val quranHiddenDirectoryMarkerFile = ".q4a"
                  File(appContext.noBackupFilesDir, quranHiddenDirectoryMarkerFile).delete()

                  val baseDirectory = quranFileUtils.quranBaseDirectory
                  File(baseDirectory, quranHiddenDirectoryMarkerFile).delete()
                  val quranDirectoryMarkerFile = "q4a"
                  File(baseDirectory, quranDirectoryMarkerFile).delete()
                } catch (e: Exception) {
                  Timber.e(e)
                }
              }
              .subscribeOn(Schedulers.io())
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe(Consumer {
                if (it.havePages() && it.patchParam == null) {
                  // only cache cases where no downloads are needed - otherwise, caching
                  // is risky since after coming back to the app, the downloads could be
                  // done, though the cached data suggests otherwise.
                  cachedPageType = pageType
                  this.lastCachedResult = it
                }
                activity?.onPagesChecked(it)
                checkPagesDisposable = null
              })
      scheduleAudioUpdater()
    }
  }

  private fun scheduleAudioUpdater() {
    val audioUpdaterTaskConstraints = Constraints.Builder()
        .setRequiredNetworkType(CONNECTED)
        .build()

    // setup audio update task
    val updateAudioTask = PeriodicWorkRequestBuilder<AudioUpdateWorker>(7, DAYS)
        .setConstraints(audioUpdaterTaskConstraints)
        .build()

    // run audio update task once a week
    WorkManager.getInstance(appContext)
        .enqueueUniquePeriodicWork(Constants.AUDIO_UPDATE_UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP, updateAudioTask)
  }

  fun imagesVersion() = quranPageProvider.getImageVersion()

  fun canProceedWithoutDownload() = quranPageProvider.getPageContentType() == PageContentType.Image

  fun fallbackToImageType() {
    val fallbackType = quranPageProvider.getFallbackPageType()
    if (fallbackType != null) {
      quranSettings.pageType = fallbackType
    }
  }

  private fun supportLegacyPages(totalPages: Int): Completable {
    return Completable.fromCallable {
      if (!quranSettings.haveDefaultImagesDirectory() && "madani" == quranSettings.pageType) {
        /* this code is only valid for the legacy madani pages.
         *
         * previously, we would send any screen widths greater than 1280
         * to get 1920 images. this was problematic for various reasons,
         * including:
         * a. a texture limit for the maximum size of a bitmap that could
         *    be loaded, which the 1920x3106 images exceeded on devices
         *    with the minimum 2048 height capacity.
         * b. slow to switch pages due to the massive size of the gl
         *    texture loaded by android.
         *
         * consequently, in this new version, we make anything above 1024
         * fallback to a 1260 bucket (height of 2038). this works around
         * both problems (much faster page flipping now too) with a very
         * minor loss in quality.
         *
         * this code checks and sees, if the user already has a complete
         * folder of 1920 images, in which case it sets that in the pref
         * so we load those instead of 1260s.
         */
        val fallback = quranFileUtils.getPotentialFallbackDirectory(totalPages)
        if (fallback != null) {
          Timber.d("setting fallback pages to %s", fallback)
          quranSettings.setDefaultImagesDirectory(fallback)
        } else {
          // stop doing this check every launch if the images don't exist.
          // since the method checks for the key being missing (and since
          // override parameter ignores the empty string), empty string is
          // fine here.
          quranSettings.setDefaultImagesDirectory("")
        }
      }

      val pageType = quranSettings.pageType
      if (!quranSettings.didCheckPartialImages(pageType) && !pageType.endsWith("lines")) {
        Timber.d("enqueuing work for $pageType...")

        // setup check pages task
        val checkTasksInputData = workDataOf(WorkerConstants.PAGE_TYPE to pageType)
        val checkPartialPagesTask = OneTimeWorkRequestBuilder<PartialPageCheckingWorker>()
            .setInputData(checkTasksInputData)
            .build()

        // setup missing page task
        val missingPageTaskConstraints = Constraints.Builder()
            .setRequiredNetworkType(CONNECTED)
            .build()
        val missingPageDownloadTask = OneTimeWorkRequestBuilder<MissingPageDownloadWorker>()
            .setConstraints(missingPageTaskConstraints)
            .build()

        // run check pages task
        WorkManager.getInstance(appContext)
            .beginUniqueWork("${WorkerConstants.CLEANUP_PREFIX}$pageType",
                KEEP,
                checkPartialPagesTask)
            .then(missingPageDownloadTask)
            .enqueue()
      }
    }
  }

  private fun actuallyCheckPages(totalPages: Int): Single<QuranDataStatus> {
    return Single.fromCallable {
      val width = quranScreenInfo.widthParam
      val havePortrait = quranFileUtils.haveAllImages(width, totalPages, true)

      val tabletWidth = quranScreenInfo.tabletWidthParam
      val needLandscapeImages = if (quranScreenInfo.isDualPageMode && width != tabletWidth) {
        val haveLandscape = quranFileUtils.haveAllImages(tabletWidth, totalPages, true)
        Timber.d("checkPages: have portrait images: %s, have landscape images: %s",
            if (havePortrait) "yes" else "no", if (haveLandscape) "yes" else "no")
        !haveLandscape
      } else {
        // either not dual screen mode or the widths are the same
        Timber.d("checkPages: have all images: %s", if (havePortrait) "yes" else "no")
        false
      }

      QuranDataStatus(width, tabletWidth, havePortrait, !needLandscapeImages, null, totalPages)
    }
  }

  @WorkerThread
  private fun checkPatchStatus(quranDataStatus: QuranDataStatus): QuranDataStatus {
    // only need patches if we have all the pages
    if (quranDataStatus.havePages()) {
      val latestImagesVersion = quranPageProvider.getImageVersion()

      val width = quranDataStatus.portraitWidth
      val needPortraitPatch =
          !quranFileUtils.isVersion(width, latestImagesVersion)

      val tabletWidth = quranDataStatus.landscapeWidth
      if (width != tabletWidth) {
        val needLandscapePatch =
            !quranFileUtils.isVersion(tabletWidth, latestImagesVersion)
        if (needLandscapePatch) {
          return quranDataStatus.copy(patchParam = width + tabletWidth)
        }
      }

      if (needPortraitPatch) {
        return quranDataStatus.copy(patchParam = width)
      }
    }
    return quranDataStatus
  }

  override fun bind(activity: QuranDataActivity) {
    this.activity = activity
  }

  override fun unbind(activity: QuranDataActivity) {
    if (this.activity === activity) {
      this.activity = null
    }
  }
}
