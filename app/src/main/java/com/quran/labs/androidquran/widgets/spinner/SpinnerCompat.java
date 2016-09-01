/*
 * Copyright (C) 2007 The Android Open Source Project
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
 */

package com.quran.labs.androidquran.widgets.spinner;

import android.content.Context;
import android.support.v7.widget.AppCompatSpinner;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SpinnerAdapter;

public class SpinnerCompat extends AppCompatSpinner {
  private static final int MAX_ITEMS_MEASURED = 15;

  private SpinnerAdapter adapter;

  public SpinnerCompat(Context context) {
    super(context);
  }

  public SpinnerCompat(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SpinnerCompat(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @Override
  public void setAdapter(SpinnerAdapter adapter) {
    super.setAdapter(adapter);
    this.adapter = adapter;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
      int calculatedWidth = calculateWidth();
      int measuredWidth = getMeasuredWidth();
      if (calculatedWidth > measuredWidth) {
        setMeasuredDimension(
            Math.min(calculatedWidth, MeasureSpec.getSize(widthMeasureSpec)),
            getMeasuredHeight());
        setDropDownWidth(calculatedWidth);
      }
    }
  }

  private int calculateWidth() {
    if (adapter == null) {
      return 0;
    }

    int width = 0;
    View itemView = null;
    int itemType = 0;
    final int widthMeasureSpec =
        MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.UNSPECIFIED);
    final int heightMeasureSpec =
        MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.UNSPECIFIED);

    // Make sure the number of items we'll measure is capped. If it's a huge data set
    // with wildly varying sizes, oh well.
    final int end = adapter.getCount();
    int start = Math.max(end - MAX_ITEMS_MEASURED, 0);
    for (int i = start; i < end; i++) {
      final int positionType = adapter.getItemViewType(i);
      if (positionType != itemType) {
        itemType = positionType;
        itemView = null;
      }
      itemView = adapter.getView(i, itemView, this);
      if (itemView.getLayoutParams() == null) {
        itemView.setLayoutParams(new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT));
      }
      itemView.measure(widthMeasureSpec, heightMeasureSpec);
      width = Math.max(width, itemView.getMeasuredWidth());
    }
    width *= 1.1; // add some extra spacing
    return width;
  }
}
