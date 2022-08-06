package com.quran.page.common.factory

fun interface PageViewFactoryProvider {
  fun providePageViewFactory(pageType: String): PageViewFactory?
}
