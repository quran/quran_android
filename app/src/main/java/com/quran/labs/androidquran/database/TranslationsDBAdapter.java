package com.quran.labs.androidquran.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.util.SparseArray;
import com.quran.labs.androidquran.common.TranslationItem;
import com.quran.labs.androidquran.util.QuranFileUtils;

import java.util.ArrayList;
import java.util.List;

import static com.quran.labs.androidquran.database.TranslationsDBHelper.TranslationsTable;

public class TranslationsDBAdapter {

   private static final String TAG = TranslationsDBAdapter.class.getSimpleName();

   private SQLiteDatabase mDb;
   private Context mContext;
   private static TranslationsDBHelper sDbHelper;

   public TranslationsDBAdapter(Context context) {
      mContext = context.getApplicationContext();
      initHelper(mContext);
   }

   public static synchronized void initHelper(Context context){
      if (sDbHelper == null){
         sDbHelper = new TranslationsDBHelper(context);
      }
   }

   public void open() throws SQLException {
      if (mDb == null && sDbHelper != null){
         mDb = sDbHelper.getWritableDatabase();
      }
   }

   public void close() {
      // http://touchlabblog.tumblr.com/post/24474750219/

      /*
      if (mDb != null){
         sDbHelper.close();
         mDb = null;
      }
      */
   }

   public SparseArray<TranslationItem> getTranslationsHash(){
      List<TranslationItem> items = getTranslations();

      SparseArray<TranslationItem> result = null;
      if (items != null){
         result = new SparseArray<TranslationItem>();
         for (TranslationItem item : items){
            result.put(item.id, item);
         }
      }
      return result;
   }

   public List<TranslationItem> getTranslations(){
      if (mDb == null){
         open();
         if (mDb == null){ return null; }
      }

      List<TranslationItem> items = null;
      Cursor cursor = mDb.query(TranslationsTable.TABLE_NAME,
              null, null, null, null, null,
              TranslationsTable.ID + " ASC");
      if (cursor != null){
         items = new ArrayList<TranslationItem>();
         while (cursor.moveToNext()){
            int id = cursor.getInt(0);
            String name = cursor.getString(1);
            String translator = cursor.getString(2);
            String filename = cursor.getString(3);
            String url = cursor.getString(4);
            int version = cursor.getInt(5);

            if (QuranFileUtils.hasTranslation(mContext, filename)){
               TranslationItem item = new TranslationItem(id, name, translator,
                       -1, filename, url, true);
               item.localVersion = version;
               items.add(item);
            }
         }
         cursor.close();
      }
      return items;
   }

   public boolean writeTranslationUpdates(List<TranslationItem> updates){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      boolean result = true;
      mDb.beginTransaction();
      try {
         for (TranslationItem item : updates){
            if (item.exists){
               ContentValues values = new ContentValues();
               values.put(TranslationsTable.ID, item.id);
               values.put(TranslationsTable.NAME, item.name);
               values.put(TranslationsTable.TRANSLATOR, item.translator);
               values.put(TranslationsTable.FILENAME, item.filename);
               values.put(TranslationsTable.URL, item.url);
               values.put(TranslationsTable.VERSION, item.localVersion);

               mDb.replace(TranslationsTable.TABLE_NAME, null, values);
            }
            else {
               mDb.delete(TranslationsTable.TABLE_NAME,
                       TranslationsTable.ID + " = " + item.id, null);
            }
         }
         mDb.setTransactionSuccessful();
      }
      catch (Exception e){
         result = false;
         Log.d(TAG, "error writing translation updates", e);
      }
      mDb.endTransaction();

      return result;
   }
}
