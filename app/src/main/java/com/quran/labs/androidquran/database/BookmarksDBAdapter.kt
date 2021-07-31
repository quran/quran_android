package com.quran.labs.androidquran.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.util.Pair
import com.quran.data.model.bookmark.Bookmark
import com.quran.data.model.bookmark.BookmarkData
import com.quran.data.model.bookmark.RecentPage
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.database.BookmarksDBHelper.*
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarksTable
import com.quran.labs.androidquran.database.BookmarksDBHelper.LastPagesTable
import timber.log.Timber
import java.lang.Exception
import java.lang.StringBuilder
import java.util.ArrayList

class BookmarksDBAdapter(context: Context) {

  companion object {
    const val SORT_DATE_ADDED = 0
    const val SORT_LOCATION = 1
    private const val SORT_ALPHABETICAL = 2
  }

  private val db: SQLiteDatabase

  init {
    val dbHelper = BookmarksDBHelper.getInstance(context)
    db = dbHelper.writableDatabase
  }

  fun getBookmarkedAyahsOnPage(page: Int): List<Bookmark> {
    return getBookmarks(SORT_LOCATION, page)
  }

  fun getBookmarks(sortOrder: Int): List<Bookmark> {
    return getBookmarks(sortOrder, null)
  }

  private fun getBookmarks(sortOrder: Int, pageFilter: Int?): List<Bookmark> {
    val orderBy: String = when (sortOrder) {
      SORT_LOCATION -> (BookmarksTable.PAGE + " ASC, "
          + BookmarksTable.SURA + " ASC, " + BookmarksTable.AYAH + " ASC")
      SORT_DATE_ADDED -> BookmarksTable.TABLE_NAME + "." + BookmarksTable.ADDED_DATE + " DESC"
      else -> BookmarksTable.TABLE_NAME + "." + BookmarksTable.ADDED_DATE + " DESC"
    }

    val bookmarks: MutableList<Bookmark> = ArrayList()
    val queryBuilder = StringBuilder(BookmarksDBHelper.QUERY_BOOKMARKS)
    if (pageFilter != null) {
      queryBuilder.append(" WHERE ")
        .append(BookmarksTable.PAGE)
        .append(" = ").append(pageFilter).append(" AND ")
        .append(BookmarksTable.SURA).append(" IS NOT NULL").append(" AND ")
        .append(BookmarksTable.AYAH).append(" IS NOT NULL")
    }
    queryBuilder.append(" ORDER BY ").append(orderBy)

    var cursor: Cursor? = null
    try {
      cursor = db.rawQuery(queryBuilder.toString(), null)
      if (cursor != null) {
        var lastId: Long = -1
        var lastBookmark: Bookmark? = null
        val tagIds: MutableList<Long> = ArrayList()
        while (cursor.moveToNext()) {
          val id = cursor.getLong(0)
          var sura: Int? = cursor.getInt(1)
          var ayah: Int? = cursor.getInt(2)
          val page = cursor.getInt(3)
          val time = cursor.getLong(4)
          val tagId = cursor.getLong(5)

          if (sura == 0 || ayah == 0) {
            sura = null
            ayah = null
          }

          if (lastId != id) {
            if (lastBookmark != null) {
              bookmarks.add(lastBookmark.withTags(tagIds))
            }
            tagIds.clear()
            lastBookmark = Bookmark(id, sura, ayah, page, time)
            lastId = id
          }

          if (tagId > 0) {
            tagIds.add(tagId)
          }
        }

        lastBookmark?.let { bookmarks.add(it.withTags(tagIds)) }
      }
    } finally {
      DatabaseUtils.closeCursor(cursor)
    }

    return bookmarks
  }

  fun getRecentPages(): List<RecentPage> {
      val recents: MutableList<RecentPage> = ArrayList()
      var cursor: Cursor? = null
      try {
        val tableColumns = arrayOf(LastPagesTable.ID, LastPagesTable.PAGE, "strftime('%s', " + LastPagesTable.ADDED_DATE + ")")
        cursor = db.query(LastPagesTable.TABLE_NAME, tableColumns, null, null, null, null,
          LastPagesTable.ADDED_DATE + " DESC")
        if (cursor != null) {
          while (cursor.moveToNext()) {
            recents.add(RecentPage(cursor.getInt(1), cursor.getLong(2)))
          }
        }
      } finally {
        DatabaseUtils.closeCursor(cursor)
      }
      return recents
    }

  fun replaceRecentRangeWithPage(deleteRangeStart: Int, deleteRangeEnd: Int, page: Int) {
    val count = db.delete(LastPagesTable.TABLE_NAME,
      LastPagesTable.PAGE + " >= ? AND " + LastPagesTable.PAGE + " <= ?",
      arrayOf(deleteRangeStart.toString(), deleteRangeEnd.toString()))
    // avoid doing a delete if this delete caused us to remove any rows
    val shouldDelete = count == 0
    addRecentPage(page, shouldDelete)
  }

  fun addRecentPage(page: Int) {
    addRecentPage(page, true)
  }

