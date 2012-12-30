package com.quran.labs.androidquran.ui.helpers;

public class QuranRow {
   // Row Types
   public static final int NONE = 0;
   public static final int HEADER = 1;
   public static final int PAGE_BOOKMARK = 2;
   public static final int AYAH_BOOKMARK = 3;
   public static final int BOOKMARK_HEADER = 4;
   
   public int sura;
   public int ayah;
   public int page;
   public String text;
   public String metadata;
   public int rowType;
   public Integer imageResource;
   public String imageText;
   
   // For Bookmarks
   public long tagId;
   public long bookmarkId;

   public QuranRow(String text, String metadata, int rowType, 
        int sura, int ayah, int page, Integer imageResource){
      this.text = text;
      this.rowType = rowType;
      this.sura = sura;
      this.ayah = ayah;
      this.page = page;
      this.metadata = metadata;
      this.imageResource = imageResource;
      this.imageText = "";
      this.tagId = -1;
      this.bookmarkId = -1;
   }

   public QuranRow(String text, String metadata, int rowType, 
         int sura, int page, Integer imageResource){
      this(text, metadata, rowType, sura, 0, page, imageResource);
   }
   
   public QuranRow(String text, String metadata, 
         int sura, int page, Integer imageResource){
      this(text, metadata, NONE, sura, 0, page, imageResource);
   }
   
   public boolean isHeader() {
      return rowType == HEADER || rowType == BOOKMARK_HEADER;
   }
   
   public boolean isBookmarkHeader() {
      return rowType == BOOKMARK_HEADER;
   }
   
   public boolean isBookmark() {
      return rowType == PAGE_BOOKMARK || rowType == AYAH_BOOKMARK;
   }
   
   public boolean isPageBookmark() {
      return rowType == PAGE_BOOKMARK;
   }
   
   public boolean isAyahBookmark() {
      return rowType == AYAH_BOOKMARK;
   }
}