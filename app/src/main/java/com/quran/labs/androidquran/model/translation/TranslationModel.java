package com.quran.labs.androidquran.model.translation;

import android.content.Context;

import com.quran.labs.androidquran.common.QuranText;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.VerseRange;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.di.ActivityScope;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.Single;

@ActivityScope
public class TranslationModel {
  private Context appContext;

  @Inject
  TranslationModel(Context appContext) {
    this.appContext = appContext;
  }

  public Single<List<QuranText>> getArabicFromDatabase(VerseRange verses) {
    return getVersesFromDatabase(verses,
        QuranDataProvider.QURAN_ARABIC_DATABASE, DatabaseHandler.TextType.ARABIC);
  }

  public Single<List<QuranText>> getTranslationFromDatabase(VerseRange verses, String db) {
    return getVersesFromDatabase(verses, db, DatabaseHandler.TextType.TRANSLATION);
  }

  private Single<List<QuranText>> getVersesFromDatabase(VerseRange verses,
                                                        String database,
                                                        @DatabaseHandler.TextType int type) {
    return Single.fromCallable(() -> {
      DatabaseHandler databaseHandler = DatabaseHandler.getDatabaseHandler(appContext, database);
      return databaseHandler.getVerses(verses, type);
    });
  }
}
