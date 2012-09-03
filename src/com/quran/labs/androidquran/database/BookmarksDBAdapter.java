package com.quran.labs.androidquran.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import static com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarkCategoriesTable;
import static com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarksTable;

public class BookmarksDBAdapter {

	private SQLiteDatabase mDb;
	private BookmarksDBHelper mDbHelper;
	
	public BookmarksDBAdapter(Context context) {
		mDbHelper = new BookmarksDBHelper(context);
	}

	public void open() throws SQLException {
      if (mDb == null){
		   mDb = mDbHelper.getWritableDatabase();
      }
	}

	public void close() {
      if (mDb != null){
		   mDbHelper.close();
         mDb = null;
      }
	}

   public List<Bookmark> getBookmarks(){
      if (mDb == null){
         open();
         if (mDb == null){ return null; }
      }

      List<Bookmark> bookmarks = null;
      Cursor cursor = mDb.query(BookmarksTable.TABLE_NAME,
              null, null, null, null, null,
              BookmarksTable.ADDED_DATE + " DESC");
      if (cursor != null){
         bookmarks = new ArrayList<Bookmark>();
         while (cursor.moveToNext()){
            long id = cursor.getLong(0);
            Integer sura = cursor.getInt(1);
            Integer ayah = cursor.getInt(2);
            int page = cursor.getInt(3);
            Long category = cursor.getLong(4);
            long time = cursor.getLong(5);

            if (sura == 0 || ayah == 0){
               sura = null;
               ayah = null;
            }

            if (category == 0){ category = null; }

            Bookmark bookmark = new Bookmark(id, sura, ayah, page,
                    category, time);
            bookmarks.add(bookmark);
         }
         cursor.close();
      }
      return bookmarks;
   }

   public boolean isPageBookmarked(int page){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      boolean result = false;
      Cursor cursor = mDb.query(BookmarksTable.TABLE_NAME,
              null, BookmarksTable.PAGE + "=" + page + " AND " +
              BookmarksTable.SURA + " IS NULL AND " +
              BookmarksTable.AYAH + " IS NULL", null, null, null, null);
      if (cursor != null && cursor.getCount() > 0){
         result = true;
      }
      return result;
   }

   public long addBookmark(int page, long category){
      return addBookmark(null, null, page, category);
   }

   public long addBookmark(int sura, int ayah, int page, Long category){
      return addBookmark(sura, ayah, page, category);
   }

   public long addBookmark(Integer sura, Integer ayah,
                           int page, Long category){
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }

      if (category != null && category == 0){
         category = null;
      }

      ContentValues values = new ContentValues();
      values.put(BookmarksTable.SURA, sura);
      values.put(BookmarksTable.AYAH, ayah);
      values.put(BookmarksTable.PAGE, page);
      values.put(BookmarksTable.CATEGORY_ID, category);
      return mDb.insert(BookmarksTable.TABLE_NAME, null, values);
   }

   public boolean removeBookmark(long bookmarkId){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      return mDb.delete(BookmarksTable.TABLE_NAME,
              BookmarksTable.ID + "=" + bookmarkId, null) == 1;
   }

   public List<BookmarkCategory> getCategories(){
      if (mDb == null){
         open();
         if (mDb == null){ return null; }
      }

      List<BookmarkCategory> categories = null;
      Cursor cursor = mDb.query(BookmarkCategoriesTable.TABLE_NAME,
              null, null, null, null, null,
              BookmarkCategoriesTable.NAME + " ASC");
      if (cursor != null){
         categories = new ArrayList<BookmarkCategory>();
         while (cursor.moveToNext()){
            long id = cursor.getLong(0);
            String name = cursor.getString(1);
            String description = cursor.getString(2);

            BookmarkCategory category = new BookmarkCategory(
                    id, name, description);
            categories.add(category);
         }
         cursor.close();
      }
      return categories;
   }

   public long addCategory(String name, String description){
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }

      ContentValues values = new ContentValues();
      values.put(BookmarkCategoriesTable.NAME, name);
      values.put(BookmarkCategoriesTable.DESCRIPTION, description);
      return mDb.insert(BookmarkCategoriesTable.TABLE_NAME, null, values);
   }

   public boolean updateCategory(long id, String name, String description){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      ContentValues values = new ContentValues();
      values.put(BookmarkCategoriesTable.ID, id);
      values.put(BookmarkCategoriesTable.NAME, name);
      values.put(BookmarkCategoriesTable.DESCRIPTION, description);
      return 1 == mDb.update(BookmarkCategoriesTable.TABLE_NAME, values,
              BookmarkCategoriesTable.ID + "=" + id, null);
   }

   public boolean removeCategory(long categoryId, boolean removeBookmarks){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      boolean removed = mDb.delete(BookmarkCategoriesTable.TABLE_NAME,
              BookmarkCategoriesTable.ID + "=" + categoryId, null) == 1;
      if (removeBookmarks){
         mDb.delete(BookmarksTable.TABLE_NAME,
                 BookmarksTable.CATEGORY_ID + "=" + categoryId, null);
      }
      else {
         mDb.rawQuery("UPDATE " + BookmarksTable.TABLE_NAME + " SET " +
                 BookmarksTable.CATEGORY_ID + " = NULL WHERE " +
                 BookmarksTable.CATEGORY_ID + " = " + categoryId, null);
      }

      return removed;
   }
   
   public static class BookmarkCategory {
      public long mId;
      public String mName;
      public String mDescription;
      
      public BookmarkCategory(long id, String name, String description) {
         mId = id;
         mName = name;
         mDescription = description;
      }

      @Override
      public String toString() {
         return mName == null? super.toString() : mName;
      }
   }
   
	public static class Bookmark {
		public long mId;
      public Integer mSura;
      public Integer mAyah;
      public int mPage;
      public long mTimestamp;
      public Long mCategoryId;

      public Bookmark(long id, Integer sura, Integer ayah, int page,
                      Long categoryId, long timestamp){
         mId = id;
         mSura = sura;
         mAyah = ayah;
         mPage = page;
         mCategoryId = categoryId;
         mTimestamp = timestamp;
      }
	}
}