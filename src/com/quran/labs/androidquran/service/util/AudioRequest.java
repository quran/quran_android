package com.quran.labs.androidquran.service.util;

import java.io.Serializable;

import com.quran.labs.androidquran.data.QuranInfo;

public class AudioRequest implements Serializable {

   private static final long serialVersionUID = 1L;

   private String mBaseUrl = null;

   private int mStartSura = 0;
   private int mEndSura = 0;
   private int mStartAyah = 0;
   private int mEndAyah = 0;
   private int mMaxAyah = 0;
   private int mCurrentSura = 0;
   private int mCurrentAyah = 0;
   
   public AudioRequest(String baseUrl, int startSura, int startAyah,
         int endSura, int endAyah){
      mBaseUrl = baseUrl;
      mStartSura = startSura;
      mEndSura = endSura;
      mStartAyah = startAyah;
      mEndAyah = endAyah;
      
      if (mStartSura < 1 || mStartSura > 114 || mStartAyah < 1){
         throw new IllegalArgumentException();
      }
      
      mCurrentSura = mStartSura;
      mCurrentAyah = mStartAyah;
      mMaxAyah = QuranInfo.SURA_NUM_AYAHS[mCurrentSura-1];
   }
   
   public String getUrl(){
      if ((mEndAyah > 0 && mCurrentAyah > mEndAyah) 
            || (mEndSura > 0 && mCurrentSura > mEndSura)
            || mCurrentSura > 114
            || mCurrentSura < 1){
         return null;
      }
      
      return String.format(mBaseUrl, mCurrentSura, mCurrentAyah);
   }
   
   public String getTitle(){
      return mCurrentSura + ":" + mCurrentAyah;
   }
   
   public void gotoNextAyah(){
      mCurrentAyah++;
      if (mEndAyah < 1 && mMaxAyah < mCurrentAyah){
         mCurrentAyah = 1;
         mCurrentSura++;
         if (mCurrentSura <= 114){
            mMaxAyah = QuranInfo.SURA_NUM_AYAHS[mCurrentSura-1];
         }
      }
   }

   public void gotoPreviousAyah(){
      mCurrentAyah--;
      if (mEndAyah < 1 && mCurrentAyah < 1){
         mCurrentSura--;
         if (mCurrentSura > 0){
            mMaxAyah = QuranInfo.SURA_NUM_AYAHS[mCurrentSura-1];
            mCurrentAyah = mMaxAyah;
         }
      }
   }
}
