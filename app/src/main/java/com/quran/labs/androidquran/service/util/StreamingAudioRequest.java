package com.quran.labs.androidquran.service.util;

import android.os.Parcel;

import com.quran.labs.androidquran.data.SuraAyah;

public class StreamingAudioRequest extends AudioRequest {

  public StreamingAudioRequest(String baseUrl, SuraAyah verse) {
    super(baseUrl, verse);
  }

  protected StreamingAudioRequest(Parcel in) {
    super(in);
  }

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
    public StreamingAudioRequest createFromParcel(Parcel source) {
      return new StreamingAudioRequest(source);
    }

    public StreamingAudioRequest[] newArray(int size) {
      return new StreamingAudioRequest[size];
    }
  };
}
