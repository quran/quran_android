package com.quran.labs.androidquran.model.bookmark;

import android.support.v4.util.Pair;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;


@Singleton
public class BookmarkModel {
  private final RecentPageModel recentPageModel;
  private final BookmarksDBAdapter bookmarksDBAdapter;
  private final Subject<Tag> tagPublishSubject;
  private final Subject<Boolean> bookmarksPublishSubject;

  @Inject
  public BookmarkModel(BookmarksDBAdapter bookmarksAdapter, RecentPageModel recentPageModel) {
    this.recentPageModel = recentPageModel;
    this.bookmarksDBAdapter = bookmarksAdapter;

    tagPublishSubject = PublishSubject.<Tag>create().toSerialized();
    bookmarksPublishSubject = PublishSubject.<Boolean>create().toSerialized();
  }

  public Observable<Tag> tagsObservable() {
    return tagPublishSubject.hide();
  }

  public Observable<Boolean> recentPagesUpdatedObservable() {
    return recentPageModel.getRecentPagesUpdatedObservable();
  }

  public Observable<Boolean> bookmarksObservable() {
    return bookmarksPublishSubject.hide();
  }

  public Single<BookmarkData> getBookmarkDataObservable(final int sortOrder) {
    return Single.zip(getTagsObservable(),
        getBookmarksObservable(sortOrder),
        recentPageModel.getRecentPagesObservable(),
        BookmarkData::new)
        .subscribeOn(Schedulers.io());
  }

  public Completable removeItemsObservable(
      final List<QuranRow> itemsToRemoveRef) {
    return Completable.fromCallable(() -> {
      List<Long> tagsToDelete = new ArrayList<>();
      List<Long> bookmarksToDelete = new ArrayList<>();
      List<Pair<Long, Long>> untag = new ArrayList<>();
      for (int i = 0, size = itemsToRemoveRef.size(); i < size; i++) {
        QuranRow row = itemsToRemoveRef.get(i);
        if (row.isBookmarkHeader() && row.tagId > 0) {
          tagsToDelete.add(row.tagId);
        } else if (row.isBookmark() && row.bookmarkId > 0) {
          if (row.tagId > 0) {
            untag.add(new Pair<>(row.bookmarkId, row.tagId));
          } else {
            bookmarksToDelete.add(row.bookmarkId);
          }
        }
      }
      bookmarksDBAdapter.bulkDelete(tagsToDelete, bookmarksToDelete, untag);
      return null;
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Long> addTagObservable(final String title) {
    return Observable.fromCallable(() -> {
      Long result = bookmarksDBAdapter.addTag(title);
      tagPublishSubject.onNext(new Tag(result, title));
      return result;
    }).subscribeOn(Schedulers.io());
  }

  public Completable updateTag(final Tag tag) {
    return Completable.fromCallable(() -> {
      boolean result = bookmarksDBAdapter.updateTag(tag.id, tag.name);
      if (result) {
        tagPublishSubject.onNext(tag);
      }
      return null;
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Boolean> updateBookmarkTags(final long[] bookmarkIds,
                                                final Set<Long> tagIds,
                                                final boolean deleteNonTagged) {
    return Observable.fromCallable(() -> {
      boolean result = bookmarksDBAdapter.tagBookmarks(bookmarkIds, tagIds, deleteNonTagged);
      if (result) {
        bookmarksPublishSubject.onNext(true);
      }
      return result;
    }).subscribeOn(Schedulers.io());
  }

  public Observable<Long> safeAddBookmark(final Integer sura, final Integer ayah, final int page) {
    return Observable.fromCallable(() -> {
      long result = bookmarksDBAdapter.addBookmarkIfNotExists(sura, ayah, page);
      bookmarksPublishSubject.onNext(true);
      return result;
    }).subscribeOn(Schedulers.io());
  }

  public Single<List<Tag>> getTagsObservable() {
    return Single.fromCallable(bookmarksDBAdapter::getTags).subscribeOn(Schedulers.io());
  }

  private Single<List<Bookmark>> getBookmarksObservable(final int sortOrder) {
    return Single.fromCallable(() -> bookmarksDBAdapter.getBookmarks(sortOrder));
  }

  public Maybe<List<Long>> getBookmarkTagIds(Single<Long> bookmarkIdSingle) {
    return bookmarkIdSingle.filter(bookmarkId -> bookmarkId > 0)
        .map(bookmarksDBAdapter::getBookmarkTagIds)
        .subscribeOn(Schedulers.io());
  }

  public Single<Long> getBookmarkId(final Integer sura, final Integer ayah, final int page) {
    return Single.fromCallable(() -> bookmarksDBAdapter.getBookmarkId(sura, ayah, page))
        .subscribeOn(Schedulers.io());
  }

  public Observable<List<Bookmark>> getBookmarkedAyahsOnPageObservable(Integer... pages) {
    return Observable.fromArray(pages)
        .map(bookmarksDBAdapter::getBookmarkedAyahsOnPage)
        .filter(bookmarks -> !bookmarks.isEmpty())
        .subscribeOn(Schedulers.io());
  }

  public Observable<Pair<Integer, Boolean>> getIsBookmarkedObservable(Integer... pages) {
    return Observable.fromArray(pages)
        .map(page -> new Pair<>(page, bookmarksDBAdapter.getBookmarkId(null, null, page) > 0))
        .subscribeOn(Schedulers.io());
  }

  public Single<Boolean> getIsBookmarkedObservable(
      final Integer sura, final Integer ayah, final int page) {
    return getBookmarkId(sura, ayah, page)
        .map(bookmarkId -> bookmarkId > 0)
        .subscribeOn(Schedulers.io());
  }

  public Single<Boolean> toggleBookmarkObservable(
      final Integer sura, final Integer ayah, final int page) {
    return getBookmarkId(sura, ayah, page)
        .map(bookmarkId -> {
          boolean result;
          if (bookmarkId > 0) {
            bookmarksDBAdapter.removeBookmark(bookmarkId);
            result = false;
          } else {
            bookmarksDBAdapter.addBookmark(sura, ayah, page);
            result = true;
          }
          bookmarksPublishSubject.onNext(true);
          return result;
        }).subscribeOn(Schedulers.io());
  }

  public Observable<Boolean> importBookmarksObservable(final BookmarkData data) {
    return Observable.fromCallable(() -> {
      boolean result = bookmarksDBAdapter.importBookmarks(data);
      if (result) {
        bookmarksPublishSubject.onNext(true);
      }
      return result;
    }).subscribeOn(Schedulers.io()).cache();
  }
}
