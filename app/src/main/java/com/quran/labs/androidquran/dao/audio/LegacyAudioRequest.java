package com.quran.labs.androidquran.dao.audio;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;

import java.util.Locale;

import timber.log.Timber;

public abstract class LegacyAudioRequest implements Parcelable {

  private String baseUrl;
  private String gaplessDatabasePath = null;

  // where we started from
  private int ayahsInThisSura;

  // min and max sura/ayah
  private int minSura;
  private int minAyah;
  private int maxSura;
  private int maxAyah;

  // what we're currently playing
  private int currentSura;
  private int currentAyah;

  // range repeat info
  private RepeatInfo rangeRepeatInfo;
  private boolean enforceBounds;

  // did we just play the basmallah?
  private boolean justPlayedBasmallah;

  // repeat information
  private RepeatInfo repeatInfo;

  public abstract boolean haveSuraAyah(int sura, int ayah);

  LegacyAudioRequest(String baseUrl, SuraAyah verse, int verseCountInSura) {
    this.baseUrl = baseUrl;
    final int startSura = verse.sura;
    final int startAyah = verse.ayah;

    if (startSura < 1 || startSura > 114 || startAyah < 1) {
      throw new IllegalArgumentException();
    }

    currentSura = startSura;
    currentAyah = startAyah;
    ayahsInThisSura = verseCountInSura;

    repeatInfo = new RepeatInfo(0).setCurrentVerse(currentSura, currentAyah);

    rangeRepeatInfo = new RepeatInfo(0);
  }

  LegacyAudioRequest(Parcel in) {
    this.baseUrl = in.readString();
    this.gaplessDatabasePath = in.readString();
    this.ayahsInThisSura = in.readInt();
    this.minSura = in.readInt();
    this.minAyah = in.readInt();
    this.maxSura = in.readInt();
    this.maxAyah = in.readInt();
    this.currentSura = in.readInt();
    this.currentAyah = in.readInt();
    this.rangeRepeatInfo = in.readParcelable(RepeatInfo.class.getClassLoader());
    this.enforceBounds = in.readByte() != 0;
    this.justPlayedBasmallah = in.readByte() != 0;
    this.repeatInfo = in.readParcelable(RepeatInfo.class.getClassLoader());
  }

  public boolean needsIsti3athaAudio() {
    // TODO base this check on a boolean array in readers.xml
    return !isGapless() || gaplessDatabasePath.contains("minshawi_murattal");
  }

  public void setGaplessDatabaseFilePath(String databaseFile) {
    gaplessDatabasePath = databaseFile;
  }

  public String getGaplessDatabaseFilePath() {
    return gaplessDatabasePath;
  }

  public boolean isGapless() {
    return gaplessDatabasePath != null;
  }

  public void setVerseRepeatCount(int count) {
    repeatInfo = repeatInfo.setRepeatCount(count);
  }

  public int getVerseRepeatCount() {
    return repeatInfo.getRepeatCount();
  }

  public void setRangeRepeatCount(int rangeRepeatCount) {
    repeatInfo = rangeRepeatInfo.setRepeatCount(rangeRepeatCount);
  }

  public void setEnforceBounds(boolean enforceBounds) {
    this.enforceBounds = enforceBounds;
  }

  public boolean shouldEnforceBounds() {
    return enforceBounds;
  }

  public int getRangeRepeatCount() {
    return rangeRepeatInfo.getRepeatCount();
  }

  public SuraAyah getRangeStart() {
    return new SuraAyah(minSura, minAyah);
  }

  public SuraAyah getRangeEnd() {
    return new SuraAyah(maxSura, maxAyah);
  }

  public RepeatInfo getRepeatInfo() {
    return repeatInfo;
  }

  public SuraAyah setCurrentAyah(QuranInfo quranInfo, int sura, int ayah) {
    Timber.d("got setCurrentAyah of: %d:%d", sura, ayah);
    if (repeatInfo.shouldRepeat()) {
      repeatInfo = repeatInfo.incrementRepeat();
    } else {
      currentSura = sura;
      currentAyah = ayah;
      if (enforceBounds &&
          ((currentSura == maxSura && currentAyah > maxAyah) ||
              (currentSura > maxSura))) {
        if (rangeRepeatInfo.shouldRepeat()) {
          rangeRepeatInfo = rangeRepeatInfo.incrementRepeat();
          currentSura = minSura;
          currentAyah = minAyah;
        } else {
          return null;
        }
      }

      if (currentSura >= 1 && currentSura <= 114) {
        ayahsInThisSura = quranInfo.getNumAyahs(currentSura);
      }
      repeatInfo = repeatInfo.setCurrentVerse(currentSura, currentAyah);
    }
    return repeatInfo.getCurrentAyah();
  }

