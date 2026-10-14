package com.quran.labs.androidquran.ui.helpers

import com.quran.labs.androidquran.common.Response
import io.reactivex.rxjava3.core.Observable

interface QuranPageLoaderInterface {
  fun loadPages(pages: Array<Int>): Observable<Response>
}
