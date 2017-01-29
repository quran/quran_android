package com.quran.labs.androidquran.model.translation;

import android.content.Context;
import android.database.Cursor;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.quran.labs.androidquran.common.QuranText;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.database.DatabaseUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class ArabicDatabaseUtils {
  public static final String AR_BASMALLAH = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ";
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) static final int NUMBER_OF_WORDS = 4;

  private final Context appContext;
  private DatabaseHandler arabicDatabaseHandler;

  @Inject
  ArabicDatabaseUtils(Context context) {
    this.appContext = context;
    arabicDatabaseHandler = getArabicDatabaseHandler();
  }

  DatabaseHandler getArabicDatabaseHandler() {
    if (arabicDatabaseHandler == null) {
      try {
        arabicDatabaseHandler = DatabaseHandler.getDatabaseHandler(
            appContext.getApplicationContext(), QuranDataProvider.QURAN_ARABIC_DATABASE);
      } catch (Exception e) {
        // ignore
      }
    }
    return arabicDatabaseHandler;
  }

  @NonNull
  public Single<List<QuranText>> getVerses(final SuraAyah start, final SuraAyah end) {
    return Single.fromCallable(() -> {
      List<QuranText> verses = new ArrayList<>();

      Cursor cursor = null;
      try {
        DatabaseHandler arabicDatabaseHandler = getArabicDatabaseHandler();
        cursor = arabicDatabaseHandler.getVerses(start.sura, start.ayah,
            end.sura, end.ayah, DatabaseHandler.ARABIC_TEXT_TABLE);
        while (cursor.moveToNext()) {
          QuranText verse = new QuranText(cursor.getInt(1), cursor.getInt(2), cursor.getString(3));
          verses.add(verse);
        }
      } catch (Exception e) {
        // no op
      } finally {
        DatabaseUtils.closeCursor(cursor);
      }
      return verses;
    }).subscribeOn(Schedulers.io());
  }

  public List<Bookmark> hydrateAyahText(List<Bookmark> bookmarks) {
    List<Integer> ayahIds = new ArrayList<>();
    for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
      Bookmark bookmark = bookmarks.get(i);
      if (!bookmark.isPageBookmark()) {
        ayahIds.add(QuranInfo.getAyahId(bookmark.sura, bookmark.ayah));
      }
    }

    return ayahIds.isEmpty() ? bookmarks :
        mergeBookmarksWithAyahText(bookmarks, getAyahTextForAyat(ayahIds));
  }

  Map<Integer, String> getAyahTextForAyat(List<Integer> ayat) {
    Map<Integer, String> result = new HashMap<>(ayat.size());
    DatabaseHandler arabicDatabaseHandler = getArabicDatabaseHandler();
    if (arabicDatabaseHandler != null) {
      Cursor cursor = null;
      try {
        cursor = arabicDatabaseHandler.getVersesByIds(ayat);
        while (cursor.moveToNext()) {
          int id = cursor.getInt(0);
          int sura = cursor.getInt(1);
          int ayah = cursor.getInt(2);
          String text = cursor.getString(3);
          result.put(id, getFirstFewWordsFromAyah(sura, ayah, text));
        }
      } finally {
        DatabaseUtils.closeCursor(cursor);
      }
    }
    return result;
  }

  private List<Bookmark> mergeBookmarksWithAyahText(
      List<Bookmark> bookmarks, Map<Integer, String> ayahMap) {
    List<Bookmark> result;
    if (ayahMap.isEmpty()) {
      result = bookmarks;
    } else {
      result = new ArrayList<>(bookmarks.size());
      for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
        Bookmark bookmark = bookmarks.get(i);
        Bookmark toAdd;
        if (bookmark.isPageBookmark()) {
          toAdd = bookmark;
        } else {
          String ayahText = ayahMap.get(QuranInfo.getAyahId(bookmark.sura, bookmark.ayah));
          toAdd = ayahText == null ? bookmark : bookmark.withAyahText(ayahText);
        }
        result.add(toAdd);
      }
    }
    return result;
  }

  static String getFirstFewWordsFromAyah(int sura, int ayah, String text) {
    String ayahText = getAyahWithoutBasmallah(sura, ayah, text);
    int start = 0;
    for (int i = 0; i < NUMBER_OF_WORDS && start > -1; i++) {
      start = ayahText.indexOf(' ', start + 1);
    }
    return start > 0 ? ayahText.substring(0, start) : ayahText;
  }

  /**
   * Get the actual ayahText from the given ayahText. This is important because, currently, the
   * arabic database (quran.ar.db) has the first verse from each sura as "[basmallah] ayah" - this
   * method just returns ayah without basmallah.
   *
   * @param sura     the sura number
   * @param ayah     the ayah number
   * @param ayahText the ayah text
   * @return the ayah without the basmallah
   */
  public static String getAyahWithoutBasmallah(int sura, int ayah, String ayahText) {
    // note that ayahText.startsWith check is always true for now - but it's explicitly here so
    // that if we update quran.ar.db one day to fix this issue and older clients get a new copy of
    // the database, their code continues to work as before.
    if (ayah == 1 && sura != 9 && sura != 1 && ayahText.startsWith(AR_BASMALLAH)) {
      return ayahText.substring(AR_BASMALLAH.length() + 1);
    }
    return ayahText;
  }
}
