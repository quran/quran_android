package com.quran.labs.androidquran.common;

public class LocalTranslation {
  public final int id;
  public final String filename;
  public final String name;
  public final String translator;
  public final String translatorForeign;
  public final String url;
  public final int version;

  public LocalTranslation(int id, String filename, String name,
                          String translator, String translatorForeign, String url, int version) {
    this.id = id;
    this.filename = filename;
    this.name = name;
    this.translator = translator;
    this.translatorForeign = translatorForeign;
    this.url = url;
    this.version = version;
  }

  public String getTranslatorName() {
    final String result;
    if (this.translatorForeign != null) {
      result = this.translatorForeign;
    } else if (this.translator != null) {
      result = this.translator;
    } else {
      result = this.name;
    }
    return result;
  }
}
