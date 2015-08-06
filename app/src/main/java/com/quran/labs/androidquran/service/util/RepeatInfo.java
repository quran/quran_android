package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.common.QuranAyah;

import android.os.Parcel;
import android.os.Parcelable;

public class RepeatInfo implements Parcelable {

  private int mRepeatCount;
  private int mCurrentAyah;
  private int mCurrentSura;
  private int mCurrentPlayCount;

  public RepeatInfo(int repeatCount) {
    mRepeatCount = repeatCount;
  }

  protected RepeatInfo(Parcel in) {
    this.mRepeatCount = in.readInt();
    this.mCurrentAyah = in.readInt();
    this.mCurrentSura = in.readInt();
    this.mCurrentPlayCount = in.readInt();
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

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(this.mRepeatCount);
    dest.writeInt(this.mCurrentAyah);
    dest.writeInt(this.mCurrentSura);
    dest.writeInt(this.mCurrentPlayCount);
  }

  public static final Parcelable.Creator<RepeatInfo> CREATOR
      = new Parcelable.Creator<RepeatInfo>() {
    public RepeatInfo createFromParcel(Parcel source) {
      return new RepeatInfo(source);
    }

    public RepeatInfo[] newArray(int size) {
      return new RepeatInfo[size];
    }
  };
}
