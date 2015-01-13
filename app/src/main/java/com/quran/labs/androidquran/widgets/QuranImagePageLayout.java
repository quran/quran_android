package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.util.PageController;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class QuranImagePageLayout extends QuranPageLayout {
  private HighlightingImageView mImageView;

  public QuranImagePageLayout(Context context) {
    super(context);
  }

  @Override
  protected View generateContentView(Context context) {
    mImageView = new HighlightingImageView(context);
    return mImageView;
  }

  @Override
  protected void setContentNightMode(boolean nightMode, int textBrightness) {
    mImageView.setNightMode(nightMode, textBrightness);
  }

  public HighlightingImageView getImageView() {
    return mImageView;
  }

  @Override
  public void setPageController(PageController controller, int pageNumber) {
    super.setPageController(controller, pageNumber);
    final GestureDetector gestureDetector = new GestureDetector(mContext,
        new PageGestureDetector());
    OnTouchListener gestureListener = new OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
      }
    };
    mImageView.setOnTouchListener(gestureListener);
    mImageView.setClickable(true);
    mImageView.setLongClickable(true);
  }

  private class PageGestureDetector extends
      GestureDetector.SimpleOnGestureListener {

    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
      return mPageController.handleTouchEvent(event,
          AyahSelectedListener.EventType.SINGLE_TAP, mPageNumber);
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
      return mPageController.handleTouchEvent(event,
          AyahSelectedListener.EventType.DOUBLE_TAP, mPageNumber);
    }

    @Override
    public void onLongPress(MotionEvent event) {
      mPageController.handleTouchEvent(event,
          AyahSelectedListener.EventType.LONG_PRESS, mPageNumber);
    }
  }
}
