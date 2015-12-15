package com.quran.labs.androidquran.database;

import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarkTagTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarksTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.TagsTable;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

public class BookmarksDBAdapter {

  public static final int SORT_DATE_ADDED = 0;
  public static final int SORT_LOCATION = 1;
  public static final int SORT_ALPHABETICAL = 2;

  private SQLiteDatabase mDb;

  public BookmarksDBAdapter(Context context) {
    BookmarksDBHelper dbHelper = BookmarksDBHelper.getInstance(context);
    mDb = dbHelper.getWritableDatabase();
  }

  public List<Bookmark> getBookmarkedAyahsOnPage(int page) {
    return getBookmarks(SORT_LOCATION, page);
  }

  public List<Bookmark> getBookmarks(int sortOrder) {
    return getBookmarks(sortOrder, null);
  }

  public List<Bookmark> getBookmarks(int sortOrder, Integer pageFilter) {
    String orderBy;
    switch (sortOrder) {
      case SORT_LOCATION:
        orderBy = BookmarksTable.PAGE + " ASC, "
            + BookmarksTable.SURA + " ASC, " + BookmarksTable.AYAH + " ASC";
        break;
      case SORT_DATE_ADDED:
      default:
        orderBy = BookmarksTable.ADDED_DATE + " DESC";
        break;
    }

    List<Bookmark> bookmarks = null;
    StringBuilder queryBuilder = new StringBuilder(BookmarksDBHelper.QUERY_BOOKMARKS);
    if (pageFilter != null) {
      queryBuilder.append(" WHERE ")
          .append(BookmarksTable.PAGE)
          .append(" = ").append(pageFilter).append(" AND ")
          .append(BookmarksTable.SURA).append(" IS NOT NULL").append(" AND ")
          .append(BookmarksTable.AYAH).append(" IS NOT NULL");
    }
    queryBuilder.append(" ORDER BY ").append(orderBy);

    Cursor cursor = mDb.rawQuery(queryBuilder.toString(), null);

    if (cursor != null) {
      bookmarks = new ArrayList<>();
      long lastId = -1;
      Bookmark lastBookmark = null;
      List<Long> tagIds = new ArrayList<>();
      while (cursor.moveToNext()) {
        long id = cursor.getLong(0);
        Integer sura = cursor.getInt(1);
        Integer ayah = cursor.getInt(2);
        int page = cursor.getInt(3);
        long time = cursor.getLong(4);
        long tagId = cursor.getLong(5);

        if (sura == 0 || ayah == 0) {
          sura = null;
          ayah = null;
        }

        if (lastId != id) {
          if (lastBookmark != null) {
            bookmarks.add(lastBookmark.withTags(tagIds));
          }
          tagIds.clear();
          lastBookmark = new Bookmark(id, sura, ayah, page, time);
          lastId = id;
        }

        if (tagId > 0) {
          tagIds.add(tagId);
        }
      }

      if (lastBookmark != null) {
        bookmarks.add(lastBookmark.withTags(tagIds));
      }
      cursor.close();
    }
    return bookmarks;
  }

