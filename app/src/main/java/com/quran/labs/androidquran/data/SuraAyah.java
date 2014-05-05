package com.quran.labs.androidquran.data;

public class SuraAyah implements Comparable<SuraAyah> {
  final public int sura;
  final public int ayah;

  public SuraAyah(int sura, int ayah) {
    this.sura = sura;
    this.ayah = ayah;
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
