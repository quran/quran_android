package com.quran.labs.androidquran.presenter.bookmark;

import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;

import javax.inject.Inject;

public class AddTagDialogPresenter implements Presenter<AddTagDialog> {
  private BookmarkModel mBookmarkModel;

  @Inject
  AddTagDialogPresenter(BookmarkModel bookmarkModel) {
    mBookmarkModel = bookmarkModel;
  }

  public void addTag(String tagName) {
    mBookmarkModel.addTagObservable(tagName)
        .subscribe();
  }

  public void updateTag(Tag tag) {
    mBookmarkModel.updateTag(tag)
        .subscribe();
  }

  @Override
  public void bind(AddTagDialog dialog) {
  }

  @Override
  public void unbind(AddTagDialog dialog) {
  }
}
