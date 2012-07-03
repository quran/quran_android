package com.quran.labs.androidquran.service.util;

import java.io.Serializable;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;

public class AudioRequest implements Serializable {

   private static final long serialVersionUID = 1L;

   private String mBaseUrl = null;

   // where we started from
   private int mStartSura = 0;
   private int mStartAyah = 0;
   private int mAyahsInThisSura = 0;

   // min and max sura/ayah
   private int mMinSura = 0;
   private int mMinAyah = 0;
   private int mMaxSura = 0;
   private int mMaxAyah = 0;

   // what we're currently playing
   private int mCurrentSura = 0;
   private int mCurrentAyah = 0;
   
   public AudioRequest(String baseUrl, QuranAyah verse){
      mBaseUrl = baseUrl;
      mStartSura = verse.getSura();
      mStartAyah = verse.getAyah();

      if (mStartSura < 1 || mStartSura > 114 || mStartAyah < 1){
         throw new IllegalArgumentException();
      }
      
      mCurrentSura = mStartSura;
      mCurrentAyah = mStartAyah;
      mAyahsInThisSura = QuranInfo.SURA_NUM_AYAHS[mCurrentSura-1];
   }

   public void setPlayBounds(QuranAyah minVerse, QuranAyah maxVerse){
      mMinSura = minVerse.getSura();
      mMinAyah = minVerse.getAyah();
      mMaxSura = maxVerse.getSura();
      mMaxAyah = maxVerse.getAyah();
   }
   
   public String getUrl(){
      if ((mMaxSura > 0 && mCurrentSura > mMaxSura)
            || (mMaxAyah > 0 && mCurrentAyah > mMaxAyah
               && mCurrentSura >= mMaxSura)
            || (mMinSura > 0 && mCurrentSura < mMinSura)
            || (mMinAyah > 0 && mCurrentAyah < mMinAyah
               && mCurrentSura <= mMinSura)
            || mCurrentSura > 114
            || mCurrentSura < 1){
         return null;
      }
      
      return String.format(mBaseUrl, mCurrentSura, mCurrentAyah);
   }
   
   public String getTitle(){
      return mCurrentSura + ":" + mCurrentAyah;
   }

   public int getCurrentSura(){
      return mCurrentSura;
   }

   public int getCurrentAyah(){
      return mCurrentAyah;
   }
   
   public void gotoNextAyah(){
      mCurrentAyah++;
      if (mAyahsInThisSura < mCurrentAyah){
         mCurrentAyah = 1;
         mCurrentSura++;
         if (mCurrentSura <= 114){
            mAyahsInThisSura = QuranInfo.SURA_NUM_AYAHS[mCurrentSura-1];
         }
      }
   }

   public void gotoPreviousAyah(){
      mCurrentAyah--;
      if (mCurrentAyah < 1){
         mCurrentSura--;
         if (mCurrentSura > 0){
            mAyahsInThisSura = QuranInfo.SURA_NUM_AYAHS[mCurrentSura-1];
            mCurrentAyah = mMaxAyah;
         }
      }
   }
}
