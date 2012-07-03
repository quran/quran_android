package com.quran.labs.androidquran.common;

public class QuranAyah {
   private int mSura = 0;
   private int mAyah = 0;

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
}
