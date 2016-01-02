package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class QuranHeaderView extends ViewGroup {

  private View mTitle;
  private View mPageNumber;
  private boolean mIsRtl;

  public QuranHeaderView(Context context) {
    this(context, null);
  }

  public QuranHeaderView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public QuranHeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    mIsRtl = QuranSettings.getInstance(context).isArabicNames() || QuranUtils.isRtl();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    mTitle = getChildAt(0);
    mPageNumber = getChildAt(1);

    measureChildWithMargins(mPageNumber, widthMeasureSpec, 0, heightMeasureSpec, 0);
    measureChildWithMargins(mTitle, widthMeasureSpec,
        mPageNumber.getMeasuredWidth(), heightMeasureSpec, 0);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    View left = mTitle;
    View right = mPageNumber;
    if (mIsRtl) {
      left = mPageNumber;
      right = mTitle;
    }

    int top = ((b - t) - mTitle.getMeasuredHeight()) / 2;
    left.layout(getPaddingLeft(),
        top, getPaddingLeft() + left.getMeasuredWidth(), top + left.getMeasuredHeight());
    top = ((b - t) - mPageNumber.getMeasuredHeight()) / 2;
    right.layout(r - (right.getMeasuredWidth() + getPaddingRight()),
        top, r - getPaddingRight(), top + right.getMeasuredHeight());
  }

  @Override
  public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
    return new MarginLayoutParams(getContext(), attrs);
  }

  @Override
  protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
    return new MarginLayoutParams(p);
  }

  @Override
  protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
    return new MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
  }
}
