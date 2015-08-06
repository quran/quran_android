package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.common.QuranAyah;

import java.io.Serializable;

public class RepeatInfo implements Serializable {

  private static final long serialVersionUID = 1L;

  private int mRepeatCount;
  private int mCurrentAyah;
  private int mCurrentSura;
  private int mCurrentPlayCount;

  public RepeatInfo(int repeatCount) {
    mRepeatCount = repeatCount;
  }

  public void setCurrentVerse(int sura, int ayah) {
    if (sura != mCurrentSura || ayah != mCurrentAyah) {
      mCurrentSura = sura;
      mCurrentAyah = ayah;
      mCurrentPlayCount = 0;
    }
  }

  public int getRepeatCount() {
    return mRepeatCount;
  }

  public void setRepeatCount(int repeatCount) {
    mRepeatCount = repeatCount;
  }

  public boolean shouldRepeat() {
    return mRepeatCount == -1 || (mCurrentPlayCount < mRepeatCount);
  }

  public void incrementRepeat() {
    mCurrentPlayCount++;
  }

  public QuranAyah getCurrentAyah() {
    return new QuranAyah(mCurrentSura, mCurrentAyah);
  }
}
