package com.quran.labs.androidquran.common;

public class LocalTranslation {
  public final int id;
  public final String filename;
  public final String name;
  public final String translator;
  public final String url;
  public final int version;

  public LocalTranslation(int id, String filename,
      String name, String translator, String url, int version) {
    this.id = id;
    this.filename = filename;
    this.name = name;
    this.translator = translator;
    this.url = url;
    this.version = version;
  }
}
