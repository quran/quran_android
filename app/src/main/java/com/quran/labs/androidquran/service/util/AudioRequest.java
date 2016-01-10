package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.Locale;

import timber.log.Timber;

public abstract class AudioRequest implements Parcelable {

  private String mBaseUrl = null;
  private String mGaplessDatabasePath = null;

  // where we started from
  private int mAyahsInThisSura = 0;

  // min and max sura/ayah
  private int mMinSura;
  private int mMinAyah;
  private int mMaxSura;
  private int mMaxAyah;

  // what we're currently playing
  private int mCurrentSura = 0;
  private int mCurrentAyah = 0;

  // range repeat info
  private RepeatInfo mRangeRepeatInfo;
  private boolean mEnforceBounds;

  // did we just play the basmallah?
  private boolean mJustPlayedBasmallah = false;

  // repeat information
  private RepeatInfo mRepeatInfo;

  public abstract boolean haveSuraAyah(int sura, int ayah);

  public AudioRequest(String baseUrl, QuranAyah verse) {
    mBaseUrl = baseUrl;
    final int startSura = verse.getSura();
    final int startAyah = verse.getAyah();

    if (startSura < 1 || startSura > 114 || startAyah < 1) {
      throw new IllegalArgumentException();
    }

    mCurrentSura = startSura;
    mCurrentAyah = startAyah;
    mAyahsInThisSura = QuranInfo.SURA_NUM_AYAHS[mCurrentSura - 1];

    mRepeatInfo = new RepeatInfo(0);
    mRepeatInfo.setCurrentVerse(mCurrentSura, mCurrentAyah);

    mRangeRepeatInfo = new RepeatInfo(0);
  }

  protected AudioRequest(Parcel in) {
    this.mBaseUrl = in.readString();
    this.mGaplessDatabasePath = in.readString();
    this.mAyahsInThisSura = in.readInt();
    this.mMinSura = in.readInt();
    this.mMinAyah = in.readInt();
    this.mMaxSura = in.readInt();
    this.mMaxAyah = in.readInt();
    this.mCurrentSura = in.readInt();
    this.mCurrentAyah = in.readInt();
    this.mRangeRepeatInfo = in.readParcelable(RepeatInfo.class.getClassLoader());
    this.mEnforceBounds = in.readByte() != 0;
    this.mJustPlayedBasmallah = in.readByte() != 0;
    this.mRepeatInfo = in.readParcelable(RepeatInfo.class.getClassLoader());
  }

  public boolean needsIsti3athaAudio() {
    // TODO base this check on a boolean array in readers.xml
    return !isGapless() || mGaplessDatabasePath.contains("minshawi_murattal");
  }

  public void setGaplessDatabaseFilePath(String databaseFile) {
    mGaplessDatabasePath = databaseFile;
  }

  public String getGaplessDatabaseFilePath() {
    return mGaplessDatabasePath;
  }

  public boolean isGapless() {
    return mGaplessDatabasePath != null;
  }

  public void setVerseRepeatCount(int count) {
    mRepeatInfo.setRepeatCount(count);
  }

  public int getVerseRepeatCount() {
    return mRepeatInfo.getRepeatCount();
  }

  public void setRangeRepeatCount(int rangeRepeatCount) {
    mRangeRepeatInfo.setRepeatCount(rangeRepeatCount);
  }

  public void setEnforceBounds(boolean enforceBounds) {
    mEnforceBounds = enforceBounds;
  }

  public boolean shouldEnforceBounds() {
    return mEnforceBounds;
  }

  public int getRangeRepeatCount() {
    return mRangeRepeatInfo.getRepeatCount();
  }

  public SuraAyah getRangeStart() {
    return new SuraAyah(mMinSura, mMinAyah);
  }

  public SuraAyah getRangeEnd() {
    return new SuraAyah(mMaxSura, mMaxAyah);
  }

  public RepeatInfo getRepeatInfo() {
    return mRepeatInfo;
  }

  public QuranAyah setCurrentAyah(int sura, int ayah) {
    Timber.d("got setCurrentAyah of: " + sura + ":" + ayah);
    if (mRepeatInfo.shouldRepeat()) {
      mRepeatInfo.incrementRepeat();
    } else {
      mCurrentSura = sura;
      mCurrentAyah = ayah;
      if (mEnforceBounds &&
          ((mCurrentSura == mMaxSura && mCurrentAyah > mMaxAyah) ||
              (mCurrentSura > mMaxSura))) {
        if (mRangeRepeatInfo.shouldRepeat()) {
          mRangeRepeatInfo.incrementRepeat();
          mCurrentSura = mMinSura;
          mCurrentAyah = mMinAyah;
        } else {
          return null;
        }
      }

      if (mCurrentSura >= 1 && mCurrentSura <= 114) {
        mAyahsInThisSura = QuranInfo.SURA_NUM_AYAHS[mCurrentSura - 1];
      }
      mRepeatInfo.setCurrentVerse(mCurrentSura, mCurrentAyah);
    }
    return mRepeatInfo.getCurrentAyah();
  }

  public String getBaseUrl() {
    return mBaseUrl;
  }

