package com.quran.labs.androidquran.presenter.bookmark;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.quran.data.model.bookmark.Bookmark;
import com.quran.data.model.bookmark.Tag;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;


@Singleton
public class TagBookmarkPresenter implements Presenter<TagBookmarkDialog> {

  private final BookmarkModel bookmarkModel;
  private final HashSet<Long> checkedTags = new HashSet<>();

  private TagBookmarkDialog dialog;

  private List<Tag> tags;
  private long[] bookmarkIds;
  private boolean madeChanges;
  private boolean saveImmediate;
  private boolean shouldRefreshTags;
  private Bookmark potentialAyahBookmark;


  @SuppressWarnings("CheckReturnValue")
  @Inject
  TagBookmarkPresenter(BookmarkModel bookmarkModel) {
    this.bookmarkModel = bookmarkModel;

    // this is unrelated to which views are attached, so it should be run
    // and we don't need to worry about disposing it.
    this.bookmarkModel.tagsObservable()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(ignore -> {
          shouldRefreshTags = true;
          if (tags != null && dialog != null) {
            saveChanges();
            refresh();
          }
        });
  }

  public void setBookmarksMode(long[] bookmarkIds) {
    setMode(bookmarkIds, null);
  }

  public void setAyahBookmarkMode(int sura, int ayah, int page) {
    setMode(null, new Bookmark(-1, sura, ayah, page));
  }

  private void setMode(long[] bookmarkIds, Bookmark potentialAyahBookmark) {
    this.bookmarkIds = bookmarkIds;
    this.potentialAyahBookmark = potentialAyahBookmark;
    saveImmediate = this.potentialAyahBookmark != null;
    checkedTags.clear();
    refresh();
  }

  @SuppressWarnings("CheckReturnValue")
  void refresh() {
    // even though this is called from the presenter, we'll cache the result even if the
    // view ends up detaching.
    Single.zip(getTagsObservable(), getBookmarkTagIdsObservable(), Pair::new)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::onRefreshedData);
  }

  void onRefreshedData(Pair<List<Tag>, List<Long>> data) {
    List<Tag> tags1 = data.first;
    int numberOfTags = tags1.size();
    if (numberOfTags == 0 || tags1.get(numberOfTags - 1).getId() != -1) {
      tags1.add(new Tag(-1, ""));
    }

    List<Long> bookmarkTags = data.second;
    checkedTags.clear();
    for (int i = 0, tagsSize = tags1.size(); i < tagsSize; i++) {
      Tag tag = tags1.get(i);
      if (bookmarkTags.contains(tag.getId())) {
        checkedTags.add(tag.getId());
      }
    }
    madeChanges = false;
    TagBookmarkPresenter.this.tags = tags1;
    shouldRefreshTags = false;
    if (dialog != null) {
      dialog.setData(TagBookmarkPresenter.this.tags, checkedTags);
    }
  }

  private Single<List<Tag>> getTagsObservable() {
    if (tags == null || shouldRefreshTags) {
      return bookmarkModel.getTagsObservable();
    } else {
      return Single.just(tags);
    }
  }

  @SuppressWarnings("CheckReturnValue")
  public void saveChanges() {
    if (madeChanges) {
      getBookmarkIdsObservable()
          .flatMap(bookmarkIds ->
              bookmarkModel.updateBookmarkTags(bookmarkIds, checkedTags, bookmarkIds.length == 1))
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(ignored -> onSaveChangesDone());
    } else {
      onSaveChangesDone();
    }
  }

  void onSaveChangesDone() {
    madeChanges = false;
  }

  /**
   * Get an Observable with the list of bookmark ids that will be tagged.
   *
   * @return the list of bookmark ids to tag
   */
  private Observable<long[]> getBookmarkIdsObservable() {
    Observable<long[]> observable;
    if (bookmarkIds != null) {
      // if we already have a list, we just use that
      observable = Observable.just(bookmarkIds);
    } else {
      // if we don't have a bookmark id, we'll add the bookmark and use its id
      observable = bookmarkModel.safeAddBookmark(potentialAyahBookmark.getSura(),
          potentialAyahBookmark.getAyah(), potentialAyahBookmark.getPage())
          .map(bookmarkId -> new long[]{ bookmarkId });
    }
    return observable;
  }

  public boolean toggleTag(long id) {
    boolean result = false;
    if (id > 0) {
      if (checkedTags.contains(id)) {
        checkedTags.remove(id);
      } else {
        checkedTags.add(id);
        result = true;
      }
      setMadeChanges();
    } else if (dialog != null) {
      dialog.showAddTagDialog();
    }
    return result;
  }

  void setMadeChanges() {
    madeChanges = true;
    if (saveImmediate) {
      saveChanges();
    }
  }

  private Single<List<Long>> getBookmarkTagIdsObservable() {
    Single<Long> bookmarkId;
    if (potentialAyahBookmark != null) {
      bookmarkId = bookmarkModel.getBookmarkId(potentialAyahBookmark.getSura(),
          potentialAyahBookmark.getAyah(), potentialAyahBookmark.getPage());
    } else {
      bookmarkId = Single.just(
          bookmarkIds != null && bookmarkIds.length == 1 ? bookmarkIds[0] : 0);
    }
    return bookmarkModel.getBookmarkTagIds(bookmarkId)
        .defaultIfEmpty(new ArrayList<>());
  }

  @Override
  public void bind(@NonNull TagBookmarkDialog dialog) {
    this.dialog = dialog;
    if (tags != null) {
      // replay the last set of tags and checked tags that we had.
      this.dialog.setData(tags, checkedTags);
    }
  }

  @Override
  public void unbind(TagBookmarkDialog dialog) {
    if (dialog.equals(this.dialog)) {
      this.dialog = null;
    }
  }
}
