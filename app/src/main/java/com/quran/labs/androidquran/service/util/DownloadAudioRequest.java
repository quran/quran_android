package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.util.AudioUtils;

import android.os.Parcel;

public class DownloadAudioRequest extends AudioRequest {

  private int mQariId = -1;
  private String mLocalDirectoryPath = null;

  public DownloadAudioRequest(String baseUrl, QuranAyah verse,
      int qariId, String localPath) {
    super(baseUrl, verse);
    mQariId = qariId;
    mLocalDirectoryPath = localPath;
  }

  protected DownloadAudioRequest(Parcel in) {
    super(in);
    this.mQariId = in.readInt();
    this.mLocalDirectoryPath = in.readString();
  }

  public int getQariId() {
    return mQariId;
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
    dest.writeInt(this.mQariId);
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
