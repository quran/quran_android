package com.quran.labs.androidquran.dao;

import android.os.Parcel;
import android.os.Parcelable;

public class Tag implements Parcelable {

  public long id;
  public String name;

  public Tag(long id, String name) {
    this.id = id;
    this.name = name;
  }

  public Tag(Parcel parcel) {
    readFromParcel(parcel);
  }

  @Override
  public String toString() {
    return name == null ? super.toString() : name;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(id);
    dest.writeString(name);
  }

  public void readFromParcel(Parcel parcel) {
    id = parcel.readLong();
    name = parcel.readString();
  }

  public static final Creator<Tag> CREATOR =
      new Creator<Tag>() {
        public Tag createFromParcel(Parcel in) {
          return new Tag(in);
        }

        public Tag[] newArray(int size) {
          return new Tag[size];
        }
      };
}
