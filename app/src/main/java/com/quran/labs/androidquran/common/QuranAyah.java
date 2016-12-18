package com.quran.labs.androidquran.common;

import android.os.Parcel;
import android.os.Parcelable;

public class QuranAyah implements Parcelable {

  private int sura = 0;
  private int ayah = 0;

  // arabic text
  private String text = null;

  // translation or tafseer text
  private String translation = null;

  // is translation or tafseer text arabic or not
  private boolean isArabic = false;

  public QuranAyah(int sura, int ayah) {
    this.sura = sura;
    this.ayah = ayah;
  }

  protected QuranAyah(Parcel in) {
    this.sura = in.readInt();
    this.ayah = in.readInt();
    this.text = in.readString();
    this.translation = in.readString();
    this.isArabic = in.readByte() != 0;
  }


  public int getSura() {
    return sura;
  }

  public int getAyah() {
    return ayah;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getTranslation() {
    return translation;
  }

  public void setTranslation(String translation) {
    this.translation = translation;
  }

  public boolean isArabic() {
    return isArabic;
  }

  public void setArabic(boolean isArabic) {
    this.isArabic = isArabic;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(this.sura);
    dest.writeInt(this.ayah);
    dest.writeString(this.text);
    dest.writeString(this.translation);
    dest.writeByte(isArabic ? (byte) 1 : (byte) 0);
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
