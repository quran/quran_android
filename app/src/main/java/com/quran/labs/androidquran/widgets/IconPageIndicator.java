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
import android.content.res.TypedArray;
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

  private static final int DEF_INDICATOR_HEIGHT = 3; // dp
  private static final int DEF_INDICATOR_COLOR = Color.WHITE;

  private final LinearLayout mIconsLayout;

  private ViewPager mViewPager;
  private OnPageChangeListener mListener;
  private OnClickListener mClickListener;
  private Runnable mIconSelector;
  private int mSelectedIndex;

  private float mSelectionOffset;
  private int mIndicatorColor;
  private int mIndicatorHeight;
  private final Paint mIndicatorPaint = new Paint();

  public IconPageIndicator(Context context) {
    this(context, null);
  }

  public IconPageIndicator(Context context, AttributeSet attrs) {
    super(context, attrs);
    setHorizontalScrollBarEnabled(false);

    mIconsLayout = new LeftToRightLinearLayout(context);
    addView(mIconsLayout, new LayoutParams(WRAP_CONTENT, MATCH_PARENT));

    // Set indicator attributes (to defaults)
    final float density = context.getResources().getDisplayMetrics().density;
    mIndicatorHeight = (int) (DEF_INDICATOR_HEIGHT * density + 0.5f);
    mIndicatorColor = DEF_INDICATOR_COLOR;
    // Read the user-specified values if set, otherwise use defaults
    if (attrs != null) {
      TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.IconPageIndicator);
      mIndicatorHeight = ta.getDimensionPixelSize(
          R.styleable.IconPageIndicator_indicatorHeight, mIndicatorHeight);
      mIndicatorColor = ta.getColor(
          R.styleable.IconPageIndicator_indicatorColor, mIndicatorColor);
      ta.recycle();
    }
    mIndicatorPaint.setColor(mIndicatorColor);
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
  public void onPageScrollStateChanged(int state) {
    if (mListener != null) {
      mListener.onPageScrollStateChanged(state);
    }
  }

  @Override
  public void onPageScrolled(int position, float offset, int offsetPixels) {
    mSelectionOffset = offset;
    mSelectedIndex = position;
    invalidate();
    if (mListener != null) {
      mListener.onPageScrolled(position, offset, offsetPixels);
    }
  }

  @Override
  public void onPageSelected(int position) {
    setCurrentItem(position);
    if (mListener != null) {
      mListener.onPageSelected(position);
    }
  }

  public void setViewPager(ViewPager view) {
    if (mViewPager == view) {
      return;
    }
    if (mViewPager != null) {
      mViewPager.addOnPageChangeListener(null);
    }
    PagerAdapter adapter = view.getAdapter();
    if (adapter == null) {
      throw new IllegalStateException("ViewPager does not have adapter instance.");
    }
    mViewPager = view;
    view.addOnPageChangeListener(this);
    notifyDataSetChanged();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int count = mIconsLayout.getChildCount();
    ImageView v = (ImageView) mIconsLayout.getChildAt(mSelectedIndex);
    final int bottom = v.getHeight();
    final int top = bottom - mIndicatorHeight;
    int left = v.getLeft();
    int right = v.getRight();

    if (mSelectedIndex + 1 < count) {
      View nextIcon = mIconsLayout.getChildAt(mSelectedIndex + 1);
      left = (int) (mSelectionOffset * nextIcon.getLeft() +
          (1.0f - mSelectionOffset) * left);
      right = (int) (mSelectionOffset * nextIcon.getRight() +
          (1.0f - mSelectionOffset) * right);
    }

    mIndicatorPaint.setColor(mIndicatorColor);
    canvas.drawRect(left, top, right, bottom, mIndicatorPaint);
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

  public void setViewPager(ViewPager view, int initialPosition) {
    setViewPager(view);
    setCurrentItem(initialPosition);
  }

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

  public void setOnPageChangeListener(OnPageChangeListener listener) {
    mListener = listener;
  }

  public void setOnClickListener(OnClickListener clickListener) {
    mClickListener = clickListener;
  }

  @Override
  public void onClick(View v) {
    if (mViewPager != null && v instanceof ImageView) {
      mViewPager.setCurrentItem((Integer) v.getTag());
    }
    if (mClickListener != null) {
      mClickListener.onClick(v);
    }
  }

  public interface IconPagerAdapter {
    int getIconResId(int index);
    int getCount();
  }

}
