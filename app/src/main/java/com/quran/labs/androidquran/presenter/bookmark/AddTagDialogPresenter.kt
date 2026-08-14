package com.quran.labs.androidquran.presenter.bookmark

import com.quran.data.dao.BookmarksDao
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.AddTagDialog

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AddTagDialogPresenter @Inject
internal constructor(private val bookmarksDao: BookmarksDao) : Presenter<AddTagDialog> {
  private var dialog: AddTagDialog? = null
  private var tags: List<Tag> = emptyList()
  private val presenterScope = MainScope()

  init {
    presenterScope.launch {
      tags = bookmarksDao.tags()
      bookmarksDao.tagsFlow().collect { tags -> this@AddTagDialogPresenter.tags = tags }
    }
  }

  fun validate(tagName: String, tagId: String?): Boolean {
    tags = runBlocking { bookmarksDao.tags() }
    if (tagName.isBlank()) {
      dialog?.onBlankTagName()
      return false
    } else {
      if (tags.any { it.name == tagName && it.id != tagId }) {
        dialog?.onDuplicateTagName()
        return false
      }
    }
    return true
  }

  fun addTag(tagName: String, onAdded: () -> Unit) {
    val requestDialog = dialog ?: return
    presenterScope.launch {
      try {
        bookmarksDao.addTag(tagName)
        if (dialog === requestDialog) {
          onAdded()
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: IllegalArgumentException) {
        if (dialog === requestDialog) {
          requestDialog.onDuplicateTagName()
        }
      }
    }
  }

  fun updateTag(tag: Tag, onUpdated: () -> Unit) {
    val requestDialog = dialog ?: return
    presenterScope.launch {
      if (bookmarksDao.updateTag(tag)) {
        if (dialog === requestDialog) {
          onUpdated()
        }
      } else if (dialog === requestDialog) {
        requestDialog.onDuplicateTagName()
      }
    }
  }

  override fun bind(dialog: AddTagDialog) {
    this.dialog = dialog
  }

  override fun unbind(dialog: AddTagDialog) {
    if (this.dialog === dialog) {
      this.dialog = null
    }
  }
}
