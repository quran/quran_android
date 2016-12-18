package com.quran.labs.androidquran.task;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Created with IntelliJ IDEA.
 * User: ahmedre
 * Date: 5/7/13
 * Time: 11:03 PM
 */
public class TranslationTask extends AsyncTask<Void, Void, List<QuranAyah>> {

  private Context context;

  private Integer[] ayahBounds;
  private int highlightedAyah;
  private String databaseName = null;
  private boolean isMissingData;
  private WeakReference<TranslationView> translationView;

  public TranslationTask(Context context, Integer[] ayahBounds,
      String databaseName) {
    this.context = context;
    this.databaseName = databaseName;
    this.ayahBounds = ayahBounds;
    highlightedAyah = 0;
    translationView = null;
  }

  public TranslationTask(Context context, int pageNumber,
      int highlightedAyah, String databaseName,
      TranslationView view) {
    this.context = context;
    this.databaseName = databaseName;
    ayahBounds = QuranInfo.getPageBounds(pageNumber);
    this.highlightedAyah = highlightedAyah;
    translationView = new WeakReference<>(view);
  }

  protected boolean loadArabicAyahText() {
    return QuranSettings.getInstance(context).wantArabicInTranslationView();
  }

  @Override
  protected List<QuranAyah> doInBackground(Void... params) {
    Integer[] bounds = ayahBounds;
    if (bounds == null) {
      return null;
    }

    String databaseName = this.databaseName;

    // is this an arabic translation/tafseer or not
    boolean isArabic = this.databaseName.contains(".ar.") ||
        this.databaseName.equals("quran.muyassar.db");
    List<QuranAyah> verses = new ArrayList<>();

    try {
      DatabaseHandler translationHandler =
          DatabaseHandler.getDatabaseHandler(context, databaseName);
      Cursor translationCursor =
          translationHandler.getVerses(bounds[0], bounds[1],
              bounds[2], bounds[3],
              DatabaseHandler.VERSE_TABLE);

      DatabaseHandler ayahHandler;
      Cursor ayahCursor = null;

      if (loadArabicAyahText()) {
        try {
          ayahHandler = DatabaseHandler.getDatabaseHandler(context,
              QuranDataProvider.QURAN_ARABIC_DATABASE);
          ayahCursor = ayahHandler.getVerses(bounds[0], bounds[1],
              bounds[2], bounds[3],
              DatabaseHandler.ARABIC_TEXT_TABLE);
        } catch (Exception e) {
          // ignore any exceptions due to no arabic database
          isMissingData = true;
        }
      }

      if (translationCursor != null) {
        boolean validAyahCursor = false;
        if (ayahCursor != null && ayahCursor.moveToFirst()) {
          validAyahCursor = true;
        }

        if (translationCursor.moveToFirst()) {
          do {
            int sura = translationCursor.getInt(1);
            int ayah = translationCursor.getInt(2);
            String translation = translationCursor.getString(3);
            QuranAyah verse = new QuranAyah(sura, ayah);
            verse.setTranslation(translation);
            if (validAyahCursor) {
              String text = ayahCursor.getString(3);
              verse.setText(text);
            }
            verse.setArabic(isArabic);
            verses.add(verse);
          }
          while (translationCursor.moveToNext() &&
              (!validAyahCursor || ayahCursor.moveToNext()));
        }
        translationCursor.close();
        if (ayahCursor != null) {
          ayahCursor.close();
        }
      }
    } catch (Exception e) {
      Timber.d(e, "unable to open: %s", databaseName);
    }

    return verses;
  }

  @Override
  protected void onPostExecute(List<QuranAyah> result) {
    final TranslationView view = translationView == null ?
        null : translationView.get();
    if (result != null) {
      if (view != null) {
        view.setAyahs(result);
        if (highlightedAyah > 0) {
          // give a chance for translation view to render
          view.postDelayed(() -> view.highlightAyah(highlightedAyah), 100);
        }
      }
    }

    if (view != null) {
      view.setDataMissing(isMissingData);
    }
  }
}
