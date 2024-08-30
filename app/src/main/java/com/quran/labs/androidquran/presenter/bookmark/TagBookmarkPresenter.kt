package com.quran.labs.androidquran.presenter.bookmark

import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class TagBookmarkPresenter @Inject internal constructor(private val bookmarkModel: BookmarkModel) :
  Presenter<TagBookmarkDialog> {
  private val checkedTags = HashSet<Long>()

  private var dialog: TagBookmarkDialog? = null

  private var tags: List<Tag>? = null
  private var bookmarkIds: LongArray? = null
  private var madeChanges = false
  private var saveImmediate = false
  private var shouldRefreshTags = false
  private var potentialAyahBookmark: Bookmark? = null


  init {
    // this is unrelated to which views are attached, so it should be run
    // and we don't need to worry about disposing it.
    bookmarkModel.tagsObservable()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { ignore: Boolean? ->
        shouldRefreshTags = true
        if (tags != null && dialog != null) {
          saveChanges()
          refresh()
        }
      }
  }

  fun setBookmarksMode(bookmarkIds: LongArray?) {
    setMode(bookmarkIds, null)
  }

  fun setAyahBookmarkMode(sura: Int, ayah: Int, page: Int) {
    setMode(null, Bookmark(-1, sura, ayah, page))
  }

  private fun setMode(bookmarkIds: LongArray?, potentialAyahBookmark: Bookmark?) {
    this.bookmarkIds = bookmarkIds
    this.potentialAyahBookmark = potentialAyahBookmark
    saveImmediate = this.potentialAyahBookmark != null
    checkedTags.clear()
    refresh()
  }

  open fun refresh() {
    // even though this is called from the presenter, we'll cache the result even if the
    // view ends up detaching.
    Single.zip(
      tagsObservable, bookmarkTagIdsObservable
    ) { first: List<Tag>, second: List<Long> ->
      Pair(first, second)
    }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { data: Pair<List<Tag>, List<Long>> ->
        this.onRefreshedData(data)
      }
  }

  open fun onRefreshedData(data: Pair<List<Tag>, List<Long>>) {
    val numberOfTags = data.first.size
    val tags1 = if (numberOfTags == 0 || data.first[numberOfTags - 1].id != -1L) {
      data.first + listOf(Tag(-1, ""))
    } else {
      data.first
    }

    val bookmarkTags = data.second
    val updatedCheckedTags = tags1.filter { tag: Tag -> bookmarkTags.contains(tag.id) }
    checkedTags.clear()
    checkedTags.addAll(updatedCheckedTags.map { it.id })

    madeChanges = false
    this@TagBookmarkPresenter.tags = tags1
    shouldRefreshTags = false
    dialog?.setData(this@TagBookmarkPresenter.tags, checkedTags)
  }

  private val tagsObservable: Single<List<Tag>>
    get() {
      val tags = tags
      return if (tags == null || shouldRefreshTags) {
        bookmarkModel.tagsObservable
      } else {
        Single.just(tags)
      }
    }

  open fun saveChanges() {
    if (madeChanges) {
      bookmarkIdsObservable
        .flatMap { bookmarkIds: LongArray ->
          bookmarkModel.updateBookmarkTags(
            bookmarkIds,
            checkedTags,
            bookmarkIds.size == 1
          )
        }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { ignored: Boolean? -> onSaveChangesDone() }
    } else {
      onSaveChangesDone()
    }
  }

  open fun onSaveChangesDone() {
    madeChanges = false
  }

  private val bookmarkIdsObservable: Observable<LongArray>
    /**
     * Get an Observable with the list of bookmark ids that will be tagged.
     *
     * @return the list of bookmark ids to tag
     */
    get() {
      val bookmarkIds = bookmarkIds
      val potentialAyahBookmark = potentialAyahBookmark
      val observable: Observable<LongArray> = if (bookmarkIds != null) {
        // if we already have a list, we just use that
        Observable.just(bookmarkIds)
      } else {
        if (potentialAyahBookmark != null) {
          // if we don't have a bookmark id, we'll add the bookmark and use its id
          bookmarkModel.safeAddBookmark(
            potentialAyahBookmark.sura,
            potentialAyahBookmark.ayah,
            potentialAyahBookmark.page
          ).map { bookmarkId: Long -> longArrayOf(bookmarkId) }
        } else {
          Observable.just(longArrayOf())
        }
      }
      return observable
    }

  fun toggleTag(id: Long): Boolean {
    var result = false

    if (id > 0) {
      if (checkedTags.contains(id)) {
        checkedTags.remove(id)
      } else {
        checkedTags.add(id)
        result = true
      }
      setMadeChanges()
    } else {
      dialog?.showAddTagDialog()
    }
    return result
  }

  fun setMadeChanges() {
    madeChanges = true
    if (saveImmediate) {
      saveChanges()
    }
  }

  private val bookmarkTagIdsObservable: Single<List<Long>>
    get() {
      val ayahBookmark = potentialAyahBookmark
      val bookmarkId = if (ayahBookmark != null) {
        bookmarkModel.getBookmarkId(
          ayahBookmark.sura,
          ayahBookmark.ayah,
          ayahBookmark.page
        )
      } else {
        val ids = bookmarkIds
        Single.just(
          if (ids != null && ids.size == 1) ids[0] else 0
        )
      }
      return bookmarkModel.getBookmarkTagIds(bookmarkId)
        .defaultIfEmpty(ArrayList())
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
