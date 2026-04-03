package com.quran.labs.androidquran.fakes

import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.ui.helpers.QuranPageLoaderInterface
import io.reactivex.rxjava3.core.Observable

class FakeQuranPageLoader : QuranPageLoaderInterface {
  var loadPagesResult: Observable<Response> = Observable.empty()
  var lastLoadedPages: Array<Int>? = null

  override fun loadPages(pages: Array<Int>): Observable<Response> {
    lastLoadedPages = pages
    return loadPagesResult
  }
}
