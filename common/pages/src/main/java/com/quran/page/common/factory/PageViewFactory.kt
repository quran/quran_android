package com.quran.page.common.factory

import androidx.fragment.app.Fragment
import com.quran.page.common.data.PageMode

interface PageViewFactory {

  /**
   * Allow for a custom [PageViewFactory] to override how page views are
   * generated. If this returns null, the default behavior will be used
   * to provide the page.
   *
   * @param pageNumber the current page to get
   * @param pageMode the type of page to make
   */
  fun providePage(pageNumber: Int, pageMode: PageMode): Fragment?
}