  private fun addRecentPage(page: Int, shouldDelete: Boolean) {
    val contentValues = ContentValues()
    contentValues.put(LastPagesTable.PAGE, page)
    if (db.replace(LastPagesTable.TABLE_NAME, null, contentValues) != -1L && shouldDelete) {
      db.execSQL("DELETE FROM " + LastPagesTable.TABLE_NAME + " WHERE " +
          LastPagesTable.ID + " NOT IN( SELECT " + LastPagesTable.ID + " FROM " +
          LastPagesTable.TABLE_NAME + " ORDER BY " + LastPagesTable.ADDED_DATE + " DESC LIMIT ? )",
          arrayOf<Any>(Constants.MAX_RECENT_PAGES))
    }
  }

  fun getBookmarkTagIds(bookmarkId: Long): List<Long> {
    val bookmarkTags: MutableList<Long> = ArrayList()
    var cursor: Cursor? = null
    try {
      cursor = db.query(BookmarkTagTable.TABLE_NAME,
        arrayOf(BookmarkTagTable.TAG_ID),
        BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId,
        null, null, null, BookmarkTagTable.TAG_ID + " ASC")
      if (cursor != null) {
        while (cursor.moveToNext()) {
          bookmarkTags.add(cursor.getLong(0))
        }
      }
    } finally {
      DatabaseUtils.closeCursor(cursor)
    }
    return bookmarkTags
  }

