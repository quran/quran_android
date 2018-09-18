package com.quran.labs.androidquran.data;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;

public class SuraAyah implements Comparable<SuraAyah>, Parcelable {
  public final int sura;
  public final int ayah;

  public SuraAyah(int sura, int ayah) {
    this.sura = sura;
    this.ayah = ayah;
  }

  public SuraAyah(Parcel parcel) {
    this.sura = parcel.readInt();
    this.ayah = parcel.readInt();
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(sura);
    dest.writeInt(ayah);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Parcelable.Creator<SuraAyah> CREATOR =
      new Parcelable.Creator<SuraAyah>() {
        @Override
        public SuraAyah createFromParcel(Parcel in) {
          return new SuraAyah(in);
        }

        @Override
        public SuraAyah[] newArray(int size) {
          return new SuraAyah[size];
        }
      };

  @Override
  public int compareTo(@NonNull SuraAyah another) {
    if (this.equals(another)) {
      return 0;
    } else if (sura == another.sura) {
      return ayah < another.ayah ? -1 : 1;
    } else {
      return sura < another.sura ? -1 : 1;
    }
  }

  @Override
  public boolean equals(Object o) {
    return o != null && o.getClass() == SuraAyah.class &&
        ((SuraAyah) o).sura == sura && ((SuraAyah) o).ayah == ayah;
  }

  @Override
  public int hashCode() {
    return 31 * sura + ayah;
  }

  @NonNull
  @Override
  public String toString() {
    return "(" + sura + ":" + ayah + ")";
  }

  public static SuraAyah min(SuraAyah a, SuraAyah b) {
    return a.compareTo(b) <= 0 ? a : b;
  }

  public static SuraAyah max(SuraAyah a, SuraAyah b) {
    return a.compareTo(b) >= 0 ? a : b;
  }

  public boolean after(SuraAyah next) {
    return sura > next.sura || (sura == next.sura && ayah > next.ayah);
  }

}
