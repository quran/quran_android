package com.quran.labs.androidquran.dao;

import android.os.Parcel;
import android.os.Parcelable;
import android.widget.Checkable;

public class Tag implements Checkable, Parcelable {

  public long mId;
  public String mName;
  public boolean mChecked = false;

  public Tag(long id, String name) {
    mId = id;
    mName = name;
  }

  public Tag(Parcel parcel) {
    readFromParcel(parcel);
  }

  @Override
  public String toString() {
    return mName == null ? super.toString() : mName;
  }

  @Override
  public boolean isChecked() {
    return mChecked;
  }

  @Override
  public void setChecked(boolean checked) {
    mChecked = checked;
  }

  @Override
  public void toggle() {
    mChecked = !mChecked;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(mId);
    dest.writeString(mName);
    dest.writeByte((byte) (mChecked ? 1 : 0));
  }

  public void readFromParcel(Parcel parcel) {
    mId = parcel.readLong();
    mName = parcel.readString();
    mChecked = parcel.readByte() == 1;
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
