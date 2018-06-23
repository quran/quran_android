package com.quran.labs.androidquran.service.util;

import android.os.Parcel;
import android.support.annotation.NonNull;

import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.SuraAyah;

public class StreamingAudioRequest extends DownloadAudioRequest {

  public StreamingAudioRequest(String baseUrl, SuraAyah verse, @NonNull
      QariItem qariItem, String localPath, int versesInThisSura) {
    super(baseUrl, verse, qariItem, localPath, versesInThisSura);
  }

  private StreamingAudioRequest(Parcel in) {
    super(in);
  }

  @Override
  public boolean haveSuraAyah(int sura, int ayah) {
    // for streaming, we (theoretically) always "have" the sura and ayah
    return true;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    super.writeToParcel(dest, flags);
  }

  public static final Creator<StreamingAudioRequest> CREATOR
      = new Creator<StreamingAudioRequest>() {
    @Override
    public StreamingAudioRequest createFromParcel(Parcel source) {
      return new StreamingAudioRequest(source);
    }

    @Override
    public StreamingAudioRequest[] newArray(int size) {
      return new StreamingAudioRequest[size];
    }
  };
}
