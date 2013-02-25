package com.quran.labs.androidquran.common;

import java.io.Serializable;

public class QuranAyah implements Serializable {
   
   private static final long serialVersionUID = 2L;
   
   private int mSura = 0;
   private int mAyah = 0;

   // arabic text
   private String mText = null;

   // translation or tafseer text
   private String mTranslation = null;

   // is translation or tafseer text arabic or not
   private boolean mIsArabic = false;

   public QuranAyah(){
   }

   public QuranAyah(int sura, int ayah){
      mSura = sura;
      mAyah = ayah;
   }

   public int getSura(){ return mSura; }
   public int getAyah(){ return mAyah; }
   public String getText(){ return mText; }
   public void setText(String text){ mText = text; }
   public String getTranslation(){ return mTranslation; }
   public void setTranslation(String translation){
      mTranslation = translation;
   }

   public boolean isArabic(){ return mIsArabic; }
   public void setArabic(boolean isArabic){ mIsArabic = isArabic; }
}
