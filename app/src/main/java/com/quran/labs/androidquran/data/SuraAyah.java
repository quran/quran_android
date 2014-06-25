package com.quran.labs.androidquran.data;

import com.quran.labs.androidquran.common.QuranAyah;

import android.os.Parcel;
import android.os.Parcelable;

public class SuraAyah implements Comparable<SuraAyah>, Parcelable {
  final public int sura;
  final public int ayah;
  private int page = -1;

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
        public SuraAyah createFromParcel(Parcel in) {
          return new SuraAyah(in);
        }

        public SuraAyah[] newArray(int size) {
          return new SuraAyah[size];
        }
      };

  public int getPage() {
    return page > 0 ? page : (page = QuranInfo.getPageFromSuraAyah(sura, ayah));
  }

  @Override
  public int compareTo(SuraAyah another) {
    if (this.equals(another)) {
      return 0;
    } else if (sura == another.sura) {
      return ayah < another.ayah ? -1 : 1;
    } else {
      return sura < another.sura ? -1 : 1;
    }
  }

  // temporarily, until we change PagerActivity and audio
  // to only deal in SuraAyah.
  public QuranAyah toQuranAyah() {
    return new QuranAyah(sura, ayah);
  }

  @Override
  public boolean equals(Object o) {
    return o != null && o.getClass() == SuraAyah.class &&
        ((SuraAyah)o).sura == sura && ((SuraAyah)o).ayah == ayah;
  }

  @Override
  public int hashCode() {
    return  31 * sura + ayah;
  }

  @Override
  public String toString() {
    return "("+ sura +":"+ ayah +")";
  }

  public static Iterator getIterator(SuraAyah start, SuraAyah end) {
    return new Iterator(start, end);
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

  public static class Iterator {

    private SuraAyah mStart;
    private SuraAyah mEnd;

    private boolean started;
    private int mCurSura;
    private int mCurAyah;

    public Iterator(SuraAyah start, SuraAyah end) {
      // Sanity check
      if (start.compareTo(end) <= 0) {
        mStart = start;
        mEnd = end;
      } else {
        mStart = end;
        mEnd = start;
      }
      reset();
    }

    public void reset() {
      mCurSura = mStart.sura;
      mCurAyah = mStart.ayah;
      started = false;
    }

    public int getSura() {
      return mCurSura;
    }

    public int getAyah() {
      return mCurAyah;
    }

    public boolean hasNext() {
      return !started || mCurSura < mEnd.sura || mCurAyah < mEnd.ayah;
    }

    public boolean next() {
      if (!started) {
        return started = true;
      } else if (!hasNext()) {
        return false;
      }
      if (mCurAyah < QuranInfo.getNumAyahs(mCurSura)) {
        mCurAyah++;
      } else {
        mCurAyah = 1;
        mCurSura++;
      }
      return true;
    }
  }

}
