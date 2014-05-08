/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (C) 2012 Jake Wharton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications:
 * - use normal LinearLayout instead of IcsLinearLayout
 * - remove dependency on PageIndicator interface
 * - notifyDataSetChanged():
 *   - use actionButtonStyle to give icons button-like padding and selector
 *   - add click listener (and tag) and onClick update the view pager
 * - constructor: initialize the indicator stuff
 * - onDraw(): draw indicator below the selected item
 */

package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.quran.labs.androidquran.R;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * This widget implements the dynamic action bar tab behavior that can change
 * across different configurations or circumstances.
 */
public class IconPageIndicator extends HorizontalScrollView implements
    ViewPager.OnPageChangeListener, View.OnClickListener {

  private static final int INDICATOR_HEIGHT = 3; // dp
  private static final int INDICATOR_COLOR = Color.WHITE;

  private final LinearLayout mIconsLayout;

  private ViewPager mViewPager;
  private OnPageChangeListener mListener;
  private Runnable mIconSelector;
  private int mSelectedIndex;

  private int mIndicatorColor;
  private int mIndicatorHeight;
  private final Paint mIndicatorPaint = new Paint();

  public IconPageIndicator(Context context) {
    this(context, null);
  }

  public IconPageIndicator(Context context, AttributeSet attrs) {
    super(context, attrs);
    setHorizontalScrollBarEnabled(false);

    mIconsLayout = new LinearLayout(context);
    addView(mIconsLayout, new LayoutParams(WRAP_CONTENT, MATCH_PARENT));

    // INDICATOR STUFF

    mIndicatorColor = INDICATOR_COLOR;
    mIndicatorPaint.setColor(mIndicatorColor);
    final float density = context.getResources().getDisplayMetrics().density;
    mIndicatorHeight = (int) (INDICATOR_HEIGHT * density + 0.5f);
  }

  private void animateToIcon(final int position) {
    final View iconView = mIconsLayout.getChildAt(position);
    if (mIconSelector != null) {
      removeCallbacks(mIconSelector);
    }
    mIconSelector = new Runnable() {
      public void run() {
        final int scrollPos = iconView.getLeft() - (getWidth() - iconView.getWidth()) / 2;
        smoothScrollTo(scrollPos, 0);
        mIconSelector = null;
      }
    };
    post(mIconSelector);
  }

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    if (mIconSelector != null) {
      // Re-post the selector we saved
      post(mIconSelector);
    }
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    if (mIconSelector != null) {
      removeCallbacks(mIconSelector);
    }
  }

  @Override
  public void onPageScrollStateChanged(int arg0) {
    if (mListener != null) {
      mListener.onPageScrollStateChanged(arg0);
    }
  }

  @Override
  public void onPageScrolled(int arg0, float arg1, int arg2) {
    if (mListener != null) {
      mListener.onPageScrolled(arg0, arg1, arg2);
    }
  }

  @Override
  public void onPageSelected(int arg0) {
    setCurrentItem(arg0);
    if (mListener != null) {
      mListener.onPageSelected(arg0);
    }
  }

  //@Override
  public void setViewPager(ViewPager view) {
    if (mViewPager == view) {
      return;
    }
    if (mViewPager != null) {
      mViewPager.setOnPageChangeListener(null);
    }
    PagerAdapter adapter = view.getAdapter();
    if (adapter == null) {
      throw new IllegalStateException("ViewPager does not have adapter instance.");
    }
    mViewPager = view;
    view.setOnPageChangeListener(this);
    notifyDataSetChanged();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int count = mIconsLayout.getChildCount();
    for (int i = 0; i < count; i++) {
      ImageView v = (ImageView) mIconsLayout.getChildAt(i);
      if (v.isSelected()) {
        final int bottom = v.getHeight();
        final int top = bottom - mIndicatorHeight;
        final int left = v.getLeft();
        final int right = v.getRight();

        mIndicatorPaint.setColor(mIndicatorColor);
        canvas.drawRect(left, top, right, bottom, mIndicatorPaint);
      }
    }
  }

  public void notifyDataSetChanged() {
    mIconsLayout.removeAllViews();
    IconPagerAdapter iconAdapter = (IconPagerAdapter) mViewPager.getAdapter();
    int count = iconAdapter.getCount();
    for (int i = 0; i < count; i++) {
      ImageView view = new ImageView(getContext(), null, R.attr.actionButtonStyle);
      view.setImageResource(iconAdapter.getIconResId(i));
      view.setTag(i);
      view.setOnClickListener(this);
      mIconsLayout.addView(view, new LayoutParams(WRAP_CONTENT, MATCH_PARENT));
    }
    if (mSelectedIndex > count) {
      mSelectedIndex = count - 1;
    }
    setCurrentItem(mSelectedIndex);
    requestLayout();
  }

  //@Override
  public void setViewPager(ViewPager view, int initialPosition) {
    setViewPager(view);
    setCurrentItem(initialPosition);
  }

  //@Override
  public void setCurrentItem(int item) {
    if (mViewPager == null) {
      throw new IllegalStateException("ViewPager has not been bound.");
    }
    mSelectedIndex = item;
    mViewPager.setCurrentItem(item);

    int tabCount = mIconsLayout.getChildCount();
    for (int i = 0; i < tabCount; i++) {
      View child = mIconsLayout.getChildAt(i);
      boolean isSelected = (i == item);
      child.setSelected(isSelected);
      if (isSelected) {
        animateToIcon(item);
      }
    }
  }

  //@Override
  public void setOnPageChangeListener(OnPageChangeListener listener) {
    mListener = listener;
  }

  @Override
  public void onClick(View v) {
    if (mViewPager != null && v instanceof ImageView) {
      mViewPager.setCurrentItem((Integer) v.getTag());
    }
  }

  public interface IconPagerAdapter {
    int getIconResId(int index);
    int getCount();
  }

}
