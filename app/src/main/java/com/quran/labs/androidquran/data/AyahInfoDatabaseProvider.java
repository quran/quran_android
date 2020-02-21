package com.quran.labs.androidquran.data;

import android.content.Context;

import com.quran.labs.androidquran.di.ActivityScope;
import com.quran.labs.androidquran.util.QuranFileUtils;

import javax.inject.Inject;

import androidx.annotation.Nullable;

@ActivityScope
public class AyahInfoDatabaseProvider {
  private final Context context;
  private final String widthParameter;
  private final QuranFileUtils quranFileUtils;
  @Nullable private AyahInfoDatabaseHandler databaseHandler;

  @Inject
  AyahInfoDatabaseProvider(Context context, String widthParameter, QuranFileUtils quranFileUtils) {
    this.context = context;
    this.widthParameter = widthParameter;
    this.quranFileUtils = quranFileUtils;
  }

  @Nullable
  public AyahInfoDatabaseHandler getAyahInfoHandler() {
    if (databaseHandler == null) {
      String filename = quranFileUtils.getAyaPositionFileName(widthParameter);
      databaseHandler =
          AyahInfoDatabaseHandler.getAyahInfoDatabaseHandler(context, filename, quranFileUtils);
    }
    return databaseHandler;
  }

  public int getPageWidth() {
    return Integer.parseInt(widthParameter.substring(1));
  }
}
