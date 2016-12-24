package com.quran.labs.androidquran.ui.helpers;

import android.graphics.drawable.BitmapDrawable;

import com.quran.labs.androidquran.common.Response;

public interface PageDownloadListener {
  void onLoadImageResponse(BitmapDrawable drawable, Response response);
}
