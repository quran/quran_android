package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.util.AudioUtils;

import android.os.Parcel;
import android.support.annotation.NonNull;

public class DownloadAudioRequest extends AudioRequest {

  @NonNull private final QariItem mQariItem;
  private String mLocalDirectoryPath = null;

  public DownloadAudioRequest(String baseUrl, QuranAyah verse,
      @NonNull QariItem qariItem, String localPath) {
    super(baseUrl, verse);
    mQariItem = qariItem;
    mLocalDirectoryPath = localPath;
  }

  protected DownloadAudioRequest(Parcel in) {
    super(in);
    this.mQariItem = in.readParcelable(QariItem.class.getClassLoader());
    this.mLocalDirectoryPath = in.readString();
  }
  
  @NonNull
  public QariItem getQariItem() {
    return mQariItem;
  }

  public String getLocalPath() {
    return mLocalDirectoryPath;
  }

  @Override
  public boolean haveSuraAyah(int sura, int ayah) {
    return AudioUtils.haveSuraAyahForQari(mLocalDirectoryPath, sura, ayah);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    super.writeToParcel(dest, flags);
    dest.writeParcelable(this.mQariItem, 0);
    dest.writeString(this.mLocalDirectoryPath);
  }

  public static final Creator<DownloadAudioRequest> CREATOR = new Creator<DownloadAudioRequest>() {
    public DownloadAudioRequest createFromParcel(Parcel source) {
      return new DownloadAudioRequest(source);
    }

    public DownloadAudioRequest[] newArray(int size) {
      return new DownloadAudioRequest[size];
    }
  };
}
