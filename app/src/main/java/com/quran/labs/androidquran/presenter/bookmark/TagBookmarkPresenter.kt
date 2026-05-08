package com.quran.labs.androidquran.presenter.bookmark

import com.quran.data.dao.BookmarksDao
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
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
  private val checkedTags = HashSet<Long>()

  private var dialog: TagBookmarkDialog? = null

  private var tags: List<Tag>? = null
  private var bookmarkIds: LongArray? = null
  private var madeChanges = false
  private var saveImmediate = false
  private var shouldRefreshTags = false
  private var potentialAyahBookmark: Bookmark? = null
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
    Single.zip(
      tagsObservable,
      bookmarkTagIdsObservable
    ) { first: List<Tag>, second: List<Long> ->
      Pair(first, second)
    }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe({ data: Pair<List<Tag>, List<Long>> ->
        this.onRefreshedData(data)
      }, { throwable ->
        Timber.e(throwable, "Unable to refresh bookmark tags")
      })
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
        Single.fromCallable { runBlocking { bookmarksDao.tags() } }
      } else {
        Single.just(tags)
      }
    }

  open fun saveChanges() {
    if (madeChanges) {
      Single.fromCallable {
        runBlocking {
          val ayahBookmark = potentialAyahBookmark
          if (ayahBookmark != null) {
            bookmarksDao.updateAyahBookmarkTags(
              suraAyah = SuraAyah(requireNotNull(ayahBookmark.sura), requireNotNull(ayahBookmark.ayah)),
              page = ayahBookmark.page,
              tagIds = checkedTags,
              deleteNonTagged = true
            )
          } else {
            val bookmarkIds = bookmarkIds ?: longArrayOf()
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

  open fun onSaveChangesDone() {
    madeChanges = false
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
      return Single.fromCallable {
        runBlocking {
          val ayahBookmark = potentialAyahBookmark
          if (ayahBookmark != null) {
            bookmarksDao.getAyahBookmarkTagIds(
              SuraAyah(requireNotNull(ayahBookmark.sura), requireNotNull(ayahBookmark.ayah))
            )
          } else {
            val ids = bookmarkIds
            if (ids != null && ids.size == 1) {
              bookmarksDao.getBookmarkTagIds(ids[0])
            } else {
              emptyList()
            }
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
