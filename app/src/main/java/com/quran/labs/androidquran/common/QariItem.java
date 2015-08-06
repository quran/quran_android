package com.quran.labs.androidquran.common;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

public class QariItem implements Parcelable {
  private final int mId;
  @NonNull private final String mName;
  @NonNull private final String mUrl;
  @NonNull private final String mPath;
  @Nullable private final String mDatabaseName;

  public QariItem(int id, @NonNull String name, @NonNull String url,
      @NonNull String path, @Nullable String databaseName) {
    mId = id;
    mName = name;
    mUrl = url;
    mPath = path;
    mDatabaseName = TextUtils.isEmpty(databaseName) ? null : databaseName;
  }

  protected QariItem(Parcel in) {
    this.mId = in.readInt();
    this.mName = in.readString();
    this.mUrl = in.readString();
    this.mPath = in.readString();
    this.mDatabaseName = in.readString();
  }

  public int getId() {
    return mId;
  }

  public boolean isGapless() {
    return mDatabaseName != null;
  }

  @NonNull
  public String getName() {
    return mName;
  }

  @NonNull
  public String getUrl() {
    return mUrl;
  }

  @NonNull
  public String getPath() {
    return mPath;
  }

  @Nullable
  public String getDatabaseName() {
    return mDatabaseName;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(this.mId);
    dest.writeString(this.mName);
    dest.writeString(this.mUrl);
    dest.writeString(this.mPath);
    dest.writeString(this.mDatabaseName);
  }

  public static final Parcelable.Creator<QariItem> CREATOR =
      new Parcelable.Creator<QariItem>() {
    public QariItem createFromParcel(Parcel source) {
      return new QariItem(source);
    }

    public QariItem[] newArray(int size) {
      return new QariItem[size];
    }
  };
}
