package com.quran.labs.androidquran.data;

public class SuraAyahIterator {

  private SuraAyah start;
  private SuraAyah end;

  private boolean started;
  private int curSura;
  private int curAyah;

  public SuraAyahIterator(SuraAyah start, SuraAyah end) {
    // Sanity check
    if (start.compareTo(end) <= 0) {
      this.start = start;
      this.end = end;
    } else {
      this.start = end;
      this.end = start;
    }
    reset();
  }

  private void reset() {
    curSura = start.sura;
    curAyah = start.ayah;
    started = false;
  }

  public int getSura() {
    return curSura;
  }

  public int getAyah() {
    return curAyah;
  }

  private boolean hasNext() {
    return !started || curSura < end.sura || curAyah < end.ayah;
  }

  public boolean next() {
    if (!started) {
      return started = true;
    } else if (!hasNext()) {
      return false;
    }
    if (curAyah < QuranInfo.getNumAyahs(curSura)) {
      curAyah++;
    } else {
      curAyah = 1;
      curSura++;
    }
    return true;
  }
}
