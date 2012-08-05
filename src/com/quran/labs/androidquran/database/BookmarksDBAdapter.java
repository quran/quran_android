package com.quran.labs.androidquran.database;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBHelper.AyahNotesTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarkMapTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarksTable;

public class BookmarksDBAdapter {

	private SQLiteDatabase db;
	private BookmarksDBHelper dbHelper;
	
	public BookmarksDBAdapter(Context context) {
		this.dbHelper = new BookmarksDBHelper(context);
	}

	// TODO Add error handling for SQLExceptions and such..

	public void open() throws SQLException {
		db = dbHelper.getWritableDatabase();
	}

	public void close() {
		dbHelper.close();
	}
	
	public String getAyahNote(int ayahId) {
	   Cursor cursor = db.query(AyahNotesTable.TABLE_NAME,
	         new String[] {AyahNotesTable.NOTE},
	         AyahNotesTable.AYAH_ID + "=" + ayahId, null, null, null, null);
	   String result = null;
	   if (cursor.moveToFirst()) {
	      result = cursor.getString(0);
	   }
	   cursor.close();
	   return result;
	}
	
	public void saveAyahNote(int ayahId, String note) {
      ContentValues values = new ContentValues();
      values.put(AyahNotesTable.AYAH_ID, ayahId);
      values.put(AyahNotesTable.NOTE, note == null ? "" : note);
	   int rowsChanged = db.update(AyahNotesTable.TABLE_NAME, values,
	         AyahNotesTable.AYAH_ID + "=" + ayahId, null);
	   if (rowsChanged == 0) {
	      db.insert(AyahNotesTable.TABLE_NAME, null, values);
	   }
	}
	
   public void deleteAyahNote(int ayahId) {
      db.delete(AyahNotesTable.TABLE_NAME,
            AyahNotesTable.AYAH_ID + "=" + ayahId, null);
   }
   
	public Bookmark getBookmark(long bookmarkId) {
      Cursor cursor = db.query(BookmarksTable.TABLE_NAME,
            new String[] {BookmarksTable.ID, BookmarksTable.NAME, BookmarksTable.TYPE, BookmarksTable.DESCRIPTION},
				BookmarksTable.ID + "=" + bookmarkId, null, null, null, BookmarksTable.ID);
		Bookmark bookmark = null;
		if (cursor.moveToFirst()) {
         boolean isCollection = cursor.getInt(2) == BookmarksTable.TYPE_COLLECTION ? true : false;
         bookmark = new Bookmark(cursor.getLong(0), cursor.getString(1), isCollection);
         if (!cursor.isNull(3))
            bookmark.description = cursor.getString(3);
		}
		cursor.close();
		return bookmark;
	}
	
	public Map<Long, Bookmark> getBookmarks() {
		Cursor cursor = db.query(BookmarksTable.TABLE_NAME,
				new String[] {BookmarksTable.ID, BookmarksTable.NAME, BookmarksTable.TYPE, BookmarksTable.DESCRIPTION},
				null, null, null, null, BookmarksTable.NAME + " ASC");
		Map<Long, Bookmark> bookmarkList = new LinkedHashMap<Long, Bookmark>();
		while (cursor.moveToNext()) {
		   long id = cursor.getLong(0);
		   boolean isCollection = cursor.getInt(2) == BookmarksTable.TYPE_COLLECTION ? true : false;
			Bookmark bookmark = new Bookmark(cursor.getLong(0), cursor.getString(1), isCollection);
			if (!cursor.isNull(3))
				bookmark.description = cursor.getString(3);
			bookmarkList.put(id, bookmark);
		}
		cursor.close();
		return bookmarkList;
	}
	
	public List<Bookmark> getBookmarksAsList() {
	   return new ArrayList<Bookmark>(getBookmarks().values());
	}

	public void saveBookmark(Bookmark bookmark) {
	   if (bookmark == null) return;
      ContentValues values = new ContentValues();
      values.put(BookmarksTable.NAME, bookmark.name);
      values.put(BookmarksTable.TYPE, bookmark.isCollection ? 1 : 0);
      values.put(BookmarksTable.DESCRIPTION, bookmark.description);
      int rowsUpdated = 0;
	   if (bookmark.id != null) {
	      rowsUpdated = db.update(BookmarksTable.TABLE_NAME, values, BookmarksTable.ID+"="+bookmark.id, null);
	   }
	   if (rowsUpdated < 1) {
	      db.insert(BookmarksTable.TABLE_NAME, null, values);
	   }
	}

	public void deleteBookmark(long bookmarkId) {
	   db.delete(BookmarkMapTable.TABLE_NAME, BookmarkMapTable.BOOKMARK_ID+"="+bookmarkId, null);
		db.delete(BookmarksTable.TABLE_NAME, BookmarksTable.ID+"="+bookmarkId, null);
	}
	
	public void deleteBookmarkMap(long bookmarkMapId) {
	   db.delete(BookmarkMapTable.TABLE_NAME, BookmarkMapTable.ID+"="+bookmarkMapId, null);
	}

	public boolean isBookmarkCollection(long bookmarkId) {
	   boolean result = false;
      Cursor c = db.query(BookmarksTable.TABLE_NAME, new String[] {BookmarksTable.TYPE},
            BookmarksTable.ID+"="+bookmarkId, null, null, null, null);
      if (c.moveToFirst()) {
         result = (c.getInt(0) == BookmarksTable.TYPE_COLLECTION);
      }
      c.close();
      return result;
	}
	