  fun getBookmarkId(sura: Int?, ayah: Int?, page: Int): Long {
    var cursor: Cursor? = null
    try {
      cursor = db.query(BookmarksTable.TABLE_NAME,
        null, (BookmarksTable.PAGE + "=" + page + " AND " +
            BookmarksTable.SURA + (if (sura == null) " IS NULL" else "=$sura") +
            " AND " + BookmarksTable.AYAH +
            (if (ayah == null) " IS NULL" else "=$ayah")), null, null, null, null)
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(0)
      }
    } catch (e: Exception) {
      // swallow the error for now
    } finally {
      DatabaseUtils.closeCursor(cursor)
    }
    return -1
  }

  fun bulkDelete(tagIds: List<Long>, bookmarkIds: List<Long>, untag: List<Pair<Long, Long>>) {
    db.beginTransaction()
    try {
      // cache and re-use for tags and bookmarks
      val param = arrayOfNulls<String>(1)

      // remove tags
      for (tagId in tagIds) {
        param[0] = tagId.toString()
        db.delete(TagsTable.TABLE_NAME, TagsTable.ID + " = ?", param)
        db.delete(BookmarkTagTable.TABLE_NAME, BookmarkTagTable.TAG_ID + " = ?", param)
      }

      // remove bookmarks
      for (bookmarkId in bookmarkIds) {
        param[0] = bookmarkId.toString()
        db.delete(BookmarksTable.TABLE_NAME, BookmarksTable.ID + " = ?", param)
        db.delete(BookmarkTagTable.TABLE_NAME, BookmarkTagTable.BOOKMARK_ID + " = ?", param)
      }

      // untag whatever is being untagged
      val params = arrayOfNulls<String>(2)
      for (item in untag) {
        params[0] = item.first.toString()
        params[1] = item.second.toString()
        db.delete(BookmarkTagTable.TABLE_NAME,
          BookmarkTagTable.BOOKMARK_ID + " = ? AND " + BookmarkTagTable.TAG_ID + " = ?", params)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun updateBookmarks(bookmarks: List<Bookmark>) {
    db.beginTransaction()
    try {
      val contentValues = ContentValues()
      for (bookmark in bookmarks) {
        contentValues.clear()
        contentValues.put(BookmarksTable.SURA, bookmark.sura)
        contentValues.put(BookmarksTable.AYAH, bookmark.ayah)
        db.update(BookmarksTable.TABLE_NAME, contentValues,
          BookmarksTable.ID + "=" + bookmark.id, null)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  fun addBookmarkIfNotExists(sura: Int, ayah: Int, page: Int): Long {
    var bookmarkId = getBookmarkId(sura, ayah, page)
    if (bookmarkId < 0) {
      bookmarkId = addBookmark(sura, ayah, page)
    }
    return bookmarkId
  }

  fun addBookmark(sura: Int, ayah: Int, page: Int): Long {
    val values = ContentValues()
    values.put(BookmarksTable.SURA, sura)
    values.put(BookmarksTable.AYAH, ayah)
    values.put(BookmarksTable.PAGE, page)
    return db.insert(BookmarksTable.TABLE_NAME, null, values)
  }

  fun removeBookmark(bookmarkId: Long) {
    db.delete(BookmarkTagTable.TABLE_NAME,
      BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId, null)
    db.delete(BookmarksTable.TABLE_NAME,
      BookmarksTable.ID + "=" + bookmarkId, null)
  }

  fun getTags(): List<Tag> = getTags(SORT_ALPHABETICAL)

  private fun getTags(sortOrder: Int): List<Tag> {
    val orderBy: String = when (sortOrder) {
      SORT_DATE_ADDED -> TagsTable.ADDED_DATE + " DESC"
      SORT_ALPHABETICAL -> TagsTable.NAME + " ASC"
      else -> TagsTable.NAME + " ASC"
    }

    val tags: MutableList<Tag> = ArrayList()
    var cursor: Cursor? = null
    try {
      cursor = db.query(TagsTable.TABLE_NAME,
        null, null, null, null, null, orderBy)
      if (cursor != null) {
        while (cursor.moveToNext()) {
          val id = cursor.getLong(0)
          val name = cursor.getString(1)
          val tag = Tag(id, name)
          tags.add(tag)
        }
      }
    } finally {
      DatabaseUtils.closeCursor(cursor)
    }
    return tags
  }

  fun addTag(name: String): Long {
    val existingTag = haveMatchingTag(name)
    return if (existingTag == -1L) {
      val values = ContentValues()
      values.put(TagsTable.NAME, name)
      db.insert(TagsTable.TABLE_NAME, null, values)
    } else {
      existingTag
    }
  }

  private fun haveMatchingTag(name: String): Long {
    var cursor: Cursor? = null
    try {
      cursor = db.query(TagsTable.TABLE_NAME, arrayOf(TagsTable.ID),
        TagsTable.NAME + " = ?", arrayOf(name),
        null,
        null,
        null,
        "1")
      if (cursor != null && cursor.moveToNext()) {
        return cursor.getLong(0)
      }
    } finally {
      DatabaseUtils.closeCursor(cursor)
    }
    return -1
  }

  fun updateTag(id: Long, newName: String): Boolean {
    val existingTag = haveMatchingTag(newName)
    return if (existingTag == -1L) {
      val values = ContentValues()
      values.put(TagsTable.ID, id)
      values.put(TagsTable.NAME, newName)
      1 == db.update(TagsTable.TABLE_NAME, values,
        TagsTable.ID + "=" + id, null)
    } else {
      false
    }
  }

  /**
   * Tag a list of bookmarks with a list of tags.
   * @param bookmarkIds the list of bookmark ids to tag.
   * @param tagIds the tags to tag those bookmarks with.
   * @param deleteNonTagged whether or not we should delete all tags not in tagIds from those
   * bookmarks or not.
   * @return a boolean denoting success
   */
  fun tagBookmarks(bookmarkIds: LongArray, tagIds: Set<Long>, deleteNonTagged: Boolean): Boolean {
    db.beginTransaction()
    try {
      // if we're literally replacing the tags such that only tagIds are tagged, then we need to
      // remove all tags from the various bookmarks first.
      if (deleteNonTagged) {
        for (bookmarkId in bookmarkIds) {
          db.delete(BookmarkTagTable.TABLE_NAME,
            BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId, null)
        }
      }

      for (tagId: Long in tagIds) {
        for (bookmarkId in bookmarkIds) {
          val values = ContentValues()
          values.put(BookmarkTagTable.BOOKMARK_ID, bookmarkId)
          values.put(BookmarkTagTable.TAG_ID, tagId)
          db.replace(BookmarkTagTable.TABLE_NAME, null, values)
        }
      }
      db.setTransactionSuccessful()
      return true
    } catch (e: Exception) {
      Timber.d(e, "exception in tagBookmark")
      return false
    } finally {
      db.endTransaction()
    }
  }

  fun importBookmarks(data: BookmarkData): Boolean {
    var result = true
    db.beginTransaction()
    try {
      db.delete(BookmarksTable.TABLE_NAME, null, null)
      db.delete(BookmarkTagTable.TABLE_NAME, null, null)
      db.delete(TagsTable.TABLE_NAME, null, null)

      val values = ContentValues()
      val tags = data.tags
      Timber.d("importing %d tags...", tags.size)
      for (tag in tags) {
        values.clear()
        values.put(TagsTable.NAME, tag.name)
        values.put(TagsTable.ID, tag.id)
        db.insert(TagsTable.TABLE_NAME, null, values)
      }

      val bookmarks = data.bookmarks
      Timber.d("importing %d bookmarks...", bookmarks.size)
      for (bookmark in bookmarks) {
        values.clear()
        values.put(BookmarksTable.ID, bookmark.id)
        values.put(BookmarksTable.SURA, bookmark.sura)
        values.put(BookmarksTable.AYAH, bookmark.ayah)
        values.put(BookmarksTable.PAGE, bookmark.page)
        values.put(BookmarksTable.ADDED_DATE, bookmark.timestamp)
        db.insert(BookmarksTable.TABLE_NAME, null, values)

        val tagIds = bookmark.tags
        for (tagId in tagIds) {
          values.clear()
          values.put(BookmarkTagTable.BOOKMARK_ID, bookmark.id)
          values.put(BookmarkTagTable.TAG_ID, tagId)
          db.insert(BookmarkTagTable.TABLE_NAME, null, values)
        }
      }

      Timber.d("import successful!")
      db.setTransactionSuccessful()
    } catch (e: Exception) {
      Timber.e(e, "Failed to import data")
      result = false
    } finally {
      db.endTransaction()
    }
    return result
  }
}
