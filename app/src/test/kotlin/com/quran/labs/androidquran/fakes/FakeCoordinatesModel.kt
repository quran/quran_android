package com.quran.labs.androidquran.fakes

import com.quran.labs.androidquran.model.quran.CoordinatesModelInterface
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import io.reactivex.rxjava3.core.Observable

data class PageCoordinatesCall(val wantPageBounds: Boolean, val pages: List<Int>)

class FakeCoordinatesModel : CoordinatesModelInterface {
  var pageCoordinatesResult: Observable<PageCoordinates> = Observable.empty()
  val pageCoordinatesCalls: MutableList<PageCoordinatesCall> = mutableListOf()
  val ayahCoordinatesResponses: MutableMap<Int, Observable<AyahCoordinates>> = mutableMapOf()
  val getAyahCoordinatesCalledWith: MutableList<Int> = mutableListOf()

  /** Most recent wantPageBounds arg — convenience accessor for single-call tests. */
  val lastWantPageBoundsArg: Boolean? get() = pageCoordinatesCalls.lastOrNull()?.wantPageBounds

  override fun getPageCoordinates(
    wantPageBounds: Boolean,
    vararg pages: Int
  ): Observable<PageCoordinates> {
    pageCoordinatesCalls.add(PageCoordinatesCall(wantPageBounds, pages.toList()))
    return pageCoordinatesResult
  }

  override fun getAyahCoordinates(vararg pages: Int): Observable<AyahCoordinates> {
    pages.forEach { getAyahCoordinatesCalledWith.add(it) }
    return ayahCoordinatesResponses[pages.firstOrNull()] ?: Observable.empty()
  }
}
