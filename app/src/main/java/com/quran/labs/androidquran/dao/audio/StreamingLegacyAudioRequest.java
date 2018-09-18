package com.quran.labs.androidquran.dao.audio;

import android.os.Parcel;
import androidx.annotation.NonNull;

import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.SuraAyah;

public class StreamingLegacyAudioRequest extends DownloadLegacyAudioRequest {

  public StreamingLegacyAudioRequest(String baseUrl, SuraAyah verse, @NonNull
      QariItem qariItem, String localPath, int versesInThisSura) {
    super(baseUrl, verse, qariItem, localPath, versesInThisSura);
  }

  private StreamingLegacyAudioRequest(Parcel in) {
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

  public static final Creator<StreamingLegacyAudioRequest> CREATOR
      = new Creator<StreamingLegacyAudioRequest>() {
    @Override
    public StreamingLegacyAudioRequest createFromParcel(Parcel source) {
      return new StreamingLegacyAudioRequest(source);
    }

    @Override
    public StreamingLegacyAudioRequest[] newArray(int size) {
      return new StreamingLegacyAudioRequest[size];
    }
  };
}
