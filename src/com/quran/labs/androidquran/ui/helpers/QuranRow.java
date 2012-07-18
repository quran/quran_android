package com.quran.labs.androidquran.ui.helpers;

public class QuranRow {
   public int sura;
   public int ayah;
   public int page;
   public String text;
   public String metadata;
   public boolean isHeader;
   public Integer imageResource;
   public String imageText;

   public QuranRow(String text, String metadata, boolean isHeader, 
        int sura, int ayah, int page, Integer imageResource){
      this.text = text;
      this.isHeader = isHeader;
      this.sura = sura;
      this.ayah = ayah;
      this.page = page;
      this.metadata = metadata;
      this.imageResource = imageResource;
      this.imageText = "";
   }

   public QuranRow(String text, String metadata, boolean isHeader, 
         int sura, int page, Integer imageResource){
      this(text, metadata, isHeader, sura, 0, page, imageResource);
   }
}