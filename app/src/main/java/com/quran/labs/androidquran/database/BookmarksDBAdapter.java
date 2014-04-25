package com.quran.labs.androidquran.database;

import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarkTagTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarksTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.TagsTable;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.widget.Checkable;

import java.util.ArrayList;
import java.util.List;

public class BookmarksDBAdapter {
   private static final String TAG =
           "com.quran.labs.androidquran.database.BookmarksDBAdapter";

   public static final int SORT_DATE_ADDED = 0;
   public static final int SORT_LOCATION = 1;
   public static final int SORT_ALPHABETICAL = 2;
   
	private SQLiteDatabase mDb;
	private BookmarksDBHelper mDbHelper;
	
	public BookmarksDBAdapter(Context context) {
		mDbHelper = BookmarksDBHelper.getInstance(context);
	}

	public void open() throws SQLException {
      if (mDb == null){
		   mDb = mDbHelper.getWritableDatabase();
      }
	}

	public void close() {
      // disabling for now - since we have a singleton helper,
      // we should be okay...

      /*
      if (mDb != null){
		   try { mDbHelper.close(); } catch (Exception e){ }
         mDb = null;
      }
      */
	}

  public List<Bookmark> getBookmarkedAyahsOnPage(int page) {
    return getBookmarks(true, SORT_LOCATION, page);
  }

	public List<Bookmark> getBookmarks(boolean loadTags){
	   return getBookmarks(loadTags, SORT_DATE_ADDED);
	}

   public List<Bookmark> getBookmarks(boolean loadTags, int sortOrder){
     return getBookmarks(loadTags, sortOrder, null);
   }

   public List<Bookmark> getBookmarks(boolean loadTags, int sortOrder, Integer pageFilter){
      if (mDb == null){
         open();
         if (mDb == null){ return null; }
      }

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
      String query = null;
      if (pageFilter != null) {
        query = BookmarksTable.PAGE + "=" + pageFilter +
            " AND " + BookmarksTable.SURA + " IS NOT NULL" +
            " AND " + BookmarksTable.AYAH + " IS NOT NULL";
      }
      Cursor cursor = mDb.query(BookmarksTable.TABLE_NAME,
              null, query, null, null, null, orderBy);
      if (cursor != null){
         bookmarks = new ArrayList<Bookmark>();
         while (cursor.moveToNext()){
            long id = cursor.getLong(0);
            Integer sura = cursor.getInt(1);
            Integer ayah = cursor.getInt(2);
            int page = cursor.getInt(3);
            long time = cursor.getLong(4);

            if (sura == 0 || ayah == 0){
               sura = null;
               ayah = null;
            }

            Bookmark bookmark = new Bookmark(id, sura, ayah, page, time);
            if (loadTags) {
               bookmark.mTags = getBookmarkTags(id);
            }
            bookmarks.add(bookmark);
         }
         cursor.close();
      }
      return bookmarks;
   }
   
   public List<Long> getBookmarkTagIds(long bookmarkId) {
      List<Long> bookmarkTags = null;
      Cursor cursor = mDb.query(BookmarkTagTable.TABLE_NAME,
            new String[] {BookmarkTagTable.TAG_ID},
            BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId,
            null, null, null, BookmarkTagTable.TAG_ID + " ASC");
      if (cursor != null){
         bookmarkTags = new ArrayList<Long>();
         while (cursor.moveToNext()){
            bookmarkTags.add(cursor.getLong(0));
         }
         cursor.close();
      }
      return bookmarkTags.size() > 0 ? bookmarkTags : null;
   }
   
   public List<Tag> getBookmarkTags(long bookmarkId) {
      List<Tag> bookmarkTags = null;
      Cursor cursor = mDb.query(BookmarkTagTable.TABLE_NAME,
              new String[] {BookmarkTagTable.TAG_ID},
              BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId,
              null, null, null, BookmarkTagTable.TAG_ID + " ASC");
      // TODO Use join to get tag name as well
      if (cursor != null){
         bookmarkTags = new ArrayList<Tag>();
         while (cursor.moveToNext()){
            long id = cursor.getLong(0);
            bookmarkTags.add(new Tag(id, null));
         }
         cursor.close();
      }
      return bookmarkTags.size() > 0 ? bookmarkTags : null;
   }

