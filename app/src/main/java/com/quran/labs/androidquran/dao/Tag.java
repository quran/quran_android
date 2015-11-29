package com.quran.labs.androidquran.dao;

import android.os.Parcel;
import android.os.Parcelable;
import android.widget.Checkable;

public class Tag implements Checkable, Parcelable {

  public long id;
  public String name;
  private boolean isChecked = false;

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
  public boolean isChecked() {
    return isChecked;
  }

  @Override
  public void setChecked(boolean checked) {
    isChecked = checked;
  }

  @Override
  public void toggle() {
    isChecked = !isChecked;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(id);
    dest.writeString(name);
    dest.writeByte((byte) (isChecked ? 1 : 0));
  }

  public void readFromParcel(Parcel parcel) {
    id = parcel.readLong();
    name = parcel.readString();
    isChecked = parcel.readByte() == 1;
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