  public void setPlayBounds(SuraAyah minVerse, SuraAyah maxVerse) {
    minSura = minVerse.sura;
    minAyah = minVerse.ayah;
    maxSura = maxVerse.sura;
    maxAyah = maxVerse.ayah;
  }

  public String getUrl() {
    if (enforceBounds &&
        ((maxSura > 0 && currentSura > maxSura)
            || (maxAyah > 0 && currentAyah > maxAyah
            && currentSura >= maxSura)
            || (minSura > 0 && currentSura < minSura)
            || (minAyah > 0 && currentAyah < minAyah
            && currentSura <= minSura))) {
      return null;
    }

    if (currentSura > 114 || currentSura < 1) {
      return null;
    }

    if (isGapless()) {
      String url = String.format(Locale.US, baseUrl, currentSura);
      Timber.d("isGapless, url: %s", url);
      return url;
    }

    int sura = currentSura;
    int ayah = currentAyah;
    if (ayah == 1 && sura != 1 && sura != 9 && !justPlayedBasmallah) {
      justPlayedBasmallah = true;
      sura = 1;
      ayah = 1;
    } else {
      justPlayedBasmallah = false;
    }

    if (justPlayedBasmallah) {
      // really if "about to play" bismillah...
      if (!haveSuraAyah(currentSura, currentAyah)) {
        // if we don't have the first ayah, don't play basmallah
        return null;
      }
    }

    return String.format(Locale.US, baseUrl, sura, ayah);
  }

  public String getTitle(Context context, QuranInfo quranInfo) {
    return quranInfo.getSuraAyahString(context, currentSura, currentAyah);
  }

  public int getCurrentSura() {
    return currentSura;
  }

  public int getCurrentAyah() {
    return currentAyah;
  }

  public boolean gotoNextAyah(QuranInfo quranInfo, boolean force) {
    // don't go to next ayah if we haven't played basmallah yet
    if (justPlayedBasmallah) {
      return false;
    }
    if (!force && repeatInfo.shouldRepeat()) {
      repeatInfo = repeatInfo.incrementRepeat();
      if (currentAyah == 1 && currentSura != 1 && currentSura != 9) {
        justPlayedBasmallah = true;
      }
      return false;
    }

    if (enforceBounds && ((currentSura > maxSura) ||
        (currentAyah >= maxAyah && currentSura == maxSura))) {
      if (rangeRepeatInfo.shouldRepeat()) {
        rangeRepeatInfo = rangeRepeatInfo.incrementRepeat();
        currentSura = minSura;
        currentAyah = minAyah;
        repeatInfo = repeatInfo.setCurrentVerse(currentSura, currentAyah);
        return true;
      }
    }

    currentAyah++;
    if (ayahsInThisSura < currentAyah) {
      currentAyah = 1;
      currentSura++;
      if (currentSura <= 114) {
        ayahsInThisSura = quranInfo.getNumAyahs(currentSura);
        repeatInfo = repeatInfo.setCurrentVerse(currentSura, currentAyah);
      }
    } else {
      repeatInfo = repeatInfo.setCurrentVerse(currentSura, currentAyah);
    }
    return true;
  }

  public void gotoPreviousAyah(QuranInfo quranInfo) {
    currentAyah--;
    if (currentAyah < 1) {
      currentSura--;
      if (currentSura > 0) {
        ayahsInThisSura = quranInfo.getNumAyahs(currentSura);
        currentAyah = ayahsInThisSura;
      }
    } else if (currentAyah == 1 && !isGapless()) {
      justPlayedBasmallah = true;
    }

    if (currentSura > 0 && currentAyah > 0) {
      repeatInfo = repeatInfo.setCurrentVerse(currentSura, currentAyah);
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(this.baseUrl);
    dest.writeString(this.gaplessDatabasePath);
    dest.writeInt(this.ayahsInThisSura);
    dest.writeInt(this.minSura);
    dest.writeInt(this.minAyah);
    dest.writeInt(this.maxSura);
    dest.writeInt(this.maxAyah);
    dest.writeInt(this.currentSura);
    dest.writeInt(this.currentAyah);
    dest.writeParcelable(this.rangeRepeatInfo, 0);
    dest.writeByte(enforceBounds ? (byte) 1 : (byte) 0);
    dest.writeByte(justPlayedBasmallah ? (byte) 1 : (byte) 0);
    dest.writeParcelable(this.repeatInfo, 0);
  }

}
