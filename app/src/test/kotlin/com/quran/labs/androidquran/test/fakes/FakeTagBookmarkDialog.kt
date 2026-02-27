package com.quran.labs.androidquran.test.fakes

import com.quran.data.model.bookmark.Tag

/**
 * Fake implementation of TagBookmarkDialog for testing.
 *
 * Pattern: Call tracking + state
 *
 * Usage:
 * ```
 * val fakeDialog = FakeTagBookmarkDialog()
 * presenter.bind(fakeDialog)
 * presenter.toggleTag(-1)
 *
 * // Assert dialog was shown
 * fakeDialog.assertShowAddTagDialogCalled()
 * ```
 */
class FakeTagBookmarkDialog {

  private val showAddTagDialogCalls = mutableListOf<Unit>()
  private val setDataCalls = mutableListOf<SetDataCall>()

  data class SetDataCall(val tags: List<Tag>?, val checkedTags: HashSet<Long>)

  fun showAddTagDialog() {
    showAddTagDialogCalls.add(Unit)
  }

  fun setData(tags: List<Tag>?, checkedTags: HashSet<Long>) {
    setDataCalls.add(SetDataCall(tags, checkedTags))
  }

  // Assertion helpers
  fun assertShowAddTagDialogCalled() {
    require(showAddTagDialogCalls.isNotEmpty()) {
      "Expected showAddTagDialog() to be called but it wasn't"
    }
  }

  fun assertShowAddTagDialogCalledTimes(times: Int) {
    require(showAddTagDialogCalls.size == times) {
      "Expected showAddTagDialog() called $times times but was called ${showAddTagDialogCalls.size} times"
    }
  }

  fun assertSetDataCalled() {
    require(setDataCalls.isNotEmpty()) {
      "Expected setData() to be called but it wasn't"
    }
  }

  fun assertSetDataCalledWith(tags: List<Tag>?, checkedTags: HashSet<Long>) {
    require(setDataCalls.any { it.tags == tags && it.checkedTags == checkedTags }) {
      "Expected setData($tags, $checkedTags) but was called with: $setDataCalls"
    }
  }

  // Query helpers
  fun getLastSetDataCall(): SetDataCall? = setDataCalls.lastOrNull()

  fun getShowAddTagDialogCallCount(): Int = showAddTagDialogCalls.size

  fun getSetDataCallCount(): Int = setDataCalls.size

  fun wasShowAddTagDialogCalled(): Boolean = showAddTagDialogCalls.isNotEmpty()

  fun wasSetDataCalled(): Boolean = setDataCalls.isNotEmpty()

  // Reset for test isolation
  fun reset() {
    showAddTagDialogCalls.clear()
    setDataCalls.clear()
  }
}
