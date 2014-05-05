package com.quran.labs.androidquran.ui.helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.internal.view.menu.ActionMenuPresenter;
import com.actionbarsherlock.internal.view.menu.ActionMenuView;
import com.actionbarsherlock.internal.view.menu.MenuBuilder;
import com.actionbarsherlock.internal.view.menu.MenuItemImpl;
import com.actionbarsherlock.internal.view.menu.MenuPresenter;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.util.QuranAppUtils;
import com.quran.labs.androidquran.widgets.SlidingUpPanelLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AyahActionPanel implements
    MenuBuilder.Callback, MenuPresenter.Callback,
    View.OnClickListener, AddTagDialog.OnTagChangedListener,
    TagBookmarkDialog.OnBookmarkTagsUpdateListener {

  // FragmentPagerAdapter Positions
  private static final int TAG_FRAGMENT_POS = 0;
  private static final int TRANSLATION_FRAGMENT_POS = 1;

  private static final float PANEL_HEIGHT = 0.6f;

  // Views
  private SlidingUpPanelLayout mSlidingPanel;
  private ViewPager mSlidingPager;
  private FragmentPagerAdapter mSlidingPagerAdapter;
  private RelativeLayout mSlidingLayout;
  private MenuBuilder mMenu;
  private ActionMenuPresenter mMenuPresenter;
  private ActionMenuView mMenuView;
  private HorizontalScrollView mMenuScrollView;

  // References
  private AsyncTask mCurrentTask;
  private ProgressDialog mProgressDialog;
  private AlertDialog mTranslationDialog;
  private WeakReference<PagerActivity> mActivityRef;

  // State
  public boolean isShowing;
  public SuraAyah mStart;
  public SuraAyah mEnd;

  public AyahActionPanel(PagerActivity activity){
    mActivityRef = new WeakReference<PagerActivity>(activity);
    init(activity);
  }

  public boolean isInActionMode() {
    return isShowing;
  }

  public void startActionMode(SuraAyah start) {
    if (!isShowing) {
      updateStartSelection(start);
    } else {
      // TODO
    }
  }

  public void endActionMode() {
    if (isShowing) {
      mSlidingPanel.hidePane();
      isShowing = false;
    }
  }

  public void updateStartSelection(SuraAyah start) {
    mStart = start;
    mEnd = start;
    new RefreshBookmarkIconTask(start).execute();
    // Update Tags
    TagBookmarkDialog tagsFrag = getTagFragment();
    if (tagsFrag != null) {
      tagsFrag.updateAyah(start);
    }
    // Update Tafsir
    AyahTranslationFragment transFrag = getTranslationFragment();
    if (transFrag != null) {
      transFrag.updateAyahSelection(mStart, mEnd);
    }
    isShowing = true;
    mSlidingPanel.showPane();
  }

  public void updateEndSelection(SuraAyah end) {
    mEnd = end;
    // TODO
    // Update Tafsir
    AyahTranslationFragment f = getTranslationFragment();
    if (f != null) {
      f.updateAyahSelection(mStart, mEnd);
    }
  }

  public void cleanup(){
    if (mProgressDialog != null){
      mProgressDialog.hide();
      mProgressDialog = null;
    }

    if (mTranslationDialog != null){
      mTranslationDialog.dismiss();
      mTranslationDialog = null;
    }

    if (mCurrentTask != null){
      mCurrentTask.cancel(true);
      mCurrentTask = null;
    }
  }

  private void updateAyahBookmarkIcon(SuraAyah suraAyah, boolean bookmarked) {
    if (mStart.equals(suraAyah)) {
      MenuItem bookmarkItem = mMenu.findItem(R.id.cab_bookmark_ayah);
      bookmarkItem.setIcon(bookmarked ? R.drawable.favorite : R.drawable.not_favorite);
      bookmarkItem.setTitle(bookmarked ? R.string.unbookmark_ayah : R.string.bookmark_ayah);
    }
  }

  public void onAyahBookmarkUpdated(SuraAyah suraAyah, boolean bookmarked) {
    if (mStart.equals(suraAyah)) {
      updateAyahBookmarkIcon(suraAyah, bookmarked);
    }
  }

  @Override public void onTagAdded(String name) {
    if (TextUtils.isEmpty(name))
      return;
    TagBookmarkDialog f = getTagFragment();
    if (f != null) {
      f.handleTagAdded(name);
    }
  }

  @Override public void onTagUpdated(long id, String name) {
    // should not be called in this flow
  }

  @Override public void onBookmarkTagsUpdated() {
    new RefreshBookmarkIconTask(mStart).execute();
  }

  @Override public void onAddTagSelected() {
    FragmentManager fm = getActivity().getSupportFragmentManager();
    AddTagDialog dialog = new AddTagDialog();
    dialog.show(fm, AddTagDialog.TAG);
  }

  @Override public void onClick(View v) {
    switch (v.getId()) {
      case R.id.sliding_menu_close:
        TagBookmarkDialog f = getTagFragment();
        if (f != null) {
          f.acceptChanges();
        }
        getActivity().endActionMode();
        break;
      default:
        break;
    }
  }

  @Override public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
    final PagerActivity activity = getActivity();
    if (activity == null) {
      return false;
    }

    boolean close = false;
    boolean expand = mSlidingPanel.isExpanded();
    int switchTo = -1;
    switch (item.getItemId()) {
      case R.id.cab_bookmark_ayah:
        activity.toggleBookmark(mStart.sura, mStart.ayah, mStart.getPage());
        break;
      case R.id.cab_tag_ayah:
        expand = true;
        switchTo = TAG_FRAGMENT_POS;
        break;
      case R.id.cab_ayah_translation:
        expand = true;
        switchTo = TRANSLATION_FRAGMENT_POS;
        // TODO if no translation, go to translation download selection activity
        break;
      case R.id.cab_play_from_here:
        close = true;
        expand = false;
        activity.playFromAyah(mStart.getPage(), mStart.sura, mStart.ayah);
        break;
      case R.id.cab_share_ayah_link:
        close = true;
        expand = false;
        mCurrentTask = new ShareQuranApp(mStart, mEnd).execute();
        break;
      case R.id.cab_share_ayah_text:
        close = true;
        expand = false;
        mCurrentTask = new ShareAyahTask(mStart, mEnd, false).execute();
        break;
      case R.id.cab_copy_ayah:
        close = true;
        expand = false;
        mCurrentTask = new ShareAyahTask(mStart, mEnd, true).execute();
        break;
      default:
        return false;
    }
    // Switch to selected tab if not already there
    if (switchTo != -1 && switchTo != mSlidingPager.getCurrentItem()) {
      mSlidingPager.setCurrentItem(switchTo, true);
    }
    // Close (or collapse/expand) the sliding panel
    if (close) {
      getActivity().endActionMode();
    } else if (expand && !mSlidingPanel.isExpanded()) {
      mSlidingPanel.expandPane();
    } else if (!expand && mSlidingPanel.isExpanded()) {
      mSlidingPanel.collapsePane();
    }
    return true;
  }

  @Override public void onMenuModeChange(MenuBuilder menu) {}

  @Override public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {}

  @Override public boolean onOpenSubMenu(MenuBuilder subMenu) {return false;}

  private PagerActivity getActivity(){
    if (mActivityRef != null){
      return mActivityRef.get();
    }
    return null;
  }

  private AyahTranslationFragment getTranslationFragment() {
    return (AyahTranslationFragment) findFragmentByPosition(TRANSLATION_FRAGMENT_POS);
  }

  private TagBookmarkDialog getTagFragment() {
    return (TagBookmarkDialog) findFragmentByPosition(TAG_FRAGMENT_POS);
  }

  // TODO there's got to be a better way than this hack
  private Fragment findFragmentByPosition(int position) {
    final SherlockFragmentActivity activity = getActivity();
    if (activity == null || mSlidingPanel == null || mSlidingPager == null) return null;
    return activity.getSupportFragmentManager().findFragmentByTag(
        "android:switcher:" + mSlidingPager.getId() + ":"
            + mSlidingPagerAdapter.getItemId(position));
  }

  private void init(final PagerActivity activity) {
    mSlidingPanel = (SlidingUpPanelLayout) activity.findViewById(R.id.sliding_panel);
    mSlidingLayout = (RelativeLayout) mSlidingPanel.findViewById(R.id.sliding_layout);
    mMenuScrollView = (HorizontalScrollView) mSlidingPanel.findViewById(R.id.sliding_menu_scroll_view);
    mSlidingPager = (ViewPager) mSlidingPanel.findViewById(R.id.sliding_layout_pager);

    // Find close button and set listener
    final View closeButton = mSlidingPanel.findViewById(R.id.sliding_menu_close);
    closeButton.setOnClickListener(this);

    // Create Menu
    mMenu = new MenuBuilder(activity);
    mMenu.setCallback(this);
    MenuInflater menuInflater = activity.getSupportMenuInflater();
    menuInflater.inflate(R.menu.ayah_menu, mMenu);
    for (int i = 0; i < mMenu.size(); i++) {
      ((MenuItemImpl)mMenu.getItem(i)).setIsActionButton(true);
    }

    // Create Presenter
    mMenuPresenter = new ActionMenuPresenter(activity);
    mMenuPresenter.setCallback(this);
    mMenuPresenter.setReserveOverflow(false);
    mMenuPresenter.setItemLimit(8);
    int width = activity.getResources().getDisplayMetrics().widthPixels;
    mMenuPresenter.setWidthLimit(width, false);
    mMenu.addMenuPresenter(mMenuPresenter);

    // Create MenuView and add to HorizontalScrollView
    mMenuView = (ActionMenuView) mMenuPresenter.getMenuView(mSlidingPanel);
    mMenuView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
    mMenuView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
    mMenuScrollView.addView(mMenuView);

    // Set sliding panel parameters
    mSlidingPanel.setDragView(mMenuView);
    mSlidingPanel.setEnableDragViewTouchEvents(true);

    // Set sliding layout parameters
    int displayHeigh = activity.getResources().getDisplayMetrics().heightPixels;
    mSlidingLayout.getLayoutParams().height = (int) (displayHeigh * PANEL_HEIGHT);
    mSlidingLayout.setVisibility(View.GONE);

    // Create and set fragment pager adapter
    mSlidingPagerAdapter = new SlidingPagerAdapter(activity.getSupportFragmentManager());
    mSlidingPager.setAdapter(mSlidingPagerAdapter);
  }

  private class SlidingPagerAdapter extends FragmentPagerAdapter {

    public SlidingPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public Fragment getItem(int position) {
      switch (position) {
        case TAG_FRAGMENT_POS:
          return new TagBookmarkDialog(mStart);
        case TRANSLATION_FRAGMENT_POS:
          return new AyahTranslationFragment(mStart, mEnd);
      }
      return null;
    }

    @Override
    public int getCount() {
      return 2;
    }
  }

  private class RefreshBookmarkIconTask extends AsyncTask<Void, Void, Boolean> {
    private SuraAyah mSuraAyah;

    public RefreshBookmarkIconTask(SuraAyah suraAyah) {
      mSuraAyah = suraAyah;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
      BookmarksDBAdapter adapter = null;
      Activity activity = getActivity();
      if (activity != null && activity instanceof BookmarkHandler){
        adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
      }

      if (adapter == null){ return null; }

      boolean bookmarked = adapter.getBookmarkId(
          mSuraAyah.sura, mSuraAyah.ayah, mSuraAyah.getPage()) >= 0;
      return bookmarked;
    }

    @Override
    protected void onPostExecute(Boolean result) {
      if (result != null){
        updateAyahBookmarkIcon(mSuraAyah, result);
      }
    }

  }

  private class ShareQuranApp extends AsyncTask<Void, Void, String> {
    private SuraAyah start;
    private SuraAyah end;
    private String mKey;

    public ShareQuranApp(SuraAyah start, SuraAyah end) {
      this.start = start;
      this.end = end;
    }

    @Override
    protected void onPreExecute() {
      Activity activity = getActivity();
      if (activity != null){
        mKey = activity.getString(R.string.quranapp_key);
        mProgressDialog = new ProgressDialog(activity);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setMessage(
            activity.getString(R.string.index_loading));
        mProgressDialog.show();
      }
    }

    @Override
    protected String doInBackground(Void... params){
      int sura = start.sura;
      int startAyah = start.ayah;
      int endAyah = end.sura == start.sura ? end.ayah : QuranInfo.getNumAyahs(start.sura);
      // TODO support spanning multiple suras
      String url = QuranAppUtils.getQuranAppUrl(mKey, sura, startAyah, endAyah);
      return url;
    }

    @Override
    protected void onPostExecute(String url) {
      if (mProgressDialog != null && mProgressDialog.isShowing()){
        mProgressDialog.dismiss();
        mProgressDialog = null;
      }

      Activity activity = getActivity();
      if (activity != null && !TextUtils.isEmpty(url)){
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url);
        activity.startActivity(Intent.createChooser(intent,
            activity.getString(R.string.share_ayah)));
      }

      mCurrentTask = null;
    }
  }

  private class ShareAyahTask extends AsyncTask<Void, Void, List<QuranAyah>> {
    private SuraAyah start, end;
    private boolean copy;

    public ShareAyahTask(SuraAyah start, SuraAyah end, boolean copy) {
      this.start = start;
      this.end = end;
      this.copy = copy;
    }

    @Override
    protected List<QuranAyah> doInBackground(Void... params) {
      List<QuranAyah> verses = new ArrayList<QuranAyah>();
      try {
        DatabaseHandler ayahHandler =
            new DatabaseHandler(getActivity(),
                QuranDataProvider.QURAN_ARABIC_DATABASE);
        Cursor cursor = ayahHandler.getVerses(start.sura, start.ayah,
            end.sura, end.ayah, DatabaseHandler.ARABIC_TEXT_TABLE);
        while (cursor.moveToNext()) {
          QuranAyah verse = new QuranAyah(cursor.getInt(0), cursor.getInt(1));
          verse.setText(cursor.getString(2));
          verses.add(verse);
        }
        cursor.close();
        ayahHandler.closeDatabase();
      }
      catch (Exception e){
      }

      return verses;
    }

    @Override
    protected void onPostExecute(List<QuranAyah> verses) {
      Activity activity = getActivity();
      if (verses != null && !verses.isEmpty() && activity != null) {
        StringBuilder sb = new StringBuilder();
        // TODO what's the best text format for multiple ayahs
        for (QuranAyah verse : verses) {
          sb.append("(").append(verse.getText()).append(") [");
          sb.append(QuranInfo.getSuraName(activity, verse.getSura(), true));
          sb.append(" : ").append(verse.getAyah()).append("]").append("\n\n");
        }
        sb.append(activity.getString(R.string.via_string));
        String text = sb.toString();
        if (copy) {
          ClipboardManager cm = (ClipboardManager)activity.
              getSystemService(Activity.CLIPBOARD_SERVICE);
          if (cm != null){
            cm.setText(text);
            Toast.makeText(activity, activity.getString(
                    R.string.ayah_copied_popup),
                Toast.LENGTH_SHORT
            ).show();
          }
        } else {
          final Intent intent = new Intent(Intent.ACTION_SEND);
          intent.setType("text/plain");
          intent.putExtra(Intent.EXTRA_TEXT, text);
          activity.startActivity(Intent.createChooser(intent,
              activity.getString(R.string.share_ayah)));
        }
      }
      mCurrentTask = null;
    }
  }

}
