package com.quran.labs.androidquran.presenter.bookmark

import com.quran.data.dao.BookmarksDao
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.AddTagDialog

import dev.zacsweers.metro.Inject
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

  fun addTag(tagName: String) {
    presenterScope.launch {
      bookmarksDao.addTag(tagName)
    }
  }

  fun updateTag(tag: Tag) {
    presenterScope.launch {
      bookmarksDao.updateTag(tag)
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
