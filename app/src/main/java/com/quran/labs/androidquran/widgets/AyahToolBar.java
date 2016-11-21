package com.quran.labs.androidquran.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.PopupMenu;
import android.util.AttributeSet;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.quran.labs.androidquran.R;

public class AyahToolBar extends ViewGroup implements
    View.OnClickListener, View.OnLongClickListener {
  public enum PipPosition { UP, DOWN }

  private Context context;
  private Menu menu;
  private Menu currentMenu;
  private int itemWidth;
  private int pipWidth;
  private int pipHeight;
  private boolean isShowing;
  private float pipOffset;
  private LinearLayout menuLayout;
  private AyahToolBarPip toolBarPip;
  private PipPosition pipPosition;
  private MenuItem.OnMenuItemClickListener itemSelectedListener;

  public AyahToolBar(Context context) {
    this(context, null);
  }

  public AyahToolBar(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public AyahToolBar(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    init(context);
  }

  private void init(Context context) {
    this.context = context;
    final Resources resources = context.getResources();
    itemWidth = resources.getDimensionPixelSize(R.dimen.toolbar_item_width);
    final int toolBarHeight = resources.getDimensionPixelSize(R.dimen.toolbar_height);
    pipHeight = resources.getDimensionPixelSize(R.dimen.toolbar_pip_height);
    pipWidth = resources.getDimensionPixelSize(R.dimen.toolbar_pip_width);
    final int background = ContextCompat.getColor(context, R.color.toolbar_background);

    menuLayout = new LinearLayout(context);
    menuLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, toolBarHeight));
    menuLayout.setBackgroundColor(background);
    addView(menuLayout);

    pipPosition = PipPosition.DOWN;
    toolBarPip = new AyahToolBarPip(context);
    toolBarPip.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, pipHeight));
    addView(toolBarPip);

    // used to use MenuBuilder, but now it has @RestrictTo, so using this clever trick from
    // StackOverflow - PopupMenu generates a new MenuBuilder internally, so this just lets us
    // get that menu and do whatever we want with it.
    menu = new PopupMenu(this.context, this).getMenu();
    final MenuInflater inflater = new MenuInflater(this.context);
    inflater.inflate(R.menu.ayah_menu, menu);
    showMenu(menu);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    final int totalWidth = getMeasuredWidth();
    final int pipWidth = toolBarPip.getMeasuredWidth();
    final int pipHeight = toolBarPip.getMeasuredHeight();
    final int menuWidth = menuLayout.getMeasuredWidth();
    final int menuHeight = menuLayout.getMeasuredHeight();

    int pipLeft = (int) pipOffset;
    if ((pipLeft + pipWidth) > totalWidth) {
      pipLeft = (totalWidth / 2) - (pipWidth / 2);
    }

    // overlap the pip and toolbar by 1px to avoid occasional gap
    if (pipPosition == PipPosition.UP) {
      toolBarPip.layout(pipLeft, 0, pipLeft + pipWidth, pipHeight + 1);
      menuLayout.layout(0, pipHeight, menuWidth, pipHeight + menuHeight);
    } else {
      toolBarPip.layout(pipLeft, menuHeight - 1, pipLeft + pipWidth, menuHeight + pipHeight);
      menuLayout.layout(0, 0, menuWidth, menuHeight);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    measureChild(menuLayout, widthMeasureSpec, heightMeasureSpec);
    final int width = menuLayout.getMeasuredWidth();
    int height = menuLayout.getMeasuredHeight();
    measureChild(toolBarPip,
        MeasureSpec.makeMeasureSpec(pipWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(pipHeight, MeasureSpec.EXACTLY));
    height += toolBarPip.getMeasuredHeight();
    setMeasuredDimension(resolveSize(width, widthMeasureSpec),
        resolveSize(height, heightMeasureSpec));
  }

  private void showMenu(Menu menu) {
    if (currentMenu == menu) {
      // no need to re-draw
      return;
    }

    menuLayout.removeAllViews();
    final int count = menu.size();
    for (int i=0; i<count; i++) {
      final MenuItem item = menu.getItem(i);
      if (item.isVisible()) {
        final View view = getMenuItemView(item);
        menuLayout.addView(view);
      }
    }

    currentMenu = menu;
  }

  private View getMenuItemView(MenuItem item) {
    final ImageButton button = new ImageButton(context);
    button.setImageDrawable(item.getIcon());
    button.setBackgroundResource(R.drawable.toolbar_button);
    button.setId(item.getItemId());
    button.setLayoutParams(new LayoutParams(itemWidth,
        ViewGroup.LayoutParams.MATCH_PARENT));
    button.setOnClickListener(this);
    button.setOnLongClickListener(this);
    return button;
  }

  public int getToolBarWidth() {
    // relying on getWidth() may give us the width of a shorter
    // submenu instead of the actual menu
    return menu.size() * itemWidth;
  }

  public void setBookmarked(boolean bookmarked) {
    MenuItem bookmarkItem = menu.findItem(R.id.cab_bookmark_ayah);
    bookmarkItem.setIcon(bookmarked ? R.drawable.ic_favorite : R.drawable.ic_not_favorite);
    ImageButton bookmarkButton = (ImageButton) findViewById(R.id.cab_bookmark_ayah);
    if (bookmarkButton != null) {
      bookmarkButton.setImageDrawable(bookmarkItem.getIcon());
    }
  }

  public void updatePosition(AyahToolBarPosition position) {
    boolean needsLayout = position.pipPosition != pipPosition || pipOffset != position.pipOffset;
    ensurePipPosition(position.pipPosition);
    pipOffset = position.pipOffset;
    float x = position.x + position.xScroll;
    float y = position.y + position.yScroll;
    setPosition(x, y);

    if (needsLayout) {
      requestLayout();
    }
  }

  private void setPosition(float x, float y) {
    setTranslationX(x);
    setTranslationY(y);
  }

  private void ensurePipPosition(PipPosition position) {
    pipPosition = position;
    toolBarPip.ensurePosition(position);
  }

  public boolean isShowing() {
    return isShowing;
  }

  public void resetMenu() {
    showMenu(menu);
  }

  public void showMenu() {
    showMenu(menu);
    setVisibility(VISIBLE);
    isShowing = true;
  }

  public void hideMenu() {
    isShowing = false;
    setVisibility(GONE);
  }

  public void setOnItemSelectedListener(
      MenuItem.OnMenuItemClickListener listener) {
    itemSelectedListener = listener;
  }

  @Override
  public void onClick(View v) {
    final MenuItem item = menu.findItem(v.getId());
    if (item == null) return;
    if (item.hasSubMenu()) {
      showMenu(item.getSubMenu());
    } else if (itemSelectedListener != null) {
      itemSelectedListener.onMenuItemClick(item);
    }
  }

  @Override
  public boolean onLongClick(View v) {
    MenuItem item = menu.findItem(v.getId());
    if (item != null && item.getTitle() != null) {
      Toast.makeText(context, item.getTitle(), Toast.LENGTH_SHORT).show();
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
