package com.quran.page.common.data

sealed class PageMode {
  object SingleArabicPage : PageMode()
  object SingleTranslationPage : PageMode()

  sealed class DualScreenMode : PageMode() {
    object Arabic : DualScreenMode()
    object Translation : DualScreenMode()
    object Mix : DualScreenMode()
  }
}