   /** @return final bookmarked state after calling this method */
   public boolean togglePageBookmark(int page) {
      long bookmarkId = getBookmarkId(null, null, page);
      if (bookmarkId < 0) {
         addBookmark(page);
         return true;
      } else {
         removeBookmark(bookmarkId);
         return false;
      }
   }
   
   public boolean isPageBookmarked(int page){
      return getBookmarkId(null, null, page) >= 0;
   }

   public long getBookmarkId(Integer sura, Integer ayah, int page) {
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }

      Cursor cursor = null;
      try {
         cursor = mDb.query(BookmarksTable.TABLE_NAME,
              null, BookmarksTable.PAGE + "=" + page + " AND " +
              BookmarksTable.SURA + (sura == null? " IS NULL" : "=" + sura) +
              " AND " + BookmarksTable.AYAH +
              (ayah == null?" IS NULL" : "=" + ayah), null, null, null, null);
         if (cursor != null && cursor.moveToFirst()){
            return cursor.getLong(0);
         }
      }
      catch (Exception e){
         // swallow the error for now
      }
      finally {
         if (cursor != null){
            try { cursor.close(); } catch (Exception e){ }
         }
      }
      return -1;
   }
   
   public long addBookmark(int page){
      return addBookmark(null, null, page);
   }

   public long addBookmarkIfNotExists(Integer sura, Integer ayah, int page){
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }
      long bookmarkId = getBookmarkId(sura, ayah, page);
      if (bookmarkId < 0)
         bookmarkId = addBookmark(sura, ayah, page);
      return bookmarkId;
   }
   
   public long addBookmark(Integer sura, Integer ayah, int page){
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }

      ContentValues values = new ContentValues();
      values.put(BookmarksTable.SURA, sura);
      values.put(BookmarksTable.AYAH, ayah);
      values.put(BookmarksTable.PAGE, page);
      return mDb.insert(BookmarksTable.TABLE_NAME, null, values);
   }

   public boolean removeBookmark(long bookmarkId){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }
      clearBookmarkTags(bookmarkId);
      return mDb.delete(BookmarksTable.TABLE_NAME,
              BookmarksTable.ID + "=" + bookmarkId, null) == 1;
   }

   public List<Tag> getTags(){
      return getTags(SORT_ALPHABETICAL);
   }

   public List<Tag> getTags(int sortOrder){
      if (mDb == null){
         open();
         if (mDb == null){ return null; }
      }

      String orderBy = null;
      switch (sortOrder) {
      case SORT_DATE_ADDED:
         orderBy = TagsTable.ADDED_DATE + " DESC";
         break;
      case SORT_ALPHABETICAL:
      default:
         orderBy = TagsTable.NAME + " ASC";
         break;
      }
      List<Tag> tags = null;
      Cursor cursor = mDb.query(TagsTable.TABLE_NAME,
              null, null, null, null, null, orderBy);
      if (cursor != null){
         tags = new ArrayList<Tag>();
         while (cursor.moveToNext()){
            long id = cursor.getLong(0);
            String name = cursor.getString(1);
            Tag tag = new Tag(id, name);
            tags.add(tag);
         }
         cursor.close();
      }
      return tags;
   }

   public long addTag(String name){
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }

      ContentValues values = new ContentValues();
      values.put(TagsTable.NAME, name);
      return mDb.insert(TagsTable.TABLE_NAME, null, values);
   }

   public boolean updateTag(long id, String newName){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      ContentValues values = new ContentValues();
      values.put(TagsTable.ID, id);
      values.put(TagsTable.NAME, newName);
      return 1 == mDb.update(TagsTable.TABLE_NAME, values,
            TagsTable.ID + "=" + id, null);
   }

   public boolean removeTag(long tagId){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      boolean removed = mDb.delete(TagsTable.TABLE_NAME,
              TagsTable.ID + "=" + tagId, null) == 1;
      if (removed) {
    	  mDb.delete(BookmarkTagTable.TABLE_NAME,
                 BookmarkTagTable.TAG_ID + "=" + tagId, null);
      }
      
      return removed;
   }
   
   public void tagBookmarks(long[] bookmarkIds, List<Tag> tags) {
      if (mDb == null){
         open();
         if (mDb == null){ return; }
      }
      
      mDb.beginTransaction();
      try {
         for (Tag t : tags){
            if (t.mId < 0 || !t.isChecked()){ continue; }
            for (int i = 0; i < bookmarkIds.length; i++) {
               ContentValues values = new ContentValues();
               values.put(BookmarkTagTable.BOOKMARK_ID, bookmarkIds[i]);
               values.put(BookmarkTagTable.TAG_ID, t.mId);
               mDb.replace(BookmarkTagTable.TABLE_NAME, null, values);
            }
         }
      }
      catch (Exception e){
         Log.d(TAG, "exception in tagBookmark", e);
      }
      
      mDb.setTransactionSuccessful();
      mDb.endTransaction();
   }
   
   public void tagBookmark(long bookmarkId, List<Tag> tags) {
      if (mDb == null){
         open();
         if (mDb == null){ return; }
      }

      mDb.beginTransaction();
      try {
         for (Tag t : tags){
            if (t.mId < 0){ continue; }
            if (t.isChecked()){
               ContentValues values = new ContentValues();
               values.put(BookmarkTagTable.BOOKMARK_ID, bookmarkId);
               values.put(BookmarkTagTable.TAG_ID, t.mId);
               mDb.replace(BookmarkTagTable.TABLE_NAME, null, values);
            }
            else {
               mDb.delete(BookmarkTagTable.TABLE_NAME,
                       BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId +
                       " AND " + BookmarkTagTable.TAG_ID + "=" + t.mId, null);
            }
         }
      }
      catch (Exception e){
         Log.d(TAG, "exception in tagBookmark", e);
      }

      mDb.setTransactionSuccessful();
      mDb.endTransaction();
   }
   
   public void untagBookmark(long bookmarkId, long tagId) {
      if (mDb == null){
         open();
         if (mDb == null){ return; }
      }
      mDb.delete(BookmarkTagTable.TABLE_NAME,
            BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId + " AND "
                  + BookmarkTagTable.TAG_ID + "=" + tagId, null);
   }
   
   public int clearBookmarkTags(long bookmarkId) {
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }
      
      return mDb.delete(BookmarkTagTable.TABLE_NAME,
            BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId, null);
   }
   
   public static class Tag implements Checkable, Parcelable {
      public long mId;
      public String mName;
      public boolean mChecked = false;
      
      public Tag(long id, String name) {
         mId = id;
         mName = name;
      }
      
      public Tag(Parcel parcel) {
    	  readFromParcel(parcel);
      }

      @Override
      public String toString() {
         return mName == null? super.toString() : mName;
      }

      @Override
      public boolean isChecked() {
         return mChecked;
      }

      @Override
      public void setChecked(boolean checked) {
         mChecked = checked;
      }

      @Override
      public void toggle() {
         mChecked = !mChecked;
      }

      @Override
      public int describeContents() {
         return 0;
      }

      @Override
      public void writeToParcel(Parcel dest, int flags) {
         dest.writeLong(mId);
         dest.writeString(mName);
         dest.writeByte((byte) (mChecked ? 1 : 0));
      }
      
      public void readFromParcel(Parcel parcel) {
         mId = parcel.readLong();
         mName = parcel.readString();
         mChecked = parcel.readByte() == 1;
      }
      
      public static final Parcelable.Creator<Tag> CREATOR =
              new Parcelable.Creator<Tag>() {
         public Tag createFromParcel(Parcel in) {
            return new Tag(in);
         }
         
         public Tag[] newArray(int size) {
            return new Tag[size];
         }
      };
   }
   
	public static class Bookmark {
		public long mId;
      public Integer mSura;
      public Integer mAyah;
      public int mPage;
      public long mTimestamp;
      public List<Tag> mTags;

      public Bookmark(long id, Integer sura, Integer ayah,
                      int page, long timestamp){
         mId = id;
         mSura = sura;
         mAyah = ayah;
         mPage = page;
         mTimestamp = timestamp;
      }
      
      public boolean isPageBookmark() {
         return mSura == null && mAyah == null;
      }
      
	}
}
