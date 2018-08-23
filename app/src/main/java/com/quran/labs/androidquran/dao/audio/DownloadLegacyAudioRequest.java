package com.quran.labs.androidquran.dao.audio;

import android.os.Parcel;
import androidx.annotation.NonNull;

import com.quran.labs.androidquran.common.QariItem;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.util.AudioUtils;

public class DownloadLegacyAudioRequest extends LegacyAudioRequest {

  @NonNull private final QariItem qariItem;
  private String localDirectoryPath = null;

  public DownloadLegacyAudioRequest(String baseUrl, SuraAyah verse,
                                    @NonNull QariItem qariItem, String localPath, int versesInThisSura) {
    super(baseUrl, verse, versesInThisSura);
    this.qariItem = qariItem;
    localDirectoryPath = localPath;
  }

  DownloadLegacyAudioRequest(Parcel in) {
    super(in);
    this.qariItem = in.readParcelable(QariItem.class.getClassLoader());
    this.localDirectoryPath = in.readString();
  }

  @Override
  public boolean haveSuraAyah(int sura, int ayah) {
    return AudioUtils.Companion.haveSuraAyahForQari(localDirectoryPath, sura, ayah);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    super.writeToParcel(dest, flags);
    dest.writeParcelable(this.qariItem, 0);
    dest.writeString(this.localDirectoryPath);
  }

  public static final Creator<DownloadLegacyAudioRequest> CREATOR = new Creator<DownloadLegacyAudioRequest>() {
    @Override
    public DownloadLegacyAudioRequest createFromParcel(Parcel source) {
      return new DownloadLegacyAudioRequest(source);
    }

    @Override
    public DownloadLegacyAudioRequest[] newArray(int size) {
      return new DownloadLegacyAudioRequest[size];
    }
  };
}
