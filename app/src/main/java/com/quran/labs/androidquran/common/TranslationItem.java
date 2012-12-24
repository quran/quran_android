package com.quran.labs.androidquran.common;

public class TranslationItem {
   public int id;
   public String name;
   public String translator;
   public String filename;
   public String url;
   public boolean exists;
   public int latestVersion;
   public Integer localVersion;
   public boolean isSeparator = false;

   public TranslationItem(String name){
      this.name = name;
   }

   public TranslationItem(int id, String name, String translator,
                          int latestVersion, String filename, String url,
                          boolean exists){
      this.id = id;
      this.name = name;
      this.translator = translator;
      this.filename = filename;
      this.url = url;
      this.exists = exists;
      this.latestVersion = latestVersion;
   }
}