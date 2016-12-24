package com.quran.labs.androidquran.data;

import android.content.Context;
import android.database.SQLException;
import android.support.annotation.Nullable;

import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AyahInfoDatabaseProvider {
  private final Context context;
  private final String widthParameter;
  @Nullable private final String tabletWidthParameter;

  @Nullable private AyahInfoDatabaseHandler pageDatabaseHandler;
  @Nullable private AyahInfoDatabaseHandler tabletDatabaseHandler;

  @Inject
  AyahInfoDatabaseProvider(Context context) {
    this.context = context;

    QuranScreenInfo quranScreenInfo = QuranScreenInfo.getOrMakeInstance(context);
    this.widthParameter = quranScreenInfo.getWidthParam();
    this.tabletWidthParameter = quranScreenInfo.getTabletWidthParam();
  }

  @Nullable
  public AyahInfoDatabaseHandler getAyahInfoHandler() {
    if (pageDatabaseHandler == null) {
      pageDatabaseHandler = getDatabaseForWidth(widthParameter);
    }
    return pageDatabaseHandler;
  }

  @Nullable
  public AyahInfoDatabaseHandler getTabletAyahInfoHandler() {
    if (tabletDatabaseHandler == null) {
      tabletDatabaseHandler = getDatabaseForWidth(tabletWidthParameter);
    }
    return tabletDatabaseHandler;
  }

  private AyahInfoDatabaseHandler getDatabaseForWidth(String width) {
    String filename = QuranFileUtils.getAyaPositionFileName(width);
    try {
      AyahInfoDatabaseHandler handler = new AyahInfoDatabaseHandler(context, filename);
      if (handler.validDatabase()) {
        return handler;
      }
    } catch (SQLException ignored) {
      // database might not yet exist, etc
    }
    return null;
  }
}