  @NonNull
  public List<Long> getBookmarkTagIds(long bookmarkId) {
    List<Long> bookmarkTags = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = mDb.query(BookmarkTagTable.TABLE_NAME,
          new String[]{BookmarkTagTable.TAG_ID},
          BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId,
          null, null, null, BookmarkTagTable.TAG_ID + " ASC");
      if (cursor != null) {
        while (cursor.moveToNext()) {
          bookmarkTags.add(cursor.getLong(0));
        }
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return bookmarkTags;
  }

  public boolean isPageBookmarked(int page) {
    return getBookmarkId(null, null, page) >= 0;
  }

  public long getBookmarkId(Integer sura, Integer ayah, int page) {
    Cursor cursor = null;
    try {
      cursor = mDb.query(BookmarksTable.TABLE_NAME,
          null, BookmarksTable.PAGE + "=" + page + " AND " +
              BookmarksTable.SURA + (sura == null ? " IS NULL" : "=" + sura) +
              " AND " + BookmarksTable.AYAH +
              (ayah == null ? " IS NULL" : "=" + ayah), null, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(0);
      }
    } catch (Exception e) {
      // swallow the error for now
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return -1;
  }

  public long addBookmarkIfNotExists(Integer sura, Integer ayah, int page) {
    long bookmarkId = getBookmarkId(sura, ayah, page);
    if (bookmarkId < 0) {
      bookmarkId = addBookmark(sura, ayah, page);
    }
    return bookmarkId;
  }

  public long addBookmark(Integer sura, Integer ayah, int page) {
    ContentValues values = new ContentValues();
    values.put(BookmarksTable.SURA, sura);
    values.put(BookmarksTable.AYAH, ayah);
    values.put(BookmarksTable.PAGE, page);
    return mDb.insert(BookmarksTable.TABLE_NAME, null, values);
  }

  public boolean removeBookmark(long bookmarkId) {
    clearBookmarkTags(bookmarkId);
    return mDb.delete(BookmarksTable.TABLE_NAME,
        BookmarksTable.ID + "=" + bookmarkId, null) == 1;
  }

  @NonNull
  public List<Tag> getTags() {
    return getTags(SORT_ALPHABETICAL);
  }

  @NonNull
  public List<Tag> getTags(int sortOrder) {
    String orderBy;
    switch (sortOrder) {
      case SORT_DATE_ADDED:
        orderBy = TagsTable.ADDED_DATE + " DESC";
        break;
      case SORT_ALPHABETICAL:
      default:
        orderBy = TagsTable.NAME + " ASC";
        break;
    }
    List<Tag> tags = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = mDb.query(TagsTable.TABLE_NAME,
          null, null, null, null, null, orderBy);
      if (cursor != null) {
        while (cursor.moveToNext()) {
          long id = cursor.getLong(0);
          String name = cursor.getString(1);
          Tag tag = new Tag(id, name);
          tags.add(tag);
        }
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return tags;
  }

  public long addTag(String name) {
    ContentValues values = new ContentValues();
    values.put(TagsTable.NAME, name);
    return mDb.insert(TagsTable.TABLE_NAME, null, values);
  }

  public boolean updateTag(long id, String newName) {
    ContentValues values = new ContentValues();
    values.put(TagsTable.ID, id);
    values.put(TagsTable.NAME, newName);
    return 1 == mDb.update(TagsTable.TABLE_NAME, values,
        TagsTable.ID + "=" + id, null);
  }

  public boolean removeTag(long tagId) {
    boolean removed = mDb.delete(TagsTable.TABLE_NAME,
        TagsTable.ID + "=" + tagId, null) == 1;
    if (removed) {
      mDb.delete(BookmarkTagTable.TABLE_NAME,
          BookmarkTagTable.TAG_ID + "=" + tagId, null);
    }

    return removed;
  }

  public void tagBookmarks(long[] bookmarkIds, Set<Long> tagIds) {
    mDb.beginTransaction();
    try {
      for (Long tagId : tagIds) {
        for (long bookmarkId : bookmarkIds) {
          ContentValues values = new ContentValues();
          values.put(BookmarkTagTable.BOOKMARK_ID, bookmarkId);
          values.put(BookmarkTagTable.TAG_ID, tagId);
          mDb.replace(BookmarkTagTable.TABLE_NAME, null, values);
        }
      }
      mDb.setTransactionSuccessful();
    } catch (Exception e) {
      Timber.d("exception in tagBookmark",e);
    } finally {
      mDb.endTransaction();
    }
  }

  public void tagBookmark(long bookmarkId, Set<Long> tagIds) {
    mDb.beginTransaction();
    try {
      // delete all tags for this bookmark, then re-add the relevant ones - since we're in a
      // transaction, we should be okay.
      mDb.delete(BookmarkTagTable.TABLE_NAME,
          BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId, null);

      for (Long tagId : tagIds) {
        ContentValues values = new ContentValues();
        values.put(BookmarkTagTable.BOOKMARK_ID, bookmarkId);
        values.put(BookmarkTagTable.TAG_ID, tagId);
        mDb.replace(BookmarkTagTable.TABLE_NAME, null, values);
      }
      mDb.setTransactionSuccessful();
    } catch (Exception e) {
      Timber.d("exception in tagBookmark",e);
    } finally {
      mDb.endTransaction();
    }
  }

  public void untagBookmark(long bookmarkId, long tagId) {
    mDb.delete(BookmarkTagTable.TABLE_NAME,
        BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId + " AND "
            + BookmarkTagTable.TAG_ID + "=" + tagId, null);
  }

  public int clearBookmarkTags(long bookmarkId) {
    return mDb.delete(BookmarkTagTable.TABLE_NAME,
        BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId, null);
  }

  public boolean importBookmarks(BookmarkData data) {
    boolean result = true;
    mDb.beginTransaction();
    try {
      mDb.delete(BookmarksTable.TABLE_NAME, null, null);
      mDb.delete(BookmarkTagTable.TABLE_NAME, null, null);
      mDb.delete(TagsTable.TABLE_NAME, null, null);

      ContentValues values = new ContentValues();

      List<Tag> tags = data.getTags();
      for (int i = 0, tagCount = tags.size(); i < tagCount; i++) {
        Tag tag = tags.get(i);
        values.clear();
        values.put(TagsTable.NAME, tag.name);
        values.put(TagsTable.ID, tag.id);
        mDb.insert(TagsTable.TABLE_NAME, null, values);
      }

      List<Bookmark> bookmarks = data.getBookmarks();
      for (int i = 0, bookmarkCount = bookmarks.size(); i < bookmarkCount; i++) {
        Bookmark bookmark = bookmarks.get(i);

        values.clear();
        values.put(BookmarksTable.ID, bookmark.id);
        values.put(BookmarksTable.SURA, bookmark.sura);
        values.put(BookmarksTable.AYAH, bookmark.ayah);
        values.put(BookmarksTable.PAGE, bookmark.page);
        values.put(BookmarksTable.ADDED_DATE, bookmark.timestamp);
        mDb.insert(BookmarksTable.TABLE_NAME, null, values);

        List<Long> tagIds = bookmark.tags;
        for (int t = 0; t < tagIds.size(); t++) {
          values.clear();
          values.put(BookmarkTagTable.BOOKMARK_ID, bookmark.id);
          values.put(BookmarkTagTable.TAG_ID, tagIds.get(t));
          mDb.insert(BookmarkTagTable.TABLE_NAME, null, values);
        }
      }

      mDb.setTransactionSuccessful();
    } catch (Exception e) {
      result = false;
    } finally {
      mDb.endTransaction();
    }

    return result;
  }
}
