package com.quran.labs.androidquran.common;

import java.io.Serializable;

public class QuranAyah implements Serializable {
   
   private static final long serialVersionUID = 1L;
   
   private int mSura = 0;
   private int mAyah = 0;
   private String mText = null;
   private String mTranslation = null;

   public QuranAyah(){
   }

   public QuranAyah(int sura, int ayah){
      mSura = sura;
      mAyah = ayah;
   }

   public int getSura(){ return mSura; }
   public int getAyah(){ return mAyah; }
   public void setSura(int sura){ mSura = sura; }
   public void setAyah(int ayah){ mAyah = ayah; }
   public String getText(){ return mText; }
   public void setText(String text){ mText = text; }
   public String getTranslation(){ return mTranslation; }
   public void setTranslation(String translation){ mTranslation = translation; }
}
