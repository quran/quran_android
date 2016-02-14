package com.quran.labs.androidquran.dao.translation;

import com.squareup.moshi.Json;

public class Translation {
  public final int id;
  public final String displayName;
  public final String downloadType;
  public final String fileUrl;
  public final String saveTo;
  public final String translator;
  @Json(name = "minimum_version") public final int minimumVersion;
  @Json(name = "current_version") public final int currentVersion;
  @Json(name = "fileName") public final String filename;
  @Json(name = "translator_foreign") public final String translatorNameLocalized;

  public Translation(int id, int minimumVersion, int currentVersion, String displayName,
      String downloadType, String filename, String fileUrl, String saveTo,
      String translator, String translatorNameLocalized) {
    this.id = id;
    this.minimumVersion = minimumVersion;
    this.currentVersion = currentVersion;
    this.displayName = displayName;
    this.downloadType = downloadType;
    this.filename = filename;
    this.fileUrl = fileUrl;
    this.saveTo = saveTo;
    this.translator = translator;
    this.translatorNameLocalized = translatorNameLocalized;
  }
}
