package com.quran.labs.androidquran.dao.translation;

public class Translation {
  public final int id;
  public final String displayName;
  public final String downloadType;
  public final String fileUrl;
  public final String saveTo;
  public final String translator;
  public final String languageCode;
  public final int minimumVersion;
  public final int currentVersion;
  public final String fileName;
  public final String translatorNameLocalized;

  public Translation(int id, int minimumVersion, int currentVersion, String displayName,
      String downloadType, String fileName, String fileUrl, String saveTo,
      String languageCode, String translator, String translatorNameLocalized) {
    this.id = id;
    this.minimumVersion = minimumVersion;
    this.currentVersion = currentVersion;
    this.displayName = displayName;
    this.downloadType = downloadType;
    this.fileName = fileName;
    this.fileUrl = fileUrl;
    this.saveTo = saveTo;
    this.languageCode = languageCode;
    this.translator = translator;
    this.translatorNameLocalized = translatorNameLocalized;
  }
}
