package com.quran.data.page.provider.linebyline

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import com.quran.labs.androidquran.extra.feature.linebyline.QuranLineByLineFragment
import com.quran.labs.androidquran.extra.feature.linebyline.QuranLineByLineWrapperView
import com.quran.page.common.data.PageMode
import com.quran.page.common.factory.PageViewFactory

class LineByLinePageViewFactory : PageViewFactory {

  override fun providePage(pageNumber: Int, pageMode: PageMode): Fragment? {
    return if (pageMode == PageMode.SingleArabicPage) {
      QuranLineByLineFragment.newInstance(pageNumber)
    } else {
      null
    }
  }

  override fun providePageView(context: Context, pageNumber: Int, pageMode: PageMode): View? {
    return if (pageMode == PageMode.DualScreenMode.Arabic) {
      QuranLineByLineWrapperView(context, pageNumber, true)
    } else {
      null
    }
  }
}
