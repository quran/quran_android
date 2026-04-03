package com.quran.labs.androidquran.fakes

import com.quran.labs.androidquran.model.quran.CoordinatesModelInterface
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import io.reactivex.rxjava3.core.Observable

class FakeCoordinatesModel : CoordinatesModelInterface {
  var pageCoordinatesResult: Observable<PageCoordinates> = Observable.empty()
  var lastWantPageBoundsArg: Boolean? = null
  val ayahCoordinatesResponses: MutableMap<Int, Observable<AyahCoordinates>> = mutableMapOf()
  val getAyahCoordinatesCalledWith: MutableList<Int> = mutableListOf()

  override fun getPageCoordinates(
    wantPageBounds: Boolean,
    vararg pages: Int
  ): Observable<PageCoordinates> {
    lastWantPageBoundsArg = wantPageBounds
    return pageCoordinatesResult
  }

  override fun getAyahCoordinates(vararg pages: Int): Observable<AyahCoordinates> {
    pages.forEach { getAyahCoordinatesCalledWith.add(it) }
    return ayahCoordinatesResponses[pages.firstOrNull()] ?: Observable.empty()
  }
}
