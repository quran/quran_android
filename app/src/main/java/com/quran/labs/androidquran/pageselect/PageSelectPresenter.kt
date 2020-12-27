package com.quran.labs.androidquran.pageselect

import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.util.ImageUtil
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.UrlUtil
import dagger.Reusable
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@Reusable
class PageSelectPresenter @Inject
    constructor(private val imageUtil: ImageUtil,
                private val quranFileUtils: QuranFileUtils,
                private val mainThreadScheduler: Scheduler,
                private val urlUtil: UrlUtil,
                private val pageTypes:
                Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>) :
    Presenter<PageSelectActivity> {
  private val baseUrl = "https://android.quran.com/data/pagetypes/snips"
  private val compositeDisposable = CompositeDisposable()
  private val downloadingSet = mutableSetOf<String>()
  private var currentView: PageSelectActivity? = null

  private fun generateData() {
    val base = quranFileUtils.quranBaseDirectory
    if (base != null) {
      val outputPath = File(File(base, "pagetypes"), "snips")
      if (!outputPath.exists()) {
        outputPath.mkdirs()
        File(outputPath, ".nomedia").createNewFile()
      }

      val data = pageTypes.map {
        val provider = it.value
        val previewImage = File(outputPath, "${it.key}.png")
        val downloadedImage = if (previewImage.exists()) {
          previewImage
        } else if (!downloadingSet.contains(it.key)){
          downloadingSet.add(it.key)
          val url = "$baseUrl/${it.key}.png"
          compositeDisposable.add(
              imageUtil.downloadImage(url, previewImage)
                  .onErrorResumeNext(
                      imageUtil.downloadImage(urlUtil.fallbackUrl(url), previewImage))
                  .subscribeOn(Schedulers.io())
                  .observeOn(mainThreadScheduler)
                  .subscribe({ generateData() }, { e -> Timber.e(e) })
          )
          null
        } else {
          // already downloading
          null
        }
        PageTypeItem(it.key,
            downloadedImage,
            provider.getPreviewTitle(),
            provider.getPreviewDescription())
      }
      currentView?.onUpdatedData(data)
    }
  }

  override fun bind(what: PageSelectActivity) {
    currentView = what
    generateData()
  }

  override fun unbind(what: PageSelectActivity?) {
    if (currentView === what) {
      currentView = null
      compositeDisposable.clear()
      downloadingSet.clear()
    }
  }
}
