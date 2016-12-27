package com.quran.labs.androidquran.common;

import android.graphics.Bitmap;

public class Response {
  public static final int ERROR_SD_CARD_NOT_FOUND = 1;
  public static final int ERROR_FILE_NOT_FOUND = 2;
  public static final int ERROR_DOWNLOADING_ERROR = 3;
  public static final int WARN_SD_CARD_NOT_FOUND = 4;
  public static final int WARN_COULD_NOT_SAVE_FILE = 5;

  private Bitmap bitmap;
  private int errorCode;
  private int pageNumber;

  public Response(Bitmap bitmap) {
    this.bitmap = bitmap;
  }

  public Response(Bitmap bitmap, int warningCode) {
    this.bitmap = bitmap;
    // we currently ignore warnings
  }

  public Response(int errorCode) {
    this.errorCode = errorCode;
  }

  public int getPageNumber() {
    return pageNumber;
  }

  public void setPageData(int pageNumber) {
    this.pageNumber = pageNumber;
  }

  public Bitmap getBitmap() {
    return bitmap;
  }

  public boolean isSuccessful() {
    return errorCode == 0;
  }

  public int getErrorCode() {
    return errorCode;
  }
}
