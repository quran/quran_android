package com.quran.labs.androidquran.presenter.bookmark

import com.quran.data.dao.BookmarksDao
import com.quran.data.di.AppScope
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@SingleIn(AppScope::class)
open class TagBookmarkPresenter @Inject internal constructor(
  private val bookmarksDao: BookmarksDao
) : Presenter<TagBookmarkDialog> {
  private val checkedTags = HashSet<String>()

  private var dialog: TagBookmarkDialog? = null

  private var tags: List<Tag>? = null
  private var bookmarkIds: Array<String>? = null
  // for multiple bookmarks, the tags that all of the bookmarks share. these are
  // the tags shown as checked, and unchecking one is what removes it from all
  // of the bookmarks (see #2208). tags that only some bookmarks have are not
  // in this set, so saving never removes them.
  private var commonTagIds: Set<String> = emptySet()
  private var madeChanges = false
  private var shouldRefreshTags = false
  private val presenterScope = MainScope()

  init {
    presenterScope.launch {
      try {
        bookmarksDao.tagsFlow()
          .drop(1)
          .collect {
            shouldRefreshTags = true
            if (tags != null && dialog != null) {
              saveChanges()
              refresh()
            }
          }
      } catch (throwable: Throwable) {
        Timber.e(throwable, "Error observing bookmark tags")
      }
    }
  }

  fun setBookmarksMode(bookmarkIds: Array<String>?) {
    this.bookmarkIds = bookmarkIds
    checkedTags.clear()
    refresh()
  }

  open fun refresh() {
    Single.zip(
      tagsObservable,
      bookmarkTagIdsObservable
    ) { first: List<Tag>, second: List<String> ->
      Pair(first, second)
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({ data: Pair<List<Tag>, List<String>> ->
        this.onRefreshedData(data)
      }, { throwable ->
        Timber.e(throwable, "Unable to refresh bookmark tags")
      })
  }

  open fun onRefreshedData(data: Pair<List<Tag>, List<String>>) {
    val bookmarkTags = data.second
    val updatedCheckedTags = data.first.filter { tag: Tag -> bookmarkTags.contains(tag.id) }
    checkedTags.clear()
    checkedTags.addAll(updatedCheckedTags.map { it.id })
    commonTagIds = if ((bookmarkIds?.size ?: 0) > 1) checkedTags.toSet() else emptySet()

    madeChanges = false
    this@TagBookmarkPresenter.tags = data.first
    shouldRefreshTags = false
    dialog?.setData(this@TagBookmarkPresenter.tags, checkedTags)
  }

  private val tagsObservable: Single<List<Tag>>
    get() {
      val tags = tags
      return if (tags == null || shouldRefreshTags) {
        Single.fromCallable { runBlocking { bookmarksDao.tags() } }
      } else {
        Single.just(tags)
      }
    }

  open fun saveChanges() {
    if (madeChanges) {
      Single.fromCallable {
        val bookmarkIds = bookmarkIds ?: emptyArray()
        if (bookmarkIds.size > 1) {
          saveChangesForMultipleBookmarks(bookmarkIds)
        } else {
          runBlocking {
            bookmarksDao.updateBookmarkTags(
              bookmarkIds = bookmarkIds,
              tagIds = checkedTags,
              deleteNonTagged = bookmarkIds.size == 1
            )
          }
        }
      }
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe({ onSaveChangesDone() }, { throwable ->
          Timber.e(throwable, "Unable to save bookmark tags")
        })
    } else {
      onSaveChangesDone()
    }
  }

  // updateBookmarkTags with deleteNonTagged = false only ever adds tags, which
  // is why tags could not be removed from multiple bookmarks (#2208). remove
  // the common tags that were unchecked from every bookmark first, then add
  // the checked tags to all of them as before. tags that only some of the
  // bookmarks have are in neither set, so they are left alone.
  private fun saveChangesForMultipleBookmarks(bookmarkIds: Array<String>) {
    runBlocking {
      val removedTagIds = commonTagIds - checkedTags
      if (removedTagIds.isNotEmpty()) {
        val bookmarksById = bookmarksDao.bookmarks().associateBy { it.id }
        for (bookmarkId in bookmarkIds) {
          val bookmark = bookmarksById[bookmarkId] ?: continue
          for (tagId in removedTagIds) {
            bookmarksDao.removeBookmarkFromTag(bookmark, tagId)
          }
        }
      }
      bookmarksDao.updateBookmarkTags(
        bookmarkIds = bookmarkIds,
        tagIds = checkedTags,
        deleteNonTagged = false
      )
    }
  }

  open fun onSaveChangesDone() {
    madeChanges = false
  }

  fun toggleTag(id: String): Boolean {
    var result = false

    if (checkedTags.contains(id)) {
      checkedTags.remove(id)
    } else {
      checkedTags.add(id)
      result = true
    }
    setMadeChanges()
    return result
  }

  fun addTag() {
    dialog?.showAddTagDialog()
  }

  private fun setMadeChanges() {
    madeChanges = true
  }

  private val bookmarkTagIdsObservable: Single<List<String>>
    get() {
      return Single.fromCallable {
        runBlocking {
          val ids = bookmarkIds
          if (ids != null && ids.size == 1) {
            bookmarksDao.getBookmarkTagIds(ids[0])
          } else if (ids != null && ids.size > 1) {
            // only the tags that every selected bookmark shares start out
            // checked - showing all of them would make unchecking a tag look
            // like it removes it from bookmarks that never had it, and showing
            // none (the previous behaviour) made removal impossible (#2208).
            ids.map { bookmarksDao.getBookmarkTagIds(it).toSet() }
              .reduce { common, tagIds -> common.intersect(tagIds) }
              .toList()
          } else {
            emptyList()
          }
        }
      }
    }

  override fun bind(what: TagBookmarkDialog) {
    this.dialog = what
    if (tags != null) {
      // replay the last set of tags and checked tags that we had.
      this.dialog?.setData(tags, checkedTags)
    }
  }

  override fun unbind(what: TagBookmarkDialog) {
    if (what == this.dialog) {
      this.dialog = null
    }
  }
}
