package com.quran.labs.androidquran.service.util;

import java.io.Serializable;

import android.util.Log;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;

public class AudioRequest implements Serializable {

   private static final long serialVersionUID = 1L;

   private String mBaseUrl = null;
   private String mGaplessDatabasePath = null;

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

   // did we just play the basmallah?
   private boolean mJustPlayedBasmallah = false;

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

   public void setGaplessDatabaseFilePath(String databaseFile){
      mGaplessDatabasePath = databaseFile;
   }

   public String getGaplessDatabaseFilePath(){
      return mGaplessDatabasePath;
   }

   public boolean isGapless(){
      return mGaplessDatabasePath != null;
   }

   public void setCurrentAyah(int sura, int ayah){
      mCurrentSura = sura;
      mCurrentAyah = ayah;
   }

   public String getBaseUrl(){ return mBaseUrl; }

   public void setPlayBounds(QuranAyah minVerse, QuranAyah maxVerse){
      mMinSura = minVerse.getSura();
      mMinAyah = minVerse.getAyah();
      mMaxSura = maxVerse.getSura();
      mMaxAyah = maxVerse.getAyah();
   }

   public void removePlayBounds(){
      mMinSura = 0;
      mMinAyah = 0;
      mMaxSura = 0;
      mMaxAyah = 0;
   }

   public QuranAyah getMinAyah(){
      return new QuranAyah(mMinSura, mMinAyah);
   }

   public QuranAyah getMaxAyah(){
      return new QuranAyah(mMaxSura, mMaxAyah);
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

      if (isGapless()){
         Log.d("AudioRequest", "isGapless, url: " +
                 String.format(mBaseUrl, mCurrentSura));
         return String.format(mBaseUrl, mCurrentSura);
      }

      int sura = mCurrentSura;
      int ayah = mCurrentAyah;
      if (ayah == 1 && sura != 1 && sura != 9 && !mJustPlayedBasmallah){
         mJustPlayedBasmallah = true;
         sura = 1;
         ayah = 1;
      }
      else { mJustPlayedBasmallah = false; }

      if (mJustPlayedBasmallah){
         // really if "about to play" bismillah...
         if (!haveSuraAyah(mCurrentSura, mCurrentAyah)){
            // if we don't have the first ayah, don't play basmallah
            return null;
         }
      }

      return String.format(mBaseUrl, sura, ayah);
   }

   public boolean haveSuraAyah(int sura, int ayah){
      // for streaming, we (theoretically) always "have" the sura and ayah
      return true;
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
      // don't go to next ayah if we haven't played basmallah yet
      if (mJustPlayedBasmallah){ return ; }

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
            mCurrentAyah = mAyahsInThisSura;
         }
      }
      else if (mCurrentAyah == 1 && !isGapless()){
         mJustPlayedBasmallah = true;
      }
   }
}
