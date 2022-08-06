package com.quran.labs.androidquran.presenter.bookmark

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarksContextualModePresenter @Inject constructor() : Presenter<BookmarksFragment> {

  private var actionMode: ActionMode? = null
  private var fragment: BookmarksFragment? = null
  private var activity: AppCompatActivity? = null

  fun isInActionMode(): Boolean {
    return actionMode != null
  }

  private fun startActionMode() {
    activity?.let {
      actionMode = it.startSupportActionMode(ModeCallback())
    }
  }

  fun invalidateActionMode(startIfStopped: Boolean) {
    if (actionMode != null) {
      actionMode!!.invalidate()
    } else if (startIfStopped) {
      startActionMode()
    }
  }

  fun finishActionMode() {
    actionMode?.finish()
  }

  override fun bind(what: BookmarksFragment) {
    fragment = what
    activity = what.activity as AppCompatActivity
  }

  override fun unbind(what: BookmarksFragment) {
    if (what == this.fragment) {
      this.fragment = null
      activity = null
    }
  }

  private inner class ModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
      activity?.menuInflater?.inflate(R.menu.bookmark_contextual_menu, menu)
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
      fragment?.prepareContextualMenu(menu)
      return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
      val result = fragment?.onContextualActionClicked(item.itemId) ?: false
      finishActionMode()
      return result
    }

    override fun onDestroyActionMode(mode: ActionMode) {
      fragment?.onCloseContextualActionMenu()
      if (mode == actionMode) {
        actionMode = null
      }
    }
  }
}
