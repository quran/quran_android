package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: ahmedre
 * Date: 5/7/13
 * Time: 11:03 PM
 */
public class TranslationTask extends AsyncTask<Void, Void, List<QuranAyah>> {
   private static final String TAG = "TranslationTask";

   private Context mContext;

   private int mPageNumber;
   private int mHighlightedAyah;
   private String mDatabaseName = null;
   private WeakReference<TranslationView> mTranslationView;

   public TranslationTask(Context context, int pageNumber,
                          int highlightedAyah, String databaseName,
                          TranslationView view){
      mContext = context;
      mDatabaseName = databaseName;
      mPageNumber = pageNumber;
      mHighlightedAyah = highlightedAyah;
      mTranslationView = new WeakReference<TranslationView>(view);

      if (context instanceof PagerActivity){
         ((PagerActivity)context).setLoadingIfPage(mPageNumber);
      }
   }

   @Override
   protected List<QuranAyah> doInBackground(Void... params) {
      int page = mPageNumber;
      Integer[] bounds = QuranInfo.getPageBounds(page);
      if (bounds == null){ return null; }

      String databaseName = mDatabaseName;

      // is this an arabic translation/tafseer or not
      boolean isArabic = mDatabaseName.contains(".ar.") ||
              mDatabaseName.equals("quran.muyassar.db");
      List<QuranAyah> verses = new ArrayList<QuranAyah>();

      try {
         DatabaseHandler translationHandler =
                 new DatabaseHandler(mContext, databaseName);
         Cursor translationCursor =
                 translationHandler.getVerses(bounds[0], bounds[1],
                         bounds[2], bounds[3],
                         DatabaseHandler.VERSE_TABLE);

         DatabaseHandler ayahHandler = null;
         Cursor ayahCursor = null;

         if (QuranSettings.wantArabicInTranslationView(mContext)){
            try {
               ayahHandler = new DatabaseHandler(mContext,
                       QuranDataProvider.QURAN_ARABIC_DATABASE);
               ayahCursor = ayahHandler.getVerses(bounds[0], bounds[1],
                       bounds[2], bounds[3],
                       DatabaseHandler.ARABIC_TEXT_TABLE);
            }
            catch (Exception e){
               // ignore any exceptions due to no arabic database
            }
         }

         if (translationCursor != null) {
            boolean validAyahCursor = false;
            if (ayahCursor != null && ayahCursor.moveToFirst()){
               validAyahCursor = true;
            }

            if (translationCursor.moveToFirst()) {
               do {
                  int sura = translationCursor.getInt(0);
                  int ayah = translationCursor.getInt(1);
                  String translation = translationCursor.getString(2);
                  QuranAyah verse = new QuranAyah(sura, ayah);
                  verse.setTranslation(translation);
                  if (validAyahCursor){
                     String text = ayahCursor.getString(2);
                     verse.setText(text);
                  }
                  verse.setArabic(isArabic);
                  verses.add(verse);
               }
               while (translationCursor.moveToNext() &&
                       (!validAyahCursor || ayahCursor.moveToNext()));
            }
            translationCursor.close();
            if (ayahCursor != null){
               ayahCursor.close();
            }
         }
         translationHandler.closeDatabase();
         if (ayahHandler != null){
            ayahHandler.closeDatabase();
         }
      }
      catch (Exception e){
         Log.d(TAG, "unable to open " + databaseName + " - " + e);
      }

      return verses;
   }

   @Override
   protected void onPostExecute(List<QuranAyah> result) {
      if (result != null){
         final TranslationView view = mTranslationView.get();
         if (view != null){
            view.setAyahs(result);
            if (mHighlightedAyah > 0){
               // give a chance for translation view to render
               view.postDelayed(new Runnable() {
                  @Override
                  public void run() {
                     view.highlightAyah(mHighlightedAyah);
                  }
               }, 100);
            }
         }

         if (mContext != null && mContext instanceof PagerActivity){
            ((PagerActivity)mContext).setLoading(false);
         }
      }
   }
}
