package com.quran.labs.androidquran.presenter.bookmark;

import android.support.v4.util.Pair;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;


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

  @Inject
  TagBookmarkPresenter(BookmarkModel bookmarkModel) {
    this.bookmarkModel = bookmarkModel;
    this.bookmarkModel.tagsObservable()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Consumer<Tag>() {
          @Override
          public void accept(Tag tag) {
            shouldRefreshTags = true;
            if (tags != null && dialog != null) {
              // change this if we support updating tags from outside of QuranActivity
              tags.add(tags.size() - 1, tag);
              checkedTags.add(tag.id);
              dialog.setData(tags, checkedTags);
              setMadeChanges();
            }
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

  void refresh() {
    Single.zip(getTagsObservable(), getBookmarkTagIdsObservable(),
        new BiFunction<List<Tag>, List<Long>, Pair<List<Tag>, List<Long>>>() {
          @Override
          public Pair<List<Tag>, List<Long>> apply(List<Tag> tags, List<Long> bookmarkTagIds) {
            return new Pair<>(tags, bookmarkTagIds);
          }
        })
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Consumer<Pair<List<Tag>,List<Long>>>() {
          @Override
          public void accept(Pair<List<Tag>, List<Long>> data) {
            List<Tag> tags = data.first;
            int numberOfTags = tags.size();
            if (numberOfTags == 0 || tags.get(numberOfTags - 1).id != -1) {
              tags.add(new Tag(-1, ""));
            }

            List<Long> bookmarkTags = data.second;
            checkedTags.clear();
            for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
              Tag tag = tags.get(i);
              if (bookmarkTags.contains(tag.id)) {
                checkedTags.add(tag.id);
              }
            }
            madeChanges = false;
            TagBookmarkPresenter.this.tags = tags;
            shouldRefreshTags = false;
            if (dialog != null) {
              dialog.setData(TagBookmarkPresenter.this.tags, checkedTags);
            }
          }
        });
  }

  private Single<List<Tag>> getTagsObservable() {
    if (tags == null || shouldRefreshTags) {
      return bookmarkModel.getTagsObservable();
    } else {
      return Single.just(tags);
    }
  }

  public void saveChanges() {
    if (madeChanges) {
      getBookmarkIdsObservable()
          .flatMap(new Function<long[], Observable<Boolean>>() {
            @Override
            public Observable<Boolean> apply(long[] bookmarkIds) {
              return bookmarkModel.updateBookmarkTags(
                  bookmarkIds, checkedTags, bookmarkIds.length == 1);
            }
          })
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) {
              madeChanges = false;
            }
          });
    }
  }

  /**
   * Get an Observable with the list of bookmark ids that will be tagged.
   * @return the list of bookmark ids to tag
   */
  private Observable<long[]> getBookmarkIdsObservable() {
    Observable<long[]> observable;
    if (bookmarkIds != null) {
      // if we already have a list, we just use that
      observable = Observable.just(bookmarkIds);
    } else {
      // if we don't have a bookmark id, we'll add the bookmark and use its id
      observable = bookmarkModel.safeAddBookmark(potentialAyahBookmark.sura,
          potentialAyahBookmark.ayah, potentialAyahBookmark.page)
          .map(new Function<Long, long[]>() {
            @Override
            public long[] apply(Long bookmarkId) {
              return new long[]{ bookmarkId };
            }
          });
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
      bookmarkId = bookmarkModel.getBookmarkId(potentialAyahBookmark.sura,
          potentialAyahBookmark.ayah, potentialAyahBookmark.page);
    } else {
      bookmarkId = Single.just(
          bookmarkIds != null && bookmarkIds.length == 1 ? bookmarkIds[0] : 0);
    }
    return bookmarkModel.getBookmarkTagIds(bookmarkId)
        .defaultIfEmpty(new ArrayList<Long>())
        .toSingle();
  }

  @Override
  public void bind(TagBookmarkDialog dialog) {
    this.dialog = dialog;
    if (tags != null) {
      // replay the last set of tags and checked tags that we had.
      this.dialog.setData(tags, checkedTags);
    }
  }

  @Override
  public void unbind(TagBookmarkDialog dialog) {
    if (dialog == this.dialog) {
      this.dialog = null;
    }
  }
}
