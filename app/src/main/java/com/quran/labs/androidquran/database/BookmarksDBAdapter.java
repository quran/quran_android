package com.quran.labs.androidquran.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.quran.data.model.bookmark.Bookmark;
import com.quran.data.model.bookmark.BookmarkData;
import com.quran.data.model.bookmark.RecentPage;
import com.quran.data.model.bookmark.Tag;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarkTagTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarksTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.LastPagesTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.TagsTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import timber.log.Timber;

public class BookmarksDBAdapter {

  public static final int SORT_DATE_ADDED = 0;
  public static final int SORT_LOCATION = 1;
  private static final int SORT_ALPHABETICAL = 2;

  private SQLiteDatabase db;
  Context context;

  public BookmarksDBAdapter(Context context) {
    BookmarksDBHelper dbHelper = BookmarksDBHelper.getInstance(context);
    db = dbHelper.getWritableDatabase();
    this.context = context;
  }

  @NonNull
  public List<Bookmark> getBookmarkedAyahsOnPage(int page) {
    return getBookmarks(SORT_LOCATION, page);
  }

  @NonNull
  public List<Bookmark> getBookmarks(int sortOrder) {
    return getBookmarks(sortOrder, null);
  }

  @NonNull
  private List<Bookmark> getBookmarks(int sortOrder, Integer pageFilter) {
    String orderBy;
    switch (sortOrder) {
      case SORT_LOCATION:
        orderBy = BookmarksTable.PAGE + " ASC, "
            + BookmarksTable.SURA + " ASC, " + BookmarksTable.AYAH + " ASC";
        break;
      case SORT_DATE_ADDED:
      default:
        orderBy = BookmarksTable.TABLE_NAME + "." + BookmarksTable.ADDED_DATE + " DESC";
    }

    List<Bookmark> bookmarks = new ArrayList<>();
    StringBuilder queryBuilder = new StringBuilder(BookmarksDBHelper.QUERY_BOOKMARKS);
    if (pageFilter != null) {
      queryBuilder.append(" WHERE ")
          .append(BookmarksTable.PAGE)
          .append(" = ").append(pageFilter).append(" AND ")
          .append(BookmarksTable.SURA).append(" IS NOT NULL").append(" AND ")
          .append(BookmarksTable.AYAH).append(" IS NOT NULL");
    }
    queryBuilder.append(" ORDER BY ").append(orderBy);

    Cursor cursor = null;
    try {
      cursor = db.rawQuery(queryBuilder.toString(), null);
      if (cursor != null) {
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
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }

    return bookmarks;
  }

  @NonNull
  public List<RecentPage> getRecentPages() {
    List<RecentPage> recents = new ArrayList<>();
    Cursor cursor = null;
    try {
      String[] tableColumns = new String[] { LastPagesTable.ID, LastPagesTable.PAGE, "strftime('%s', " + LastPagesTable.ADDED_DATE + ")" };
      cursor = db.query(LastPagesTable.TABLE_NAME, tableColumns, null, null, null, null,
          LastPagesTable.ADDED_DATE + " DESC");
      if (cursor != null) {
        while (cursor.moveToNext()) {
          recents.add(new RecentPage(cursor.getInt(1), cursor.getLong(2)));
        }
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return recents;
  }

  public void replaceRecentRangeWithPage(int deleteRangeStart, int deleteRangeEnd, int page) {
    int count = db.delete(LastPagesTable.TABLE_NAME,
        LastPagesTable.PAGE + " >= ? AND " + LastPagesTable.PAGE + " <= ?",
        new String[] { String.valueOf(deleteRangeStart), String.valueOf(deleteRangeEnd) });
    // avoid doing a delete if this delete caused us to remove any rows
    boolean shouldDelete = count == 0;
    addRecentPage(page, shouldDelete);
  }

  public void addRecentPage(int page) {
    addRecentPage(page, true);
  }

  private void addRecentPage(int page, boolean shouldDelete) {
    ContentValues contentValues = new ContentValues();
    contentValues.put(LastPagesTable.PAGE, page);
    if (db.replace(LastPagesTable.TABLE_NAME, null, contentValues) != -1 && shouldDelete) {
      db.execSQL("DELETE FROM " + LastPagesTable.TABLE_NAME + " WHERE " +
          LastPagesTable.ID + " NOT IN( SELECT " + LastPagesTable.ID + " FROM " +
          LastPagesTable.TABLE_NAME + " ORDER BY " + LastPagesTable.ADDED_DATE + " DESC LIMIT ? )",
          new Object[] { Constants.MAX_RECENT_PAGES });
    }
  }

  @NonNull
  public List<Long> getBookmarkTagIds(long bookmarkId) {
    List<Long> bookmarkTags = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = db.query(BookmarkTagTable.TABLE_NAME,
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

  public long getBookmarkId(Integer sura, Integer ayah, int page) {
    Cursor cursor = null;
    try {
      cursor = db.query(BookmarksTable.TABLE_NAME,
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

  public void bulkDelete(List<Long> tagIds, List<Long> bookmarkIds, List<Pair<Long, Long>> untag) {
    db.beginTransaction();
    try {
      // cache and re-use for tags and bookmarks
      String[] param = new String[1];

      // remove tags
      for (int i = 0, tagIdsSize = tagIds.size(); i < tagIdsSize; i++) {
        long tagId = tagIds.get(i);
        param[0] = String.valueOf(tagId);
        db.delete(TagsTable.TABLE_NAME, TagsTable.ID + " = ?", param);
        db.delete(BookmarkTagTable.TABLE_NAME, BookmarkTagTable.TAG_ID + " = ?", param);
      }

      // remove bookmarks
      for (int i = 0, bookmarkIdsSize = bookmarkIds.size(); i < bookmarkIdsSize; i++) {
        long bookmarkId = bookmarkIds.get(i);
        param[0] = String.valueOf(bookmarkId);
        db.delete(BookmarksTable.TABLE_NAME, BookmarksTable.ID + " = ?", param);
        db.delete(BookmarkTagTable.TABLE_NAME, BookmarkTagTable.BOOKMARK_ID + " = ?", param);
      }

      // untag whatever is being untagged
      String[] params = new String[2];
      for (int i = 0, untagSize = untag.size(); i < untagSize; i++) {
        Pair<Long, Long> item = untag.get(i);
        params[0] = String.valueOf(item.first);
        params[1] = String.valueOf(item.second);
        db.delete(BookmarkTagTable.TABLE_NAME,
            BookmarkTagTable.BOOKMARK_ID + " = ? AND " + BookmarkTagTable.TAG_ID + " = ?", params);
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  public void updateBookmarks(List<Bookmark> bookmarks) {
    db.beginTransaction();
    try {
      final ContentValues contentValues = new ContentValues();
      for (int i = 0, size = bookmarks.size(); i < size; i++) {
        final Bookmark bookmark = bookmarks.get(i);

        contentValues.clear();
        contentValues.put(BookmarksTable.SURA, bookmark.getSura());
        contentValues.put(BookmarksTable.AYAH, bookmark.getAyah());
        db.update(BookmarksTable.TABLE_NAME, contentValues,
            BookmarksTable.ID + "=" + bookmark.getId(), null);
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
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
    return db.insert(BookmarksTable.TABLE_NAME, null, values);
  }

  public void removeBookmark(long bookmarkId) {
    db.delete(BookmarkTagTable.TABLE_NAME,
        BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId, null);
    db.delete(BookmarksTable.TABLE_NAME,
        BookmarksTable.ID + "=" + bookmarkId, null);
  }

  @NonNull
  public List<Tag> getTags() {
    return getTags(SORT_ALPHABETICAL);
  }

  @NonNull
  private List<Tag> getTags(int sortOrder) {
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
      cursor = db.query(TagsTable.TABLE_NAME,
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
    final long existingTag = haveMatchingTag(name);
    if (existingTag == -1) {
      ContentValues values = new ContentValues();
      values.put(TagsTable.NAME, name);
      return db.insert(TagsTable.TABLE_NAME, null, values);
    } else {
      return existingTag;
    }
  }

  private long haveMatchingTag(String name) {
    Cursor cursor = null;
    try {
      cursor = db.query(TagsTable.TABLE_NAME, new String[]{ TagsTable.ID },
          TagsTable.NAME + " = ?",
          new String[]{ name },
          null,
          null,
          null,
          "1");
      if (cursor != null && cursor.moveToNext()) {
        return cursor.getLong(0);
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return -1;
  }

  public boolean updateTag(long id, String newName) {
    final long existingTag = haveMatchingTag(newName);
    if (existingTag == -1) {
      ContentValues values = new ContentValues();
      values.put(TagsTable.ID, id);
      values.put(TagsTable.NAME, newName);
      return 1 == db.update(TagsTable.TABLE_NAME, values,
          TagsTable.ID + "=" + id, null);
    } else {
      return false;
    }
  }

  /**
   * Tag a list of bookmarks with a list of tags.
   * @param bookmarkIds the list of bookmark ids to tag.
   * @param tagIds the tags to tag those bookmarks with.
   * @param deleteNonTagged whether or not we should delete all tags not in tagIds from those
   *                        bookmarks or not.
   * @return a boolean denoting success
   */
  public boolean tagBookmarks(long[] bookmarkIds, Set<Long> tagIds, boolean deleteNonTagged) {
    db.beginTransaction();
    try {
      // if we're literally replacing the tags such that only tagIds are tagged, then we need to
      // remove all tags from the various bookmarks first.
      if (deleteNonTagged) {
        for (long bookmarkId : bookmarkIds) {
          db.delete(BookmarkTagTable.TABLE_NAME,
              BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId, null);
        }
      }

      for (Long tagId : tagIds) {
        for (long bookmarkId : bookmarkIds) {
          ContentValues values = new ContentValues();
          values.put(BookmarkTagTable.BOOKMARK_ID, bookmarkId);
          values.put(BookmarkTagTable.TAG_ID, tagId);
          db.replace(BookmarkTagTable.TABLE_NAME, null, values);
        }
      }
      db.setTransactionSuccessful();
      return true;
    } catch (Exception e) {
      Timber.d(e, "exception in tagBookmark");
      return false;
    } finally {
      db.endTransaction();
    }
  }

  public boolean importBookmarks(BookmarkData data) {
    boolean result = true;
    db.beginTransaction();
    try {
      db.delete(BookmarksTable.TABLE_NAME, null, null);
      db.delete(BookmarkTagTable.TABLE_NAME, null, null);
      db.delete(TagsTable.TABLE_NAME, null, null);

      ContentValues values = new ContentValues();

      List<Tag> tags = data.getTags();
      int tagSize = tags.size();
      Timber.d("importing %d tags...", tagSize);

      for (int i = 0; i < tagSize; i++) {
        Tag tag = tags.get(i);
        values.clear();
        values.put(TagsTable.NAME, tag.getName());
        values.put(TagsTable.ID, tag.getId());
        db.insert(TagsTable.TABLE_NAME, null, values);
      }

      List<Bookmark> bookmarks = data.getBookmarks();
      int bookmarkSize = bookmarks.size();
      Timber.d("importing %d bookmarks...", bookmarkSize);

      for (int i = 0; i < bookmarkSize; i++) {
        Bookmark bookmark = bookmarks.get(i);

        values.clear();
        values.put(BookmarksTable.ID, bookmark.getId());
        values.put(BookmarksTable.SURA, bookmark.getSura());
        values.put(BookmarksTable.AYAH, bookmark.getAyah());
        values.put(BookmarksTable.PAGE, bookmark.getPage());
        values.put(BookmarksTable.ADDED_DATE, bookmark.getTimestamp());
        db.insert(BookmarksTable.TABLE_NAME, null, values);

        List<Long> tagIds = bookmark.getTags();
        for (int t = 0; t < tagIds.size(); t++) {
          values.clear();
          values.put(BookmarkTagTable.BOOKMARK_ID, bookmark.getId());
          values.put(BookmarkTagTable.TAG_ID, tagIds.get(t));
          db.insert(BookmarkTagTable.TABLE_NAME, null, values);
        }
      }

      Timber.d("import successful!");
      db.setTransactionSuccessful();
    } catch (Exception e) {
      Timber.e(e, "Failed to import data");
      result = false;
    } finally {
      db.endTransaction();
    }
    return result;
  }
}
