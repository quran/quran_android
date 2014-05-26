package com.quran.labs.androidquran.widgets;

import com.actionbarsherlock.internal.view.menu.MenuBuilder;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

public class AyahToolBar extends LinearLayout {
  private Menu mMenu;
  private Context mContext;
  private int mItemWidth;

  public AyahToolBar(Context context) {
    super(context);
    init(context);
  }

  public AyahToolBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init(context);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public AyahToolBar(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  private void init(Context context) {
    mContext = context;
    mMenu = new MenuBuilder(context);
    final Resources resources = context.getResources();
    mItemWidth = resources.getDimensionPixelSize(R.dimen.toolbar_item_width);
    final MenuInflater inflater = new MenuInflater(context);
    inflater.inflate(R.menu.ayah_menu, mMenu);

    setOrientation(LinearLayout.HORIZONTAL);
    showMenu();
  }

  private void showMenu() {
    removeAllViews();
    final int count = mMenu.size();
    for (int i=0; i<count; i++) {
      final MenuItem item = mMenu.getItem(i);
      if (item.isVisible()) {
        final View view = getMenuItemView(item);
        addView(view);
      }
    }
  }

  private View getMenuItemView(MenuItem item) {
    final ImageButton button = new ImageButton(mContext);
    button.setImageDrawable(item.getIcon());
    button.setBackgroundResource(R.drawable.toolbar_button);
    button.setId(item.getItemId());
    button.setLayoutParams(new LayoutParams(mItemWidth,
        ViewGroup.LayoutParams.MATCH_PARENT));
    return button;
  }

  public int getToolBarWidth() {
    final int width = getWidth();
    return width > 0 ? width : mMenu.size() * mItemWidth;
  }
}
