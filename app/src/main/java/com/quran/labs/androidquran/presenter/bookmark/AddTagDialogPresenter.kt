package com.quran.labs.androidquran.presenter.bookmark

import com.quran.labs.androidquran.dao.Tag
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.AddTagDialog

import javax.inject.Inject

class AddTagDialogPresenter @Inject
internal constructor(private val bookmarkModel: BookmarkModel) : Presenter<AddTagDialog> {
  private var dialog: AddTagDialog? = null

  fun addTag(tagName: String): Boolean {
    return if (tagName.isBlank()) {
      dialog?.onBlankTagName()
      false
    } else {
      bookmarkModel.addTagObservable(tagName)
          .subscribe()
      true
    }
  }

  fun updateTag(tag: Tag): Boolean {
    return if (tag.name.isBlank()) {
      dialog?.onBlankTagName()
      false
    } else {
      bookmarkModel.updateTag(tag)
          .subscribe()
      true
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