  public void setPlayBounds(QuranAyah minVerse, QuranAyah maxVerse) {
    mMinSura = minVerse.getSura();
    mMinAyah = minVerse.getAyah();
    mMaxSura = maxVerse.getSura();
    mMaxAyah = maxVerse.getAyah();
  }

  public QuranAyah getMinAyah() {
    return new QuranAyah(mMinSura, mMinAyah);
  }

  public QuranAyah getMaxAyah() {
    return new QuranAyah(mMaxSura, mMaxAyah);
  }

  public String getUrl() {
    if (mEnforceBounds &&
        ((mMaxSura > 0 && mCurrentSura > mMaxSura)
            || (mMaxAyah > 0 && mCurrentAyah > mMaxAyah
            && mCurrentSura >= mMaxSura)
            || (mMinSura > 0 && mCurrentSura < mMinSura)
            || (mMinAyah > 0 && mCurrentAyah < mMinAyah
            && mCurrentSura <= mMinSura))) {
      return null;
    }

    if (mCurrentSura > 114 || mCurrentSura < 1) {
      return null;
    }

    if (isGapless()) {
      String url = String.format(Locale.US, mBaseUrl, mCurrentSura);
      Timber.d("isGapless, url: " + url);
      return url;
    }

    int sura = mCurrentSura;
    int ayah = mCurrentAyah;
    if (ayah == 1 && sura != 1 && sura != 9 && !mJustPlayedBasmallah) {
      mJustPlayedBasmallah = true;
      sura = 1;
      ayah = 1;
    } else {
      mJustPlayedBasmallah = false;
    }

    if (mJustPlayedBasmallah) {
      // really if "about to play" bismillah...
      if (!haveSuraAyah(mCurrentSura, mCurrentAyah)) {
        // if we don't have the first ayah, don't play basmallah
        return null;
      }
    }

    return String.format(Locale.US, mBaseUrl, sura, ayah);
  }

  public String getTitle(Context context) {
    return QuranInfo.getSuraAyahString(context, mCurrentSura, mCurrentAyah);
  }

  public int getCurrentSura() {
    return mCurrentSura;
  }

  public int getCurrentAyah() {
    return mCurrentAyah;
  }

  public boolean gotoNextAyah(boolean force) {
    // don't go to next ayah if we haven't played basmallah yet
    if (mJustPlayedBasmallah) {
      return false;
    }
    if (!force && mRepeatInfo.shouldRepeat()) {
      mRepeatInfo.incrementRepeat();
      if (mCurrentAyah == 1 && mCurrentSura != 1 && mCurrentSura != 9) {
        mJustPlayedBasmallah = true;
      }
      return false;
    }

    if (mEnforceBounds && ((mCurrentSura > mMaxSura) ||
        (mCurrentAyah >= mMaxAyah && mCurrentSura == mMaxSura))) {
      if (mRangeRepeatInfo.shouldRepeat()) {
        mRangeRepeatInfo.incrementRepeat();
        mCurrentSura = mMinSura;
        mCurrentAyah = mMinAyah;
        mRepeatInfo.setCurrentVerse(mCurrentSura, mCurrentAyah);
        return true;
      }
    }

    mCurrentAyah++;
    if (mAyahsInThisSura < mCurrentAyah) {
      mCurrentAyah = 1;
      mCurrentSura++;
      if (mCurrentSura <= 114) {
        mAyahsInThisSura = QuranInfo.SURA_NUM_AYAHS[mCurrentSura - 1];
        mRepeatInfo.setCurrentVerse(mCurrentSura, mCurrentAyah);
      }
    } else {
      mRepeatInfo.setCurrentVerse(mCurrentSura, mCurrentAyah);
    }
    return true;
  }

  public void gotoPreviousAyah() {
    mCurrentAyah--;
    if (mCurrentAyah < 1) {
      mCurrentSura--;
      if (mCurrentSura > 0) {
        mAyahsInThisSura = QuranInfo.SURA_NUM_AYAHS[mCurrentSura - 1];
        mCurrentAyah = mAyahsInThisSura;
      }
    } else if (mCurrentAyah == 1 && !isGapless()) {
      mJustPlayedBasmallah = true;
    }

    if (mCurrentSura > 0 && mCurrentAyah > 0) {
      mRepeatInfo.setCurrentVerse(mCurrentSura, mCurrentAyah);
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(this.mBaseUrl);
    dest.writeString(this.mGaplessDatabasePath);
    dest.writeInt(this.mAyahsInThisSura);
    dest.writeInt(this.mMinSura);
    dest.writeInt(this.mMinAyah);
    dest.writeInt(this.mMaxSura);
    dest.writeInt(this.mMaxAyah);
    dest.writeInt(this.mCurrentSura);
    dest.writeInt(this.mCurrentAyah);
    dest.writeParcelable(this.mRangeRepeatInfo, 0);
    dest.writeByte(mEnforceBounds ? (byte) 1 : (byte) 0);
    dest.writeByte(mJustPlayedBasmallah ? (byte) 1 : (byte) 0);
    dest.writeParcelable(this.mRepeatInfo, 0);
  }

}
