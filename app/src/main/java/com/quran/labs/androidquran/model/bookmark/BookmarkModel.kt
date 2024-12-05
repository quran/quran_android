package com.quran.labs.androidquran.model.bookmark

import androidx.core.util.Pair
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import com.quran.labs.androidquran.ui.helpers.QuranRow
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("") // Use BookmarksDao instead
@Singleton
open class BookmarkModel @Inject constructor(
  private val bookmarksDBAdapter: BookmarksDBAdapter,
  private val recentPageModel: RecentPageModel
) {
  private val tagPublishSubject =
    PublishSubject.create<Boolean>().toSerialized()
  private val bookmarksPublishSubject =
    PublishSubject.create<Boolean>().toSerialized()

  open fun tagsObservable(): Observable<Boolean> {
    return tagPublishSubject.hide()
  }

  fun recentPagesUpdatedObservable(): Observable<Boolean> {
    return recentPageModel.getRecentPagesUpdatedObservable()
  }

  fun bookmarksObservable(): Observable<Boolean> {
    return bookmarksPublishSubject.hide()
  }

  fun notifyBookmarksUpdated() {
    bookmarksPublishSubject.onNext(true)
  }

  fun notifyRecentPagesUpdated(page: Int) {
    recentPageModel.notifyRecentPagesUpdated()
    recentPageModel.updateLatestPage(page)
  }

  open fun getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData> {
    return Single.zip(
      tagsObservable,
      getBookmarksObservable(sortOrder),
      recentPageModel.getRecentPagesObservable()
    ) { tags: List<Tag>, bookmarks: List<Bookmark>, recentPages: List<RecentPage> ->
      BookmarkData(
        tags,
        bookmarks,
        recentPages
      )
    }
      .subscribeOn(Schedulers.io())
  }

  fun removeItemsObservable(
    itemsToRemoveRef: List<QuranRow>
  ): Completable {
    return Completable.fromCallable {
      val tagsToDelete: MutableList<Long> =
        ArrayList()
      val bookmarksToDelete: MutableList<Long> =
        ArrayList()
      val untag: MutableList<Pair<Long, Long>> =
        ArrayList()
      var i = 0
      val size = itemsToRemoveRef.size
      while (i < size) {
        val row = itemsToRemoveRef[i]
        if (row.isBookmarkHeader && row.tagId > 0) {
          tagsToDelete.add(row.tagId)
        } else if (row.isBookmark && row.bookmarkId > 0) {
          if (row.tagId > 0) {
            untag.add(
              Pair(
                row.bookmarkId,
                row.tagId
              )
            )
          } else {
            bookmarksToDelete.add(row.bookmarkId)
          }
        }
        i++
      }
      bookmarksDBAdapter.bulkDelete(tagsToDelete, bookmarksToDelete, untag)
      bookmarksPublishSubject.onNext(true)
      if (tagsToDelete.isNotEmpty()) {
        tagPublishSubject.onNext(true)
      }
      null
    }.subscribeOn(Schedulers.io())
  }

  fun addTagObservable(title: String): Observable<Long> {
    return Observable.fromCallable {
      val result = bookmarksDBAdapter.addTag(title)
      tagPublishSubject.onNext(true)
      result
    }.subscribeOn(Schedulers.io())
  }

  fun updateTag(tag: Tag): Completable {
    return Completable.fromCallable {
      val result = bookmarksDBAdapter.updateTag(tag.id, tag.name)
      if (result) {
        tagPublishSubject.onNext(true)
      }
      null
    }.subscribeOn(Schedulers.io())
  }

  open fun updateBookmarkTags(
    bookmarkIds: LongArray,
    tagIds: Set<Long>,
    deleteNonTagged: Boolean
  ): Observable<Boolean> {
    return Observable.fromCallable {
      val result =
        bookmarksDBAdapter.tagBookmarks(bookmarkIds, tagIds, deleteNonTagged)
      if (result) {
        bookmarksPublishSubject.onNext(true)
      }
      result
    }.subscribeOn(Schedulers.io())
  }

  open fun safeAddBookmark(sura: Int?, ayah: Int?, page: Int): Observable<Long> {
    return Observable.fromCallable {
      val result = bookmarksDBAdapter.addBookmarkIfNotExists(sura, ayah, page)
      bookmarksPublishSubject.onNext(true)
      result
    }.subscribeOn(Schedulers.io())
  }

  open val tagsObservable: Single<List<Tag>>
    get() = Single.fromCallable<List<Tag>> { bookmarksDBAdapter.getTags() }
      .subscribeOn(Schedulers.io())

  private fun getBookmarksObservable(sortOrder: Int): Single<List<Bookmark>> {
    return Single.fromCallable {
      bookmarksDBAdapter.getBookmarks(
        sortOrder
      )
    }
  }

  open fun getBookmarkTagIds(bookmarkIdSingle: Single<Long>): Maybe<List<Long>> {
    return bookmarkIdSingle.filter { bookmarkId: Long -> bookmarkId > 0 }
      .map { bookmarkId: Long ->
        bookmarksDBAdapter.getBookmarkTagIds(bookmarkId)
      }
      .subscribeOn(Schedulers.io())
  }

  fun getBookmarkId(sura: Int?, ayah: Int?, page: Int): Single<Long> {
    return Single.fromCallable {
      bookmarksDBAdapter.getBookmarkId(
        sura,
        ayah,
        page
      )
    }
      .subscribeOn(Schedulers.io())
  }

  fun importBookmarksObservable(data: BookmarkData): Observable<Boolean> {
    return Observable.fromCallable {
      val result = bookmarksDBAdapter.importBookmarks(data)
      if (result) {
        bookmarksPublishSubject.onNext(true)
      }
      result
    }.subscribeOn(Schedulers.io()).cache()
  }
}
