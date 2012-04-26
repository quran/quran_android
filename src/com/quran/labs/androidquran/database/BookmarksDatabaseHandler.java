package com.quran.labs.androidquran.database;

import java.util.ArrayList;
import java.util.List;

import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDatabaseHelper.AyahTable;
import com.quran.labs.androidquran.database.BookmarksDatabaseHelper.AyahTagMapTable;
import com.quran.labs.androidquran.database.BookmarksDatabaseHelper.PageTable;
import com.quran.labs.androidquran.database.BookmarksDatabaseHelper.TagsTable;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class BookmarksDatabaseHandler {

	private SQLiteDatabase mDb;
	private BookmarksDatabaseHelper mDbHelper;
	
	public BookmarksDatabaseHandler(Context context) {
		this.mDbHelper = new BookmarksDatabaseHelper(context);
	}

	// TODO Add error handling for SQLExceptions and such..

	public void open() throws SQLException {
		mDb = mDbHelper.getWritableDatabase();
	}

	public void close() {
		mDbHelper.close();
	}

	private Cursor findAyah(int sura, int ayah, String[] columns) {
		return findAyah(QuranInfo.getAyahId(sura, ayah), columns);
	}
	
	private Cursor findAyah(int ayahId, String[] columns) {
		return mDb.query(AyahTable.TABLE_NAME, columns, AyahTable.ID + "=" + ayahId,
				null, null, null, AyahTable.SURA+", "+AyahTable.AYAH);
	}
	
	private ContentValues createAyahValues(
			int ayahId, int page, int sura, int ayah, int bookmarked) {
		ContentValues values = new ContentValues();
		values.put(AyahTable.ID, ayahId);
		values.put(AyahTable.PAGE, page);
		values.put(AyahTable.SURA, sura);
		values.put(AyahTable.AYAH, ayah);
		values.put(AyahTable.BOOKMARKED, bookmarked);
		return values;
	}
	
	// Returns new isBookmarked value
	public boolean toggleAyahBookmark(int page, int sura, int ayah) {
		int ayahId = QuranInfo.getAyahId(sura, ayah);
		Cursor cursor = findAyah(ayahId, new String[] {AyahTable.BOOKMARKED});
		if (cursor.moveToFirst()) {
			boolean isBookmarked = (cursor.getInt(0) != 0);
			ContentValues values = new ContentValues();
			values.put(AyahTable.BOOKMARKED, !isBookmarked);
			mDb.update(AyahTable.TABLE_NAME, values, AyahTable.ID+"="+ayahId, null);
			return !isBookmarked;
		} else {
			ContentValues values = createAyahValues(ayahId, page, sura, ayah, 1);
			mDb.insert(AyahTable.TABLE_NAME, null, values);
			return true;
		}
	}
	
	public boolean isAyahBookmarked(int sura, int ayah) {
		Cursor cursor = findAyah(sura, ayah, new String[] {AyahTable.BOOKMARKED});
		return (cursor.moveToFirst() && cursor.getInt(0) == 1);
	}
	
	public List<AyahBookmark> getAyahBookmarks() {
		return getAyahBookmarks(null);
	}
	
	public List<AyahBookmark> getAyahBookmarks(Integer page) {
		String query = AyahTable.BOOKMARKED + "=1";
		if(page != null)
			query += " and " + AyahTable.PAGE + "=" + page;
		Cursor cursor = mDb.query(AyahTable.TABLE_NAME,
				new String[] {AyahTable.ID, AyahTable.PAGE, AyahTable.SURA, AyahTable.AYAH},
				query, null, null, null, AyahTable.SURA+", "+AyahTable.AYAH);
		List<AyahBookmark> ayahBookmarks = new ArrayList<AyahBookmark>();
		while(cursor.moveToNext()) {
			ayahBookmarks.add(new AyahBookmark(cursor.getInt(0), 
					cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), true));
		}
		return ayahBookmarks;
	}
	
	// If no notes, returns empty string
	public String getAyahNotes(int sura, int ayah) {
		Cursor cursor = findAyah(sura, ayah, new String[] {AyahTable.NOTES});
		if (!cursor.moveToFirst() || cursor.isNull(0))
			return "";
		return cursor.getString(0);
	}
	
	public void saveAyahNotes(int page, int sura, int ayah, String notes) {
		int ayahId = QuranInfo.getAyahId(sura, ayah);
		Cursor cursor = findAyah(ayahId, new String[] {AyahTable.BOOKMARKED});
		if (cursor.moveToFirst()) {
			ContentValues values = new ContentValues();
			values.put(AyahTable.NOTES, notes);
			mDb.update(AyahTable.TABLE_NAME, values, AyahTable.ID+"="+ayahId, null);
		} else {
			ContentValues values = createAyahValues(ayahId, page, sura, ayah, 0);
			values.put(AyahTable.NOTES, notes);
			mDb.insert(AyahTable.TABLE_NAME, null, values);
		}
	}
	
	public Integer getTagId(String tagName) {
		Cursor cursor = mDb.query(TagsTable.TABLE_NAME,
				new String[] {TagsTable.ID},
				TagsTable.NAME + "='" + tagName + "'", null, null, null, TagsTable.ID);
		if (cursor.moveToFirst())
			return cursor.getInt(0);
		else
			return null;
	}
	
	public AyahTag getTag(int tagId) {
		Cursor cursor = mDb.query(TagsTable.TABLE_NAME,
				new String[] {TagsTable.ID, TagsTable.NAME, TagsTable.DESCRIPTION, TagsTable.COLOR},
				TagsTable.ID + "=" + tagId, null, null, null, TagsTable.ID);
		AyahTag tag = null;
		if (cursor.moveToFirst()) {
			tag = new AyahTag(cursor.getInt(0), cursor.getString(1));
			if (!cursor.isNull(2))
				tag.description = cursor.getString(2);
			if (!cursor.isNull(3))
				tag.color = cursor.getInt(3);
		}
		return tag;
	}
	
	public List<AyahTag> getTagList() {
		Cursor cursor = mDb.query(TagsTable.TABLE_NAME,
				new String[] {TagsTable.ID, TagsTable.NAME, TagsTable.DESCRIPTION, TagsTable.COLOR},
				null, null, null, null, TagsTable.ID);
		List<AyahTag> tagList = new ArrayList<AyahTag>();
		while (cursor.moveToNext()) {
			AyahTag tag = new AyahTag(cursor.getInt(0), cursor.getString(1));
			if (!cursor.isNull(2))
				tag.description = cursor.getString(2);
			if (!cursor.isNull(3))
				tag.color = cursor.getInt(3);
			tagList.add(tag);
		}
		return tagList;
	}
	
	// Returns false if an existing tag was updated, true if a new one was created
	public int saveTag(String tagName, String tagDescription, Integer tagColor) {
		ContentValues values = new ContentValues();
		values.put(TagsTable.NAME, tagName);
		if (tagDescription != null)
			values.put(TagsTable.DESCRIPTION, tagDescription);
		if (tagColor != null)
			values.put(TagsTable.COLOR, tagColor);
		
		Integer tagId = getTagId(tagName);
		if (tagId != null)
			mDb.update(TagsTable.TABLE_NAME, values, TagsTable.ID+"="+tagId, null);
		else
			tagId = (int) mDb.insert(TagsTable.TABLE_NAME, null, values);
		return tagId;
	}
	
	public void deleteTag(int tagId) {
		mDb.delete(TagsTable.TABLE_NAME, TagsTable.ID+"="+tagId, null);
		mDb.delete(AyahTagMapTable.TABLE_NAME, AyahTagMapTable.TAG_ID+"="+tagId, null);
	}
	
	public List<AyahTag> getAyahTags(int page, int sura, int ayah) {
		return getAyahTags(QuranInfo.getAyahId(sura, ayah));
	}
	
	public List<AyahTag> getAyahTags(int ayahId) {
		List<Integer> ayahTagIds = getAyahTagIds(ayahId);
		List<AyahTag> tags = new ArrayList<AyahTag>();
		for (Integer tagId : ayahTagIds) {
			AyahTag tag = getTag(tagId);
			if (tag != null)
				tags.add(tag);
		}
		return tags;
	}
	
	public List<Integer> getAyahTagIds(int page, int sura, int ayah) {
		return getAyahTagIds(QuranInfo.getAyahId(sura, ayah));
	}
	
	public List<Integer> getAyahTagIds(int ayahId) {
		Cursor cursor = mDb.query(AyahTagMapTable.TABLE_NAME,
				new String[] {AyahTagMapTable.TAG_ID},
				AyahTagMapTable.AYAH_ID + "=" + ayahId, null, null, null, AyahTagMapTable.TAG_ID);
		List<Integer> tagIds = new ArrayList<Integer>();
		while(cursor.moveToNext()) {
			tagIds.add(cursor.getInt(0));
		}
		return tagIds;
	}
	
	public void updateAyahTags(int sura, int ayah, List<Integer> tagIds) {
		updateAyahTags(QuranInfo.getAyahId(sura, ayah), tagIds);
	}
	
	public void updateAyahTags(int ayahId, List<Integer> tagIds) {
		clearAyahTags(ayahId);
		for (Integer tagId : tagIds) {
			ContentValues values = new ContentValues();
			values.put(AyahTagMapTable.AYAH_ID, ayahId);
			values.put(AyahTagMapTable.TAG_ID, tagId);
			mDb.insert(AyahTagMapTable.TABLE_NAME, null, values);
		}
	}
	
	public void clearAyahTags(int ayahId) {
		mDb.delete(AyahTagMapTable.TABLE_NAME, AyahTagMapTable.AYAH_ID+"="+ayahId, null);
	}
	
	private Cursor findPage(int page) {
		return mDb.query(PageTable.TABLE_NAME, new String[] {PageTable.BOOKMARKED},
				PageTable.ID + "=" + page, null, null, null, PageTable.ID);
	}
	
	public boolean togglePageBookmark(int page) {
		Cursor cursor = findPage(page);
		if (!cursor.moveToFirst()) {
			ContentValues values = new ContentValues();
			values.put(PageTable.ID, page);
			values.put(PageTable.BOOKMARKED, 1);
			mDb.insert(PageTable.TABLE_NAME, null, values);
			return true;
		} else {
			boolean isBookmarked = (cursor.getInt(0) != 0);
			ContentValues values = new ContentValues();
			values.put(PageTable.BOOKMARKED, !isBookmarked);
			mDb.update(PageTable.TABLE_NAME, values, PageTable.ID+"="+page, null);
			return !isBookmarked;
		}
	}
	
	public List<Integer> getPageBookmarks() {
		Cursor cursor = mDb.query(PageTable.TABLE_NAME, new String[] {PageTable.ID},
				PageTable.BOOKMARKED + "=1", null, null, null, PageTable.ID);
		List<Integer> pageBookmarks = new ArrayList<Integer>();
		while(cursor.moveToNext()) {
			pageBookmarks.add(cursor.getInt(0));
		}
		return pageBookmarks;
	}
	
	public class AyahBookmark {
		public int id, page, sura, ayah;
		public boolean isBookmarked;
		public String notes;
		public List<Integer> tagIds;
		
		public AyahBookmark(int id, int page, int sura, int ayah, boolean isBookmarked) {
			this(id, page, sura, ayah, isBookmarked, null, null);
		}
		
		public AyahBookmark(int id, int page, int sura, int ayah,
				boolean isBookmarked, String notes, List<Integer> tagIds) {
			this.id = id;
			this.page = page;
			this.sura = sura;
			this.ayah = ayah;
			this.isBookmarked = isBookmarked;
			this.notes = notes;
			this.tagIds = tagIds != null ? tagIds : new ArrayList<Integer>();
		}
		
	}
	
	public class AyahTag {
		public Integer id;
		public String name, description;
		public Integer color;
		
		public AyahTag(Integer id, String name) {
			this(id, name, null, null);
		}
		
		public AyahTag(Integer id, String name, String description, Integer color) {
			this.id = id;
			this.name = name;
			this.description = description;
			this.color = color;
		}
	}
}
