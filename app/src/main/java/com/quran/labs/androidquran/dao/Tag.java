package com.quran.labs.androidquran.dao;

import android.os.Parcel;
import android.os.Parcelable;

public class Tag implements Parcelable {

  public final long id;
  public final String name;

  public Tag(long id, String name) {
    this.id = id;
    this.name = name;
  }

  public Tag(Parcel parcel) {
    id = parcel.readLong();
    name = parcel.readString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Tag tag = (Tag) o;
    return id == tag.id && name.equals(tag.name);
  }

  @Override
  public int hashCode() {
    int result = (int) (id ^ (id >>> 32));
    result = 31 * result + name.hashCode();
    return result;
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
