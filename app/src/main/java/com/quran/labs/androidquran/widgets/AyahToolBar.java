package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

public class AyahToolBar extends ViewGroup implements
    View.OnClickListener, View.OnLongClickListener {
  public static enum PipPosition { UP, DOWN };
  private static boolean sHoneycombPlus =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;

  private Context mContext;
  private Menu mMenu;
  private Menu mCurrentMenu;
  private int mItemWidth;
  private int mPipWidth;
  private int mPipHeight;
  private boolean mIsShowing;
  private float mPipOffset;
  private LinearLayout mMenuLayout;
  private AyahToolBarPip mToolBarPip;
  private PipPosition mPipPosition;
  private AyahToolBarPosition mLastAyahToolBarPosition;
  private MenuItem.OnMenuItemClickListener mItemSelectedListener;

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
    final int toolBarHeight =
        resources.getDimensionPixelSize(R.dimen.toolbar_height);
    mPipHeight = resources.getDimensionPixelSize(R.dimen.toolbar_pip_height);
    mPipWidth = resources.getDimensionPixelSize(R.dimen.toolbar_pip_width);
    final int background = resources.getColor(R.color.toolbar_background);

    mMenuLayout = new LinearLayout(context);
    mMenuLayout.setLayoutParams(
        new LayoutParams(LayoutParams.MATCH_PARENT, toolBarHeight));
    mMenuLayout.setBackgroundColor(background);
    addView(mMenuLayout);

    mPipPosition = PipPosition.DOWN;
    mToolBarPip = new AyahToolBarPip(context);
    mToolBarPip.setLayoutParams(
        new LayoutParams(LayoutParams.WRAP_CONTENT, mPipHeight));
    addView(mToolBarPip);

    mMenu = new MenuBuilder(mContext);
    final MenuInflater inflater = new MenuInflater(mContext);
    inflater.inflate(R.menu.ayah_menu, mMenu);
    showMenu(mMenu);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int totalWidth = getMeasuredWidth();
    final int pipWidth = mToolBarPip.getMeasuredWidth();
    final int pipHeight = mToolBarPip.getMeasuredHeight();
    final int menuWidth = mMenuLayout.getMeasuredWidth();
    final int menuHeight = mMenuLayout.getMeasuredHeight();

    int pipLeft = (int) mPipOffset;
    if ((pipLeft + pipWidth) > totalWidth) {
      pipLeft = (totalWidth / 2) - (pipWidth / 2);
    }

    // overlap the pip and toolbar by 1px to avoid occasional gap
    if (mPipPosition == PipPosition.UP) {
      mToolBarPip.layout(pipLeft, 0, pipLeft + pipWidth, pipHeight + 1);
      mMenuLayout.layout(0, pipHeight, menuWidth, pipHeight + menuHeight);
    } else {
      mToolBarPip.layout(pipLeft, menuHeight - 1,
          pipLeft + pipWidth, menuHeight + pipHeight);
      mMenuLayout.layout(0, 0, menuWidth, menuHeight);
    }

    if (!sHoneycombPlus) {
      setPositionEclairMr1();
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    measureChild(mMenuLayout, widthMeasureSpec, heightMeasureSpec);
    final int width = mMenuLayout.getMeasuredWidth();
    int height = mMenuLayout.getMeasuredHeight();
    measureChild(mToolBarPip,
        MeasureSpec.makeMeasureSpec(mPipWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(mPipHeight, MeasureSpec.EXACTLY));
    height += mToolBarPip.getMeasuredHeight();
    setMeasuredDimension(resolveSize(width, widthMeasureSpec),
        resolveSize(height, heightMeasureSpec));
  }

  private void showMenu(Menu menu) {
    if (mCurrentMenu == menu) {
      // no need to re-draw
      return;
    }

    mMenuLayout.removeAllViews();
    final int count = menu.size();
    for (int i=0; i<count; i++) {
      final MenuItem item = menu.getItem(i);
      if (item.isVisible()) {
        final View view = getMenuItemView(item);
        mMenuLayout.addView(view);
      }
    }

    mCurrentMenu = menu;
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
    // relying on getWidth() may give us the width of a shorter
    // submenu instead of the actual menu
    return mMenu.size() * mItemWidth;
  }

  public void setBookmarked(boolean bookmarked) {
    MenuItem bookmarkItem = mMenu.findItem(R.id.cab_bookmark_ayah);
    bookmarkItem.setIcon(bookmarked ?
        R.drawable.ic_favorite : R.drawable.ic_not_favorite);
    ImageButton bookmarkButton =
        (ImageButton) findViewById(R.id.cab_bookmark_ayah);
    if (bookmarkButton != null) {
      bookmarkButton.setImageDrawable(bookmarkItem.getIcon());
    }
  }

  public void updatePosition(AyahToolBarPosition position) {
    boolean needsLayout = position.pipPosition != mPipPosition ||
        mPipOffset != position.pipOffset;
    ensurePipPosition(position.pipPosition);
    mPipOffset = position.pipOffset;
    mLastAyahToolBarPosition = position;
    float x = position.x + position.xScroll;
    float y = position.y + position.yScroll;
    if (sHoneycombPlus) {
      setPositionHoneycomb(x, y);
    } else if (!needsLayout) {
      // if we need layout, layout will adjust the position anyway
      setPositionEclairMr1();
    }

    if (needsLayout) {
      requestLayout();
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void setPositionHoneycomb(float x, float y) {
    setTranslationX(x);
    setTranslationY(y);
  }

  private void setPositionEclairMr1() {
    if (mLastAyahToolBarPosition != null) {
      float x = mLastAyahToolBarPosition.x + mLastAyahToolBarPosition.xScroll;
      float y = mLastAyahToolBarPosition.y + mLastAyahToolBarPosition.yScroll;
      final int deltaY = (int) (y - getTop());
      final int deltaX = (int) (x - getLeft());
      offsetTopAndBottom(deltaY);
      offsetLeftAndRight(deltaX);
    }
  }

  private void ensurePipPosition(PipPosition position) {
    mPipPosition = position;
    mToolBarPip.ensurePosition(position);
  }

  public boolean isShowing() {
    return mIsShowing;
  }

  public void resetMenu() {
    showMenu(mMenu);
  }

  public void showMenu() {
    showMenu(mMenu);
    setVisibility(VISIBLE);
    mIsShowing = true;
  }

  public void hideMenu() {
    mIsShowing = false;
    setVisibility(GONE);
  }

  public void setOnItemSelectedListener(
      MenuItem.OnMenuItemClickListener listener) {
    mItemSelectedListener = listener;
  }

  @Override
  public void onClick(View v) {
    final MenuItem item = mMenu.findItem(v.getId());
    if (item == null) return;
    if (item.hasSubMenu()) {
      showMenu(item.getSubMenu());
    } else if (mItemSelectedListener != null) {
      mItemSelectedListener.onMenuItemClick(item);
    }
  }

  @Override
  public boolean onLongClick(View v) {
    MenuItem item = mMenu.findItem(v.getId());
    if (item != null && item.getTitle() != null) {
      Toast.makeText(mContext, item.getTitle(), Toast.LENGTH_SHORT).show();
      return true;
    }
    return false;
  }

  public static class AyahToolBarPosition {
    public float x;
    public float y;
    public float xScroll;
    public float yScroll;
    public float pipOffset;
    public PipPosition pipPosition;
  }
}
