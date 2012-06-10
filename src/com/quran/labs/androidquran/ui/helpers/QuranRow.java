package com.quran.labs.androidquran.ui.helpers;

public class QuranRow {
   public int number;
   public int page;
   public String text;
   public String metadata;
   public boolean isHeader;
   public Integer imageResource;
   public String imageText;

   public QuranRow(String text, String metadata, boolean isHeader, 
        int number, int page, Integer imageResource){
      this.text = text;
      this.isHeader = isHeader;
      this.number = number;
      this.page = page;
      this.metadata = metadata;
      this.imageResource = imageResource;
      this.imageText = "";
   }
}