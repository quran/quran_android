package com.quran.labs.androidquran.dao.audio;

import android.os.Parcel;
import android.os.Parcelable;

import com.quran.labs.androidquran.data.SuraAyah;

public class RepeatInfo implements Parcelable {
  private final int repeatCount;
  private final int currentAyah;
  private final int currentSura;
  private final int currentPlayCount;

  RepeatInfo(int repeatCount) {
    this(repeatCount, 0, 0, 0);
  }

  private RepeatInfo(int repeatCount, int currentSura, int currentAyah, int currentPlayCount) {
    this.repeatCount = repeatCount;
    this.currentSura = currentSura;
    this.currentAyah = currentAyah;
    this.currentPlayCount = currentPlayCount;
  }

  private RepeatInfo(Parcel in) {
    this.repeatCount = in.readInt();
    this.currentAyah = in.readInt();
    this.currentSura = in.readInt();
    this.currentPlayCount = in.readInt();
  }

  RepeatInfo setCurrentVerse(int sura, int ayah) {
    if (sura != currentSura || ayah != currentAyah) {
      return new RepeatInfo(repeatCount, sura, ayah, 0);
    }
    return this;
  }

  public int getRepeatCount() {
    return repeatCount;
  }

  RepeatInfo setRepeatCount(int repeatCount) {
    return new RepeatInfo(repeatCount, currentSura, currentAyah, currentPlayCount);
  }

  boolean shouldRepeat() {
    return repeatCount == -1 || (currentPlayCount < repeatCount);
  }

  RepeatInfo incrementRepeat() {
    return new RepeatInfo(repeatCount, currentSura, currentAyah, currentPlayCount + 1);
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
    @Override
    public RepeatInfo createFromParcel(Parcel source) {
      return new RepeatInfo(source);
    }

    @Override
    public RepeatInfo[] newArray(int size) {
      return new RepeatInfo[size];
    }
  };
}
