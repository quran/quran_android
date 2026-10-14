package com.quran.labs.androidquran.model.quran

import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import io.reactivex.rxjava3.core.Observable

interface CoordinatesModelInterface {
  fun getPageCoordinates(wantPageBounds: Boolean, vararg pages: Int): Observable<PageCoordinates>
  fun getAyahCoordinates(vararg pages: Int): Observable<AyahCoordinates>
}