	public long bookmarkPage(int page, long bookmarkId) {
	   int ayahId = QuranInfo.getAyahId(page);
	   return bookmark(ayahId, bookmarkId, true);
	}
	
	public long bookmarkAyah(int ayahId, long bookmarkId) {
	   return bookmark(ayahId, bookmarkId, false);
	}
	
   private long bookmark(int ayahId, long bookmarkId, boolean isPageBookmark) {
      ContentValues values = new ContentValues();
      values.put(BookmarkMapTable.AYAH_ID, ayahId);
      values.put(BookmarkMapTable.BOOKMARK_ID, bookmarkId);
      values.put(BookmarkMapTable.TYPE, isPageBookmark ? 
            BookmarkMapTable.TYPE_PAGE : BookmarkMapTable.TYPE_AYAH);
      if (isBookmarkCollection(bookmarkId)) {
         Cursor c = db.query(BookmarkMapTable.TABLE_NAME,
               new String[] {BookmarkMapTable.ID},
               BookmarkMapTable.AYAH_ID+"="+ayahId+" AND "+BookmarkMapTable.BOOKMARK_ID+"="+bookmarkId,
               null, null, null, null);
         if (c.moveToFirst())
            return c.getLong(0);
         return db.insert(BookmarkMapTable.TABLE_NAME, null, values);
      } else {
         db.delete(BookmarkMapTable.TABLE_NAME, BookmarkMapTable.BOOKMARK_ID+"="+bookmarkId, null);
         return db.insert(BookmarkMapTable.TABLE_NAME, null, values);
      }
   }

   public boolean isAyahBookmarked(int sura, int ayah) {
      int ayahId = QuranInfo.getAyahId(sura, ayah);
      return isBookmarked(ayahId, BookmarkMapTable.TYPE_AYAH);
   }
   
   public boolean isPageBookmarked(int page){
      if (page < Constants.PAGES_FIRST || page > Constants.PAGES_LAST) {
         return false;
      }
      int ayahId = QuranInfo.getAyahId(page);
      return isBookmarked(ayahId, BookmarkMapTable.TYPE_PAGE);
   }
   
	private boolean isBookmarked(int ayahId, int bookmarkType){
	   boolean result = false;
	   Cursor cursor = db.query(BookmarkMapTable.TABLE_NAME,
	         new String[] {BookmarkMapTable.ID},
	         BookmarkMapTable.AYAH_ID + "=" + ayahId + " AND "+ 
	         BookmarkMapTable.TYPE + "=" + bookmarkType,
	         null, null, null, null);
	   if (cursor.moveToFirst())
	      result = true;
	   cursor.close();
	   return result;
	}

	// Retruns a map with K as bookmarkId and V as a bookmark with its
	// bookmarkMaps field populated with the appropriate ayah-bookmark maps
   public Map<Long, Bookmark> getBookmarkMaps() {
      Map<Long, Bookmark> bookmarks = getBookmarks();
      Cursor c = db.query(BookmarkMapTable.TABLE_NAME,
            new String[] {BookmarkMapTable.ID, BookmarkMapTable.AYAH_ID,
            BookmarkMapTable.BOOKMARK_ID, BookmarkMapTable.TYPE},
            null, null, null, null, BookmarkMapTable.AYAH_ID + " ASC");
      while(c.moveToNext()) {
         long bookmarkMapId = c.getLong(0);
         int ayahId = c.getInt(1);
         long bookmarkId = c.getLong(2);
         boolean isPageBookmark = (c.getInt(3) == 1) ? true : false;
         BookmarkMap b = new BookmarkMap(bookmarkMapId, ayahId, bookmarkId, isPageBookmark);
         Bookmark bookmark = bookmarks.get(bookmarkId);
         if (bookmark != null && bookmark.bookmarkMaps != null) {
            bookmark.bookmarkMaps.add(b);
         }
      }
      c.close();
      return bookmarks;
   }
   
   public static class BookmarkMap {
      public long bookmarkMapId, bookmarkId;
      public int ayahId, page, sura, ayah;
      public boolean isPageBookmark;
      
      public BookmarkMap(long bookmarkMapId, int ayahId, long bookmarkId, boolean isPageBookmark) {
         this.bookmarkMapId = bookmarkMapId;
         this.ayahId = ayahId;
         this.bookmarkId = bookmarkId;
         this.isPageBookmark = isPageBookmark;
         int[] suraAyah = QuranInfo.getSuraAyahFromAyahId(ayahId);
         sura = suraAyah[0];
         ayah = suraAyah[1];
         page = QuranInfo.getPageFromSuraAyah(sura, ayah);
      }
   }
   
	public static class Bookmark {
		public Long id;
		public String name, description;
		public boolean isCollection;
		public List<BookmarkMap> bookmarkMaps;
		
		public Bookmark(Long id, String name, boolean isCollection) {
			this(id, name, isCollection, null);
		}
		
		public Bookmark(Long id, String name, boolean isCollection, String description) {
			this.id = id;
			this.name = name;
			this.isCollection = isCollection;
			this.description = description;
			this.bookmarkMaps = new ArrayList<BookmarkMap>();
		}
		
		@Override
		public String toString() {
		   return (name != null) ? name : super.toString();
		}
	}
	
}
