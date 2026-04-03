package com.quran.labs.androidquran.fakes

import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog

class FakeTagBookmarkDialog : TagBookmarkDialog() {
  var showAddTagDialogCallCount: Int = 0

  override fun showAddTagDialog() {
    showAddTagDialogCallCount++
  }
}
