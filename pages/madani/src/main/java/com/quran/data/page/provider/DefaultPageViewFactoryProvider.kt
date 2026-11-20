package com.quran.data.page.provider

import com.quran.data.page.provider.linebyline.LineByLinePageViewFactory
import com.quran.page.common.factory.PageViewFactory
import com.quran.page.common.factory.PageViewFactoryProvider

class DefaultPageViewFactoryProvider : PageViewFactoryProvider {

  override fun providePageViewFactory(pageType: String): PageViewFactory? {
    return if (pageType.endsWith("lines")) {
      LineByLinePageViewFactory()
    } else {
      null
    }
  }
}
