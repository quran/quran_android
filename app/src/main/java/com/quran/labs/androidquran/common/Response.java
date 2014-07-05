package com.quran.labs.androidquran.common;

import android.graphics.Bitmap;

public class Response {
  public static final int ERROR_SD_CARD_NOT_FOUND = 1;
  public static final int ERROR_FILE_NOT_FOUND = 2;
  public static final int ERROR_DOWNLOADING_ERROR = 3;
  public static final int WARN_SD_CARD_NOT_FOUND = 4;
  public static final int WARN_COULD_NOT_SAVE_FILE = 5;

  private Bitmap mBitmap;
  private int mWarningCode;
  private int mErrorCode;
  private int mPageNumber;

  public static Response lightResponse(Response r) {
    final Response resp;
    if (r != null) {
      resp = new Response(r.getWarningCode(), r.getErrorCode());
      resp.setPageNumber(r.getPageNumber());
    } else {
      resp = null;
    }

    return resp;
  }

  public static Response fromPage(int page) {
    final Response r = new Response();
    r.mPageNumber = page;
    return r;
  }

  public Response(Bitmap bitmap) {
    mBitmap = bitmap;
  }

  public Response(Bitmap bitmap, int warningCode) {
    mBitmap = bitmap;
    mWarningCode = warningCode;
  }

  public Response(int warningCode, int errorCode) {
    mWarningCode = warningCode;
    mErrorCode = errorCode;
  }

  public Response(int errorCode) {
    mErrorCode = errorCode;
  }

  private Response() {
  }

  public void setPageNumber(int pageNumber) {
    mPageNumber = pageNumber;
  }

  public int getPageNumber() {
    return mPageNumber;
  }

  public Bitmap getBitmap() {
    return mBitmap;
  }

  public boolean isSuccessful() {
    return mErrorCode == 0;
  }

  public int getWarningCode() {
    return mWarningCode;
  }

  public int getErrorCode() {
    return mErrorCode;
  }
}
