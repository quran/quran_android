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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.dao.bookmark.BookmarkRawResult
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.presenter.bookmark.BookmarkPresenter
import com.quran.labs.androidquran.presenter.bookmark.BookmarksContextualModePresenter
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.ui.helpers.BookmarkUIConverter
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter.QuranTouchListener
import com.quran.labs.androidquran.ui.helpers.QuranRow
import dev.zacsweers.metro.Inject

class BookmarksFragment : Fragment(), QuranTouchListener {
  private var recyclerView: RecyclerView? = null
  private var bookmarksAdapter: QuranListAdapter? = null

  @JvmField
  @Inject
  var bookmarkPresenter: BookmarkPresenter? = null

  @JvmField
  @Inject
  var bookmarksContextualModePresenter: BookmarksContextualModePresenter? = null

  @JvmField
  @Inject
  var bookmarkUIConverter: BookmarkUIConverter? = null

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
    setHasOptionsMenu(true)
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.quran_list, container, false)

    val context = requireContext()

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
    return view
  }

  override fun onStart() {
    super.onStart()
    bookmarkPresenter?.bind(this)
    bookmarksContextualModePresenter?.bind(this)
  }

  override fun onStop() {
    bookmarkPresenter?.unbind(this)
    bookmarksContextualModePresenter?.unbind(this)
    super.onStop()
  }

  override fun onDestroyView() {
    recyclerView = null
    bookmarksAdapter = null
    super.onDestroyView()
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, inflater)
    val sortItem = menu.findItem(R.id.sort)
    if (sortItem != null) {
      sortItem.isVisible = true
      sortItem.isEnabled = true

      val bookmarkPresenter = bookmarkPresenter
      if (bookmarkPresenter != null) {
        if (BookmarksDBAdapter.SORT_DATE_ADDED == bookmarkPresenter.getSortOrder()) {
          val sortByDate = menu.findItem(R.id.sort_date)
          sortByDate.isChecked = true
        } else {
          val sortByLocation = menu.findItem(R.id.sort_location)
          sortByLocation.isChecked = true
        }

        val groupByTags = menu.findItem(R.id.group_by_tags)
        groupByTags.isChecked = bookmarkPresenter.isGroupedByTags

        val showRecents = menu.findItem(R.id.show_recents)
        showRecents.isChecked = bookmarkPresenter.isShowingRecents

        val showDates = menu.findItem(R.id.show_date)
        showDates.isChecked = bookmarkPresenter.isDateShowing
      }
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val itemId = item.itemId
    val bookmarkPresenter = bookmarkPresenter

    if (bookmarkPresenter != null) {
      when (itemId) {
        R.id.sort_date -> {
          bookmarkPresenter.setSortOrder(BookmarksDBAdapter.SORT_DATE_ADDED)
          item.isChecked = true
          return true
        }

        R.id.sort_location -> {
          bookmarkPresenter.setSortOrder(BookmarksDBAdapter.SORT_LOCATION)
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
    }

    return super.onOptionsItemSelected(item)
  }

  fun onNewRawData(rawItems: BookmarkRawResult) {
    val items = bookmarkUIConverter?.convertToUIResult(requireContext(), rawItems)

    val bookmarksAdapter = bookmarksAdapter
    val bookmarkPresenter = bookmarkPresenter
    if (bookmarksAdapter != null && items != null && bookmarkPresenter != null) {
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
    if (bookmarksAdapter != null && bookmarksContextualModePresenter != null) {
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
      if (bookmarksAdapter != null && bookmarksContextualModePresenter != null) {
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
    return selected.isBookmark || (selected.isBookmarkHeader && selected.tagId >= 0)
  }

  private val mOnUndoClickListener: View.OnClickListener = View.OnClickListener {
    bookmarkPresenter?.cancelDeletion()
    bookmarkPresenter?.requestData(true)
  }

  fun prepareContextualMenu(menu: Menu) {
    val bookmarksAdapter = bookmarksAdapter
    val bookmarkPresenter = bookmarkPresenter
    if (bookmarkPresenter != null && bookmarksAdapter != null) {
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
          bookmarkPresenter?.deleteAfterSomeTime(selected)
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
      activity.editTag(row.tagId, row.text)
    }
  }

  private fun handleTagBookmarks(activity: QuranActivity, selected: List<QuranRow>) {
    val ids = LongArray(selected.size)
    var i = 0
    val selectedItems = selected.size
    while (i < selectedItems) {
      ids[i] = selected[i].bookmarkId
      i++
    }
    activity.tagBookmarks(ids)
  }

  companion object {
    fun newInstance(): BookmarksFragment {
      return BookmarksFragment()
    }
  }
}
