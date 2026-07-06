package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.data.dao.BookmarkSortOrder
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.presenter.bookmark.BookmarkPresenter
import com.quran.labs.androidquran.presenter.bookmark.BookmarksContextualModePresenter
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.ui.helpers.BookmarkUIConverter
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter.QuranTouchListener
import com.quran.labs.androidquran.ui.helpers.QuranRow
import com.quran.mobile.bookmark.model.isDefaultBookmarkCollectionId
import com.quran.mobile.feature.sync.QuranSyncManager
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.launch

class BookmarksFragment : Fragment(), QuranTouchListener {
  private var bookmarksSwipeRefresh: SwipeRefreshLayout? = null
  private var recyclerView: RecyclerView? = null
  private var bookmarksAdapter: QuranListAdapter? = null

  @Inject
  lateinit var bookmarkPresenter: BookmarkPresenter

  @Inject
  lateinit var bookmarksContextualModePresenter: BookmarksContextualModePresenter

  @Inject
  lateinit var bookmarkUIConverter: BookmarkUIConverter

  @Inject
  lateinit var syncManager: QuranSyncManager

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.quran_bookmarks_list, container, false)

    val context = requireContext()
    val bookmarksSwipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.bookmarks_swipe_refresh)
    this.bookmarksSwipeRefresh = bookmarksSwipeRefresh
    bookmarksSwipeRefresh.setOnRefreshListener { onRefreshBookmarks() }
    updatePullToRefreshEnabled(syncManager.canTriggerSync)

    val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
    recyclerView.setLayoutManager(LinearLayoutManager(context))
    recyclerView.setItemAnimator(DefaultItemAnimator())
    this.recyclerView = recyclerView

    val bookmarksAdapter =
      QuranListAdapter(context, recyclerView, emptyArray(), true)
    bookmarksAdapter.setQuranTouchListener(this)
    this.bookmarksAdapter = bookmarksAdapter
    recyclerView.setAdapter(bookmarksAdapter)

    ViewCompat.setOnApplyWindowInsetsListener(
      recyclerView
    ) { v: View, insets: WindowInsetsCompat ->
      val innerPadding = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout()
      )
      // top, left, right are handled by QuranActivity
      v.setPadding(
        0,
        0,
        0,
        innerPadding.bottom
      )
      insets
    }
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        syncManager.canTriggerSyncFlow.collect { canTriggerSync ->
          updatePullToRefreshEnabled(canTriggerSync)
        }
      }
    }
    return view
  }

  override fun onStart() {
    super.onStart()
    bookmarkPresenter.bind(this)
    bookmarksContextualModePresenter.bind(this)
  }

  override fun onStop() {
    bookmarkPresenter.unbind(this)
    bookmarksContextualModePresenter.unbind(this)
    super.onStop()
  }

  override fun onDestroyView() {
    bookmarksSwipeRefresh?.isRefreshing = false
    bookmarksSwipeRefresh = null
    recyclerView = null
    bookmarksAdapter = null
    super.onDestroyView()
  }

  private fun onRefreshBookmarks() {
    if (syncManager.canTriggerSync) {
      syncManager.triggerSync()
    }
    bookmarksSwipeRefresh?.isRefreshing = false
  }

  private fun updatePullToRefreshEnabled(isEnabled: Boolean) {
    bookmarksSwipeRefresh?.isEnabled = isEnabled
    if (!isEnabled) {
      bookmarksSwipeRefresh?.isRefreshing = false
    }
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, inflater)
    val sortItem = menu.findItem(R.id.sort)
    if (sortItem != null) {
      sortItem.isVisible = true
      sortItem.isEnabled = true

      val bookmarkPresenter = bookmarkPresenter
      if (BookmarkSortOrder.SORT_DATE_ADDED == bookmarkPresenter.getSortOrder()) {
        val sortByDate = menu.findItem(R.id.sort_date)
        sortByDate.isChecked = true
      } else {
        val sortByLocation = menu.findItem(R.id.sort_location)
        sortByLocation.isChecked = true
      }

      val groupByTags = menu.findItem(R.id.group_by_tags)
      groupByTags.isVisible = true
      groupByTags.isEnabled = true
      groupByTags.isChecked = bookmarkPresenter.isGroupedByTags

      val showRecents = menu.findItem(R.id.show_recents)
      showRecents.isChecked = bookmarkPresenter.isShowingRecents

      val showDates = menu.findItem(R.id.show_date)
      showDates.isChecked = bookmarkPresenter.isDateShowing
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val itemId = item.itemId
    val bookmarkPresenter = bookmarkPresenter

    when (itemId) {
      R.id.sort_date -> {
        bookmarkPresenter.setSortOrder(BookmarkSortOrder.SORT_DATE_ADDED)
        item.isChecked = true
        return true
      }

      R.id.sort_location -> {
        bookmarkPresenter.setSortOrder(BookmarkSortOrder.SORT_LOCATION)
        item.isChecked = true
        return true
      }

      R.id.group_by_tags -> {
        bookmarkPresenter.toggleGroupByTags()
        item.isChecked = bookmarkPresenter.isGroupedByTags
        return true
      }

      R.id.show_recents -> {
        bookmarkPresenter.toggleShowRecents()
        item.isChecked = bookmarkPresenter.isShowingRecents
        return true
      }

      R.id.show_date -> {
        bookmarkPresenter.toggleShowDate()
        bookmarksAdapter?.setShowDate(bookmarkPresenter.isDateShowing)
        item.isChecked = bookmarkPresenter.isDateShowing
        return true
      }
    }

    return super.onOptionsItemSelected(item)
  }

  fun onNewRawData(rawItems: BookmarkRawResult) {
    val items = bookmarkUIConverter?.convertToUIResult(requireContext(), rawItems)

    val bookmarksAdapter = bookmarksAdapter
    val bookmarkPresenter = bookmarkPresenter
    if (bookmarksAdapter != null && items != null) {
      if (bookmarksContextualModePresenter.isInActionMode()) {
        bookmarksContextualModePresenter.finishActionMode()
      }
      bookmarksAdapter.setShowTags(bookmarkPresenter.shouldShowInlineTags())
      bookmarksAdapter.setShowDate(bookmarkPresenter.isDateShowing)
      bookmarksAdapter.setElements(
        items.rows.toTypedArray<QuranRow>(), items.tagMap
      )
      bookmarksAdapter.notifyDataSetChanged()
    }
  }

  override fun onClick(row: QuranRow, position: Int) {
    val bookmarksAdapter = bookmarksAdapter
    val bookmarksContextualModePresenter = bookmarksContextualModePresenter
    if (bookmarksAdapter != null) {
      if (bookmarksContextualModePresenter.isInActionMode()) {
        val checked = isValidSelection(row) && !bookmarksAdapter.isItemChecked(position)
        bookmarksAdapter.setItemChecked(position, checked)
        bookmarksContextualModePresenter.invalidateActionMode(false)
      } else {
        bookmarksAdapter.setItemChecked(position, false)
        handleRowClicked(activity, row)
      }
    }
  }

  override fun onLongClick(row: QuranRow, position: Int): Boolean {
    if (isValidSelection(row)) {
      val bookmarksAdapter = bookmarksAdapter
      val bookmarksContextualModePresenter = bookmarksContextualModePresenter
      if (bookmarksAdapter != null) {
        bookmarksAdapter.setItemChecked(position, !bookmarksAdapter.isItemChecked(position))
        if (bookmarksContextualModePresenter.isInActionMode() && bookmarksAdapter.getCheckedItems()
            .isEmpty()
        ) {
          bookmarksContextualModePresenter.finishActionMode()
        } else {
          bookmarksContextualModePresenter.invalidateActionMode(true)
        }
        return true
      }
    }
    return false
  }

  private fun isValidSelection(selected: QuranRow): Boolean {
    return selected.isBookmark || (selected.isBookmarkHeader && selected.userTagId() != null)
  }

  private val mOnUndoClickListener: View.OnClickListener = View.OnClickListener {
    bookmarkPresenter.cancelDeletion()
    bookmarkPresenter.requestData(true)
  }

  fun prepareContextualMenu(menu: Menu) {
    val bookmarksAdapter = bookmarksAdapter
    val bookmarkPresenter = bookmarkPresenter
    if (bookmarksAdapter != null) {
      val menuVisibility =
        bookmarkPresenter.getContextualOperationsForItems(bookmarksAdapter.getCheckedItems())
      menu.findItem(R.id.cab_edit_tag).isVisible = menuVisibility[0]
      menu.findItem(R.id.cab_delete).isVisible = menuVisibility[1]
      menu.findItem(R.id.cab_tag_bookmark).isVisible = menuVisibility[2]
    }
  }

  fun onContextualActionClicked(itemId: Int): Boolean {
    val currentActivity: Activity? = activity
    val bookmarksAdapter = bookmarksAdapter
    if (currentActivity is QuranActivity && bookmarksAdapter != null) {
      when (itemId) {
        R.id.cab_delete -> {
          val selected: List<QuranRow> = bookmarksAdapter.getCheckedItems()
          val size = selected.size
          val res = resources
          bookmarkPresenter.deleteAfterSomeTime(selected)
          val recyclerView = recyclerView
          if (recyclerView != null) {
            val snackbar = Snackbar.make(
              recyclerView,
              res.getQuantityString(R.plurals.bookmark_tag_deleted, size, size),
              BookmarkPresenter.DELAY_DELETION_DURATION_IN_MS
            )
            snackbar.setAction(R.string.undo, mOnUndoClickListener)
            snackbar.setTextColor(res.getColor(R.color.default_text))
            snackbar.getView()
              .setBackgroundColor(res.getColor(R.color.snackbar_background_color))
            snackbar.show()
          }
          return true
        }

        R.id.cab_new_tag -> {
          currentActivity.addTag()
          return true
        }

        R.id.cab_edit_tag -> {
          handleTagEdit(currentActivity, bookmarksAdapter.getCheckedItems())
          return true
        }

        R.id.cab_tag_bookmark -> {
          handleTagBookmarks(currentActivity, bookmarksAdapter.getCheckedItems())
          return true
        }
      }
    }
    return false
  }

  fun onCloseContextualActionMenu() {
    bookmarksAdapter?.uncheckAll()
  }

  private fun handleRowClicked(activity: Activity?, row: QuranRow) {
    if (!row.isHeader && activity is QuranActivity) {
      val quranActivity = activity
      if (row.isAyahBookmark) {
        quranActivity.jumpToAndHighlight(row.page, row.sura, row.ayah)
      } else {
        quranActivity.jumpTo(row.page)
      }
    }
  }

  private fun handleTagEdit(activity: QuranActivity, selected: List<QuranRow>) {
    if (selected.size == 1) {
      val row = selected[0]
      row.userTagId()?.let { tagId -> activity.editTag(tagId, row.text) }
    }
  }

  private fun handleTagBookmarks(activity: QuranActivity, selected: List<QuranRow>) {
    val ids = selected.mapNotNull { row -> row.bookmarkId }.toTypedArray()
    activity.tagBookmarks(ids)
  }

  private fun QuranRow.userTagId(): String? {
    return tagId?.takeUnless { tagId -> tagId.isDefaultBookmarkCollectionId() }
  }

  companion object {
    fun newInstance(): BookmarksFragment {
      return BookmarksFragment()
    }
  }
}
