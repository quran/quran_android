package com.quran.labs.androidquran.database;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.io.File;


public class DatabaseHandler {
	
	private SQLiteDatabase mDatabase = null;
   private String mDatabasePath = null;

	public static String COL_SURA = "sura";
	public static String COL_AYAH = "ayah";
	public static String COL_TEXT = "text";
	public static String VERSE_TABLE = "verses";
	public static String ARABIC_TEXT_TABLE = "arabic_text";
	public static String AR_SEARCH_TABLE = "search";
	public static String TRANSLITERATION_TABLE = "transliteration";
	
	public static String PROPERTIES_TABLE = "properties";
	public static String COL_PROPERTY = "property";
	public static String COL_VALUE = "value";
	
	private int schemaVersion = 1;
	
	public DatabaseHandler(String databaseName) throws SQLException {
		String base = QuranFileUtils.getQuranDatabaseDirectory();
		if (base == null) return;
		String path = base + File.separator + databaseName;
		mDatabase = SQLiteDatabase.openDatabase(path, null,
				SQLiteDatabase.NO_LOCALIZED_COLLATORS);
		schemaVersion = getSchemaVersion();
      mDatabasePath = path;
	}
	
	public boolean validDatabase(){
		return (mDatabase == null)? false : mDatabase.isOpen();
	}

   public boolean reopenDatabase(){
      try {
         mDatabase = SQLiteDatabase.openDatabase(mDatabasePath,
                 null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
         return (mDatabase != null);
      }
      catch (Exception e){ return false; }
   }
	
	public Cursor getVerses(int sura, int minAyah, int maxAyah){
		return getVerses(sura, minAyah, maxAyah, VERSE_TABLE);
	}
	
	public int getSchemaVersion(){
		int version = 1;
		if (!validDatabase()){ return version; }
		
		Cursor result = null;
		try {
			result = mDatabase.query(PROPERTIES_TABLE, new String[]{ COL_VALUE },
					COL_PROPERTY + "= ?", new String[]{ "schema_version" },
					null, null, null);
			if ((result != null) && (result.moveToFirst()))
				version = result.getInt(0);
			if (result != null)
				result.close();
			return version;
		}
		catch (SQLException se){
			if (result != null)
				result.close();
			return version;
		}
	}

   public int getTextVersion(){
      int version = 1;
      if (!validDatabase()){ return version; }

      Cursor result = null;
      try {
         result = mDatabase.query(PROPERTIES_TABLE, new String[]{ COL_VALUE },
                 COL_PROPERTY + "= ?", new String[]{ "text_version" },
                 null, null, null);
         if ((result != null) && (result.moveToFirst()))
            version = result.getInt(0);
         if (result != null)
            result.close();
         return version;
      }
      catch (SQLException se){
         if (result != null)
            result.close();
         return version;
      }
   }
	
	public Cursor getVerses(int sura, int minAyah, int maxAyah, String table){
      return getVerses(sura, minAyah, sura, maxAyah, table);
   }

   public Cursor getVerses(int minSura, int minAyah, int maxSura,
                           int maxAyah, String table){
      if (!validDatabase()){
         if (!reopenDatabase()){ return null; }
      }

      StringBuilder whereQuery = new StringBuilder();
      whereQuery.append("(");

      if (minSura == maxSura){
         whereQuery.append(COL_SURA)
                 .append("=").append(minSura)
                 .append(" and ").append(COL_AYAH)
                 .append(">=").append(minAyah)
                 .append(" and ").append(COL_AYAH)
                 .append("<=").append(maxAyah);
      }
      else {
         // (sura = minSura and ayah >= minAyah)
         whereQuery.append("(").append(COL_SURA).append("=")
                 .append(minSura).append(" and ")
                 .append(COL_AYAH).append(">=").append(minAyah).append(")");

         whereQuery.append(" or ");

         // (sura = maxSura and ayah <= maxAyah)
         whereQuery.append("(").append(COL_SURA).append("=")
                 .append(maxSura).append(" and ")
                 .append(COL_AYAH).append("<=").append(maxAyah).append(")");

         whereQuery.append(" or ");

         // (sura > minSura and sura < maxSura)
         whereQuery.append("(").append(COL_SURA).append(">")
                 .append(minSura).append(" and ")
                 .append(COL_SURA).append("<")
                 .append(maxSura).append(")");
      }

      whereQuery.append(")");

		return mDatabase.query(table,
				new String[]{ COL_SURA, COL_AYAH, COL_TEXT },
				whereQuery.toString(), null, null, null,
              COL_SURA + "," + COL_AYAH);
	}
	
	public Cursor getVerse(int sura, int ayah){
		return getVerses(sura, ayah, ayah);
	}
	
	public Cursor search(String query, boolean withSnippets){
		return search(query, VERSE_TABLE, withSnippets);
	}
	
	public Cursor search(String query, String table, boolean withSnippets){
		if (!validDatabase()){
         if (!reopenDatabase()){ return null; }
      }

		String operator = " like '%";
		String endOperator = "%'";
		String whatTextToSelect = COL_TEXT;
		
		boolean useFullTextIndex = (schemaVersion > 1);
		if (useFullTextIndex){
			operator = " MATCH '";
			endOperator = "*'";
		}
		
		if (useFullTextIndex && withSnippets)
			whatTextToSelect = "snippet(" + table + ")";
		
		return mDatabase.rawQuery("select " + COL_SURA + ", " + COL_AYAH +
				", " + whatTextToSelect + " from " + table + " where " + COL_TEXT +
				operator + query + endOperator + " limit 50", null);
	}
	
	public void closeDatabase() {
		if (mDatabase != null){
			mDatabase.close();
         mDatabase = null;
      }
	}
}
