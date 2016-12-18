package com.quran.labs.androidquran.dao;

import android.os.Parcel;

public class Tag {

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
}
