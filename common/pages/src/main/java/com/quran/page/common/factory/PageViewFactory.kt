package com.quran.page.common.factory

import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import com.quran.page.common.data.PageMode

/**
 * Allow for a custom [PageViewFactory] to override how page views are
 * generated. If this returns null, the default behavior will be used
 * to provide the page.
 */
interface PageViewFactory {

  /**
   * Allow for a custom [PageViewFactory] to create a [Fragment] for
   * a page view. If this returns null, the default behavior will be
   * used to provide the page fragment.
   *
   * @param pageNumber the current page to get
   * @param pageMode the type of page to make
   */
  fun providePage(pageNumber: Int, pageMode: PageMode): Fragment?

  /**
   * Allow for a custom [PageViewFactory] to override the actual [View]
   * generated. If this returns null, the default behavior will be used
   * to provide the page.
   *
   * @param context the current context
   * @param pageNumber the current page to get
   * @param pageMode the type of page to make
   */
  fun providePageView(context: Context, pageNumber: Int, pageMode: PageMode): View?
}
