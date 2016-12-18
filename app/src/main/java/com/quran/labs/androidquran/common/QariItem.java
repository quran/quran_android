package com.quran.labs.androidquran.common;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

public class QariItem implements Parcelable {
  private final int id;
  @NonNull private final String name;
  @NonNull private final String url;
  @NonNull private final String path;
  @Nullable private final String databaseName;

  public QariItem(int id, @NonNull String name, @NonNull String url,
      @NonNull String path, @Nullable String databaseName) {
    this.id = id;
    this.name = name;
    this.url = url;
    this.path = path;
    this.databaseName = TextUtils.isEmpty(databaseName) ? null : databaseName;
  }

  private QariItem(Parcel in) {
    this.id = in.readInt();
    this.name = in.readString();
    this.url = in.readString();
    this.path = in.readString();
    this.databaseName = in.readString();
  }

  public int getId() {
    return id;
  }

  public boolean isGapless() {
    return databaseName != null;
  }

  @NonNull
  public String getName() {
    return name;
  }

  @NonNull
  public String getUrl() {
    return url;
  }

  @NonNull
  public String getPath() {
    return path;
  }

  @Nullable
  public String getDatabaseName() {
    return databaseName;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(this.id);
    dest.writeString(this.name);
    dest.writeString(this.url);
    dest.writeString(this.path);
    dest.writeString(this.databaseName);
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
