package com.quran.labs.androidquran.common;

import android.os.Parcel;
import android.os.Parcelable;

public class QuranAyah implements Parcelable {

  private int mSura = 0;
  private int mAyah = 0;

  // arabic text
  private String mText = null;

  // translation or tafseer text
  private String mTranslation = null;

  // is translation or tafseer text arabic or not
  private boolean mIsArabic = false;

  public QuranAyah(int sura, int ayah) {
    mSura = sura;
    mAyah = ayah;
  }

  protected QuranAyah(Parcel in) {
    this.mSura = in.readInt();
    this.mAyah = in.readInt();
    this.mText = in.readString();
    this.mTranslation = in.readString();
    this.mIsArabic = in.readByte() != 0;
  }


  public int getSura() {
    return mSura;
  }

  public int getAyah() {
    return mAyah;
  }

  public String getText() {
    return mText;
  }

  public void setText(String text) {
    mText = text;
  }

  public String getTranslation() {
    return mTranslation;
  }

  public void setTranslation(String translation) {
    mTranslation = translation;
  }

  public boolean isArabic() {
    return mIsArabic;
  }

  public void setArabic(boolean isArabic) {
    mIsArabic = isArabic;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(this.mSura);
    dest.writeInt(this.mAyah);
    dest.writeString(this.mText);
    dest.writeString(this.mTranslation);
    dest.writeByte(mIsArabic ? (byte) 1 : (byte) 0);
  }

  public static final Parcelable.Creator<QuranAyah> CREATOR = new Parcelable.Creator<QuranAyah>() {
    public QuranAyah createFromParcel(Parcel source) {
      return new QuranAyah(source);
    }

    public QuranAyah[] newArray(int size) {
      return new QuranAyah[size];
    }
  };
}
