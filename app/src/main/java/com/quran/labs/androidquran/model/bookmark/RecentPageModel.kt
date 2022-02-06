package com.quran.labs.androidquran.model.bookmark

import androidx.annotation.UiThread
import com.quran.data.model.bookmark.RecentPage
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.database.BookmarksDBAdapter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.observers.DisposableSingleObserver
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class RecentPageModel @Inject constructor(
  private val bookmarksDBAdapter: BookmarksDBAdapter
) {

  private val lastPageSubject = BehaviorSubject.create<Int>()
  private val refreshRecentPagePublishSubject = PublishSubject.create<Boolean>().toSerialized()
  private val recentWriterSubject = PublishSubject.create<PersistRecentPagesRequest>()
  private val recentPagesUpdatedObservable: Observable<Boolean>
  private var initialDataSubscription: DisposableSingleObserver<List<RecentPage>>? = null

  init {
    val recentWritesObservable = recentWriterSubject.hide()
      .observeOn(Schedulers.io())
      .map { update ->
        if (update.deleteRangeStart != null) {
          bookmarksDBAdapter.replaceRecentRangeWithPage(
            update.deleteRangeStart,
            update.deleteRangeEnd!!,
            update.page
          )
        } else {
          bookmarksDBAdapter.addRecentPage(update.page)
        }
        return@map true
      }

    recentPagesUpdatedObservable = Observable.merge(
      recentWritesObservable,
      refreshRecentPagePublishSubject.hide()
    ).share()

    // there needs to always be one subscriber in order for us to properly be able
    // to write updates to the database (even when no one else is subscribed).
    recentPagesUpdatedObservable.subscribe()

    // fetch the first set of "recent pages"
    initialDataSubscription = this.getRecentPagesObservable()
      // this is only to avoid serializing lastPageSubject
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeWith(object : DisposableSingleObserver<List<RecentPage>>() {
        override fun onSuccess(recentPages: List<RecentPage>) {
          val page = if (recentPages.isNotEmpty()) recentPages[0].page else Constants.NO_PAGE
          lastPageSubject.onNext(page)
          initialDataSubscription = null
        }

        override fun onError(e: Throwable) {}
      })
  }

  fun notifyRecentPagesUpdated() {
    refreshRecentPagePublishSubject.onNext(true)
  }

  @UiThread
  fun updateLatestPage(page: Int) {
    // it is possible (though unlikely) for a page update to come in while we're still waiting
    // for the initial query of recent pages from the database. if so, unsubscribe from the initial
    // query since its data will be stale relative to this data.
    initialDataSubscription?.dispose()
    lastPageSubject.onNext(page)
  }

  @UiThread
  fun persistLatestPage(minimumPage: Int, maximumPage: Int, lastPage: Int) {
    val min = if (minimumPage == maximumPage) null else minimumPage
    val max = if (min == null) null else maximumPage
    recentWriterSubject.onNext(
      PersistRecentPagesRequest(lastPage, min, max)
    )
  }

  /**
   * Returns an observable of the very latest pages visited
   *
   * Note that this stream never terminates, and will return pages even when they have not yet been
   * persisted to the database. This is basically a stream of every page as it gets visited.
   *
   * @return an Observable of the latest pages visited
   */
  fun getLatestPageObservable(): Observable<Int> {
    return lastPageSubject.hide()
  }

  /**
   * Returns an observable of observing when recent pages change in the database
   *
   * Note that this stream never terminates, and will emit onNext when the database has been
   * updated. Note that writes to the database are typically batched, and as thus, this observable
   * will fire a lot less than [.getLatestPageObservable].
   *
   * @return an observable that receives events whenever recent pages is persisted
   */
  fun getRecentPagesUpdatedObservable(): Observable<Boolean> = recentPagesUpdatedObservable

  open fun getRecentPagesObservable(): Single<List<RecentPage>> {
    return Single.fromCallable { bookmarksDBAdapter.getRecentPages() }
      .subscribeOn(Schedulers.io())
  }

  private class PersistRecentPagesRequest(
    val page: Int,
    val deleteRangeStart: Int?,
    val deleteRangeEnd: Int?,
  )
}
