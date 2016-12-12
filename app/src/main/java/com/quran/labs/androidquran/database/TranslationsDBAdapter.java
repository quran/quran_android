package com.quran.labs.androidquran.database;

import com.quran.labs.androidquran.common.LocalTranslation;
import com.quran.labs.androidquran.dao.translation.TranslationItem;
import com.quran.labs.androidquran.util.QuranFileUtils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

import static com.quran.labs.androidquran.database.TranslationsDBHelper.TranslationsTable;

public class TranslationsDBAdapter {

  private SQLiteDatabase mDb;
  private Context mContext;
  private static TranslationsDBHelper sDbHelper;

  public TranslationsDBAdapter(Context context) {
    mContext = context.getApplicationContext();
    initHelper(mContext);
  }

  public static synchronized void initHelper(Context context) {
    if (sDbHelper == null) {
      sDbHelper = new TranslationsDBHelper(context);
    }
  }

  public void open() throws SQLException {
    if (mDb == null && sDbHelper != null) {
      mDb = sDbHelper.getWritableDatabase();
    }
  }

  public SparseArray<LocalTranslation> getTranslationsHash() {
    List<LocalTranslation> items = getTranslations();

    SparseArray<LocalTranslation> result = new SparseArray<>();
    if (items != null) {
      for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
        LocalTranslation item = items.get(i);
        result.put(item.id, item);
      }
    }
    return result;
  }

  public List<LocalTranslation> getTranslations() {
    if (mDb == null) {
      open();
      if (mDb == null) {
        return null;
      }
    }

    List<LocalTranslation> items = null;
    Cursor cursor = mDb.query(TranslationsTable.TABLE_NAME,
        null, null, null, null, null,
        TranslationsTable.ID + " ASC");
    if (cursor != null) {
      items = new ArrayList<>();
      while (cursor.moveToNext()) {
        int id = cursor.getInt(0);
        String name = cursor.getString(1);
        String translator = cursor.getString(2);
        String translatorForeign = cursor.getString(3);
        String filename = cursor.getString(4);
        String url = cursor.getString(5);
        int version = cursor.getInt(6);

        if (QuranFileUtils.hasTranslation(mContext, filename)) {
          items.add(new LocalTranslation(id, filename, name, translator, translatorForeign, url, version));
        }
      }
      cursor.close();
    }
    return items;
  }

  public boolean writeTranslationUpdates(List<TranslationItem> updates) {
    if (mDb == null) {
      open();
      if (mDb == null) {
        return false;
      }
    }

    boolean result = true;
    mDb.beginTransaction();
    try {
      for (int i = 0, updatesSize = updates.size(); i < updatesSize; i++) {
        TranslationItem item = updates.get(i);
        if (item.exists()) {
          ContentValues values = new ContentValues();
          values.put(TranslationsTable.ID, item.translation.id);
          values.put(TranslationsTable.NAME, item.translation.displayName);
          values.put(TranslationsTable.TRANSLATOR, item.translation.translator);
          values.put(TranslationsTable.TRANSLATOR_FOREIGN, item.translation.translatorNameLocalized);
          values.put(TranslationsTable.FILENAME, item.translation.filename);
          values.put(TranslationsTable.URL, item.translation.fileUrl);
          values.put(TranslationsTable.VERSION, item.localVersion);

          mDb.replace(TranslationsTable.TABLE_NAME, null, values);
        } else {
          mDb.delete(TranslationsTable.TABLE_NAME,
              TranslationsTable.ID + " = " + item.translation.id, null);
        }
      }
      mDb.setTransactionSuccessful();
    } catch (Exception e) {
      result = false;
      Timber.d(e, "error writing translation updates");
    }
    mDb.endTransaction();

    return result;
  }
}
