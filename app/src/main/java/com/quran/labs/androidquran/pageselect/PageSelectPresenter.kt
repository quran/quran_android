package com.quran.labs.androidquran.pageselect

import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.util.ImageUtil
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import dagger.Reusable
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import javax.inject.Inject

@Reusable
class PageSelectPresenter @Inject
    constructor(private val imageUtil: ImageUtil,
                private val quranFileUtils: QuranFileUtils,
                private val quranScreenInfo: QuranScreenInfo,
                private val mainThreadScheduler: Scheduler,
                private val pageTypes:
                Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>) :
    Presenter<PageSelectActivity> {
  private val compositeDisposable = CompositeDisposable()
  private var currentView: PageSelectActivity? = null

  private fun generateData() {
    val base = quranFileUtils.quranBaseDirectory
    if (base != null) {
      val outputPath = File(base, "pagetype")
      if (!outputPath.exists()) {
        outputPath.mkdirs()
      }

      val data = pageTypes.map {
        val provider = it.value
        val previewImage = File(outputPath, "${it.key}_604.png")
        val downloadedImage = if (previewImage.exists()) {
          previewImage
        } else {
          val url = provider.getImagesBaseUrl() + "width${quranScreenInfo.widthParam}/page604.png"
          compositeDisposable.add(
              imageUtil.downloadImage(url, previewImage)
                  .subscribeOn(Schedulers.io())
                  .observeOn(mainThreadScheduler)
                  .subscribe { generateData() }
          )
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
    }
  }
}
