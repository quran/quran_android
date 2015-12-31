package com.quran.labs.androidquran.presenter.bookmark;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.presenter.Presenter;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;

public class TagBookmarkPresenter implements Presenter<TagBookmarkDialog> {
  private static TagBookmarkPresenter sInstance;

  private BookmarkModel mBookmarkModel;
  private TagBookmarkDialog mDialog;

  private long[] mBookmarkIds;
  private Bookmark mPotentialAyahBookmark;

  private List<Tag> mTags;
  private boolean mMadeChanges;
  private boolean mSaveImmediate;
  private boolean mShouldRefreshTags;
  private HashSet<Long> mCheckedTags = new HashSet<>();

  public synchronized static TagBookmarkPresenter getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new TagBookmarkPresenter(context);
    }
    return sInstance;
  }

  private TagBookmarkPresenter(Context context) {
    mBookmarkModel = BookmarkModel.getInstance(context);
    mBookmarkModel.tagsObservable()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Tag>() {
          @Override
          public void call(Tag tag) {
            mShouldRefreshTags = true;
            if (mTags != null && mDialog != null) {
              // change this if we support updating tags from outside of QuranActivity
              mTags.add(mTags.size() - 1, tag);
              mCheckedTags.add(tag.id);
              mDialog.setData(mTags, mCheckedTags);
              setMadeChanges();
            }
          }
        });
  }

  @VisibleForTesting
  TagBookmarkPresenter(BookmarkModel model) {
    mBookmarkModel = model;
  }

  public void setBookmarksMode(long[] bookmarkIds) {
    setMode(bookmarkIds, null);
  }

  public void setAyahBookmarkMode(int sura, int ayah, int page) {
    setMode(null, new Bookmark(-1, sura, ayah, page));
  }

  private void setMode(long[] bookmarkIds, Bookmark potentialAyahBookmark) {
    mBookmarkIds = bookmarkIds;
    mPotentialAyahBookmark = potentialAyahBookmark;
    mSaveImmediate = mPotentialAyahBookmark != null;
    mCheckedTags.clear();
    refresh();
  }

  public void refresh() {
    Observable.zip(getTagsObservable(), getBookmarkTagIdsObservable(),
        new Func2<List<Tag>, List<Long>, Pair<List<Tag>, List<Long>>>() {
          @Override
          public Pair<List<Tag>, List<Long>> call(List<Tag> tags, List<Long> bookmarkTagIds) {
            return new Pair<>(tags, bookmarkTagIds);
          }
        })
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Pair<List<Tag>, List<Long>>>() {
          @Override
          public void call(Pair<List<Tag>, List<Long>> data) {
            List<Tag> tags = data.first;
            int numberOfTags = tags.size();
            if (numberOfTags == 0 || tags.get(numberOfTags - 1).id != -1) {
              tags.add(new Tag(-1, ""));
            }

            List<Long> bookmarkTags = data.second;
            mCheckedTags.clear();
            for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
              Tag tag = tags.get(i);
              if (bookmarkTags.contains(tag.id)) {
                mCheckedTags.add(tag.id);
              }
            }
            mMadeChanges = false;
            mTags = tags;
            mShouldRefreshTags = false;
            if (mDialog != null) {
              mDialog.setData(mTags, mCheckedTags);
            }
          }
        });
  }

  private Observable<List<Tag>> getTagsObservable() {
    if (mTags == null || mShouldRefreshTags) {
      return mBookmarkModel.getTagsObservable();
    } else {
      return Observable.just(mTags);
    }
  }

  public void saveChanges() {
    if (mMadeChanges) {
      getBookmarkIdsObservable()
          .flatMap(new Func1<long[], Observable<Boolean>>() {
            @Override
            public Observable<Boolean> call(long[] bookmarkIds) {
              return mBookmarkModel.updateBookmarkTags(
                  bookmarkIds, mCheckedTags, bookmarkIds.length == 1);
            }
          })
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(new Action1<Boolean>() {
            @Override
            public void call(Boolean aBoolean) {
              mMadeChanges = false;
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
    if (mBookmarkIds != null) {
      // if we already have a list, we just use that
      observable = Observable.just(mBookmarkIds);
    } else {
      // if we don't have a bookmark id, we'll add the bookmark and use its id
      observable = mBookmarkModel.safeAddBookmark(mPotentialAyahBookmark.sura,
          mPotentialAyahBookmark.ayah, mPotentialAyahBookmark.page)
          .map(new Func1<Long, long[]>() {
            @Override
            public long[] call(Long bookmarkId) {
              return new long[]{ bookmarkId };
            }
          });
    }
    return observable;
  }

  public boolean toggleTag(long id) {
    boolean result = false;
    if (id > 0) {
      if (mCheckedTags.contains(id)) {
        mCheckedTags.remove(id);
      } else {
        mCheckedTags.add(id);
        result = true;
      }
      setMadeChanges();
    } else if (mDialog != null) {
      mDialog.showAddTagDialog();
    }
    return result;
  }

  void setMadeChanges() {
    mMadeChanges = true;
    if (mSaveImmediate) {
      saveChanges();
    }
  }

  private Observable<List<Long>> getBookmarkTagIdsObservable() {
    Observable<Long> bookmarkId;
    if (mPotentialAyahBookmark != null) {
      bookmarkId = mBookmarkModel.getBookmarkId(mPotentialAyahBookmark.sura,
          mPotentialAyahBookmark.ayah, mPotentialAyahBookmark.page);
    } else {
      bookmarkId = Observable.just(
          mBookmarkIds != null && mBookmarkIds.length == 1 ? mBookmarkIds[0] : 0);
    }
    return mBookmarkModel.getBookmarkTagIds(bookmarkId)
        .defaultIfEmpty(new ArrayList<Long>());
  }

  @Override
  public void bind(TagBookmarkDialog dialog) {
    mDialog = dialog;
    if (mTags != null) {
      // replay the last set of tags and checked tags that we had.
      mDialog.setData(mTags, mCheckedTags);
    }
  }

  @Override
  public void unbind(TagBookmarkDialog dialog) {
    if (dialog == mDialog) {
      mDialog = null;
    }
  }
}
