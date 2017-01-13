package com.quran.labs.androidquran.service.util;

import android.os.Parcel;
import android.os.Parcelable;

import com.quran.labs.androidquran.data.SuraAyah;

public class RepeatInfo implements Parcelable {

  private int repeatCount;
  private int currentAyah;
  private int currentSura;
  private int currentPlayCount;

  RepeatInfo(int repeatCount) {
    this.repeatCount = repeatCount;
  }

  private RepeatInfo(Parcel in) {
    this.repeatCount = in.readInt();
    this.currentAyah = in.readInt();
    this.currentSura = in.readInt();
    this.currentPlayCount = in.readInt();
  }

  void setCurrentVerse(int sura, int ayah) {
    if (sura != currentSura || ayah != currentAyah) {
      currentSura = sura;
      currentAyah = ayah;
      currentPlayCount = 0;
    }
  }

  public int getRepeatCount() {
    return repeatCount;
  }

  void setRepeatCount(int repeatCount) {
    this.repeatCount = repeatCount;
  }

  boolean shouldRepeat() {
    return repeatCount == -1 || (currentPlayCount < repeatCount);
  }

  void incrementRepeat() {
    currentPlayCount++;
  }

  SuraAyah getCurrentAyah() {
    return new SuraAyah(currentSura, currentAyah);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(this.repeatCount);
    dest.writeInt(this.currentAyah);
    dest.writeInt(this.currentSura);
    dest.writeInt(this.currentPlayCount);
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
