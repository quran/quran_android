package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

public class ObservableScrollView extends ScrollView {

  private OnScrollListener mListener;

  public interface OnScrollListener {
    void onScrollChanged(ObservableScrollView scrollView, int x, int y, int oldx, int oldy);
  }

  public ObservableScrollView(Context context) {
    super(context);
  }

  public ObservableScrollView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ObservableScrollView(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  public void setOnScrollListener(OnScrollListener listener) {
    mListener = listener;
  }

  @Override
  protected void onScrollChanged(int l, int t, int oldl, int oldt) {
    super.onScrollChanged(l, t, oldl, oldt);
    if (mListener != null) {
      mListener.onScrollChanged(this, l, t, oldl, oldt);
    }
  }
}
