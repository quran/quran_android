package com.quran.labs.androidquran.pages.linebyline

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import com.quran.data.di.AppScope
import com.quran.data.source.PageContentType
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.extra.feature.linebyline.QuranLineByLineFragment
import com.quran.labs.androidquran.extra.feature.linebyline.QuranLineByLineWrapperView
import com.quran.page.common.data.PageMode
import com.quran.page.common.factory.PageViewFactory
import com.quran.page.common.factory.PageViewFactoryProvider
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/**
 * Shows the page types drawn line by line (PageContentType.Line) with feature:linebyline, in the
 * open source build (app/pluggable.gradle). quran.com's own build wires its page views in extras.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object LineByLinePagesModule {

  @Provides
  fun providePageViewFactoryProvider(pageProviders: Map<String, PageProvider>): PageViewFactoryProvider =
    PageViewFactoryProvider { pageType ->
      LineByLinePageViewFactory.takeIf { pageProviders[pageType]?.getPageContentType() is PageContentType.Line }
    }
}

internal object LineByLinePageViewFactory : PageViewFactory {

  // a single Arabic page is a fragment of its own; every other mode keeps its usual fragment, which
  // asks for the Arabic page's view below
  override fun providePage(pageNumber: Int, pageMode: PageMode): Fragment? =
    if (pageMode is PageMode.SingleArabicPage) QuranLineByLineFragment.newInstance(pageNumber) else null

  override fun providePageView(context: Context, pageNumber: Int, pageMode: PageMode): View =
    QuranLineByLineWrapperView(context, pageNumber, dualScreenMode = true)
}
