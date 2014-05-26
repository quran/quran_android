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
import android.widget.Toast;

public class AyahToolBar extends LinearLayout implements
    View.OnClickListener, View.OnLongClickListener {
  private Menu mMenu;
  private Context mContext;
  private int mItemWidth;
  private int mCurrentMenu;
  private OnItemSelectedListener mItemSelectedListener;
  private boolean mBookmarked;

  public interface OnItemSelectedListener {
    void onItemSelected(int itemId);
  }

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
    final Resources resources = context.getResources();
    mItemWidth = resources.getDimensionPixelSize(R.dimen.toolbar_item_width);
    setOrientation(LinearLayout.HORIZONTAL);
    showMenu(R.menu.ayah_menu);
  }

  private void showMenu(int menuId) {
    mMenu = new MenuBuilder(mContext);
    final MenuInflater inflater = new MenuInflater(mContext);
    inflater.inflate(menuId, mMenu);
    mCurrentMenu = menuId;

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
    button.setOnClickListener(this);
    button.setOnLongClickListener(this);
    return button;
  }

  public int getToolBarWidth() {
    final int width = getWidth();
    return width > 0 ? width : mMenu.size() * mItemWidth;
  }

  public void setBookmarked(boolean bookmarked) {
    mBookmarked = bookmarked;
    if (mCurrentMenu == R.menu.ayah_menu) {
      MenuItem bookmarkItem = mMenu.findItem(R.id.cab_bookmark_ayah);
      bookmarkItem.setIcon(bookmarked ? R.drawable.favorite : R.drawable.not_favorite);
      ImageButton bookmarkButton = (ImageButton) findViewById(R.id.cab_bookmark_ayah);
      if (bookmarkButton != null) {
        bookmarkButton.setImageDrawable(bookmarkItem.getIcon());
      }
    }
  }

  public void updatePosition(float x, float y) {
    setX(x);
    setY(y);
  }

  public boolean isShowing() {
    return this.getVisibility() == VISIBLE;
  }

  public void showMenu() {
    if (mCurrentMenu != R.menu.ayah_menu) {
      showMenu(R.menu.ayah_menu);
    }
    this.setVisibility(VISIBLE);
  }

  public void hideMenu() {
    this.setVisibility(GONE);
  }

  public void setOnItemSelectedListener(OnItemSelectedListener listener) {
    mItemSelectedListener = listener;
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.cab_share_ayah) {
      showMenu(R.menu.ayah_menu_share);
    }
    if (mItemSelectedListener != null) {
      mItemSelectedListener.onItemSelected(v.getId());
    }
  }

  @Override
  public boolean onLongClick(View v) {
    MenuItem item = mMenu.findItem(v.getId());
    if (item != null && item.getTitle() != null) {
      Toast.makeText(this.getContext(), item.getTitle(), Toast.LENGTH_SHORT).show();
      return true;
    }
    return false;
  }

}
