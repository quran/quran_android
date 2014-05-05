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
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.fragment.AddTagDialog;
import com.quran.labs.androidquran.ui.fragment.AyahTranslationFragment;
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog;
import com.quran.labs.androidquran.util.QuranAppUtils;
import com.quran.labs.androidquran.widgets.SlidingUpPanelLayout;

import java.lang.ref.WeakReference;

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
  public int mStartSura;
  public int mStartAyah;
  public int mStartPage;
  public int mEndSura;
  public int mEndAyah;
  public int mEndPage;

  public AyahActionPanel(PagerActivity activity){
    mActivityRef = new WeakReference<PagerActivity>(activity);
    init(activity);
  }

  private boolean isCurrentSelection(int sura, int ayah, int page) {
    return mStartSura == sura && mStartAyah == ayah && mStartPage == page;
  }

  public boolean isInActionMode() {
    return isShowing;
  }

  public void startActionMode(int sura, int ayah, int page) {
    if (!isShowing) {
      updateStartSelection(sura, ayah, page);
    } else {
      // TODO
    }
  }

  public void endActionMode() {
    if (isShowing) {
      mSlidingPanel.hidePane();
      isShowing = false;
      mStartSura = mStartAyah = mStartPage = mEndSura = mEndAyah = mEndPage = 0;
    }
  }

  public void updateStartSelection(int sura, int ayah, int page) {
    mStartSura = mEndSura = sura;
    mStartAyah = mEndAyah = ayah;
    mStartPage = mEndPage = page;
    new RefreshBookmarkIconTask(sura, ayah, page).execute();
    // Update Tags
    TagBookmarkDialog tagsFrag = getTagFragment();
    if (tagsFrag != null) {
      tagsFrag.updateAyah(sura, ayah, page);
    }
    // Update Tafsir
    AyahTranslationFragment transFrag = getTranslationFragment();
    if (transFrag != null) {
      transFrag.updateAyahSelection(mStartSura, mStartAyah, mEndSura, mEndAyah);
    }
    isShowing = true;
    mSlidingPanel.showPane();
  }

  public void updateEndSelection(int sura, int ayah, int page) {
    mEndSura = sura;
    mEndAyah = ayah;
    mEndPage = page;
    // TODO
    // Update Tafsir
    AyahTranslationFragment f = getTranslationFragment();
    if (f != null) {
      f.updateAyahSelection(mStartSura, mStartAyah, mEndSura, mEndAyah);
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

  private void updateAyahBookmarkIcon(int sura, int ayah, int page, boolean bookmarked) {
    if (isCurrentSelection(sura, ayah, page)) {
      MenuItem bookmarkItem = mMenu.findItem(R.id.cab_bookmark_ayah);
      bookmarkItem.setIcon(bookmarked ? R.drawable.favorite : R.drawable.not_favorite);
      bookmarkItem.setTitle(bookmarked ? R.string.unbookmark_ayah : R.string.bookmark_ayah);
    }
  }

  public void onAyahBookmarkUpdated(int sura, int ayah, int page, boolean bookmarked) {
    if (isCurrentSelection(sura, ayah, page)) {
      updateAyahBookmarkIcon(sura, ayah, page, bookmarked);
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
    new RefreshBookmarkIconTask(mStartSura, mStartAyah, mStartPage).execute();
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

    View menuItemView = mMenuPresenter.getItemView((MenuItemImpl) item, null, mMenuView);
    if (menuItemView != null) {
      menuItemView.setBackgroundResource(R.color.selection_highlight);
      menuItemView.setPressed(true);
      menuItemView.setSelected(true);
      mMenuPresenter.updateMenuView(false);
    }

    boolean close = false;
    boolean expand = mSlidingPanel.isExpanded();
    int switchTo = -1;
    switch (item.getItemId()) {
      case R.id.cab_bookmark_ayah:
        activity.toggleBookmark(mStartSura, mStartAyah, mStartPage);
        break;
      case R.id.cab_tag_ayah:
        expand = true;
        switchTo = TAG_FRAGMENT_POS;
        break;
      case R.id.cab_ayah_translation:
        expand = true;
        switchTo = TRANSLATION_FRAGMENT_POS;
        //mCurrentTask = new ShowTafsirTask(mStartSura, mStartAyah).execute();
        // TODO if no translation, go to translation download selection activity
        break;
      case R.id.cab_play_from_here:
        close = true;
        expand = false;
        activity.playFromAyah(mStartPage, mStartSura, mStartAyah);
        break;
      case R.id.cab_share_ayah_link:
        close = true;
        expand = false;
        mCurrentTask = new ShareQuranApp().execute(mStartSura, mStartAyah);
        break;
      case R.id.cab_share_ayah_text:
        close = true;
        expand = false;
        mCurrentTask = new ShareAyahTask(mStartSura, mStartAyah, false).execute();
        break;
      case R.id.cab_copy_ayah:
        close = true;
        expand = false;
        mCurrentTask = new ShareAyahTask(mStartSura, mStartAyah, true).execute();
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
          return new TagBookmarkDialog(mStartSura, mStartAyah, mStartPage);
        case TRANSLATION_FRAGMENT_POS:
          return new AyahTranslationFragment(mStartSura, mStartAyah);
      }
      return null;
    }

    @Override
    public int getCount() {
      return 2;
    }
  }

  private class RefreshBookmarkIconTask extends AsyncTask<Void, Void, Boolean> {
    private int mSura;
    private int mAyah;
    private int mPage;

    public RefreshBookmarkIconTask(int sura, int ayah, int page) {
      mSura = sura;
      mAyah = ayah;
      mPage = page;
    }

    @Override
    protected Boolean doInBackground(Void... params) {
      BookmarksDBAdapter adapter = null;
      Activity activity = getActivity();
      if (activity != null && activity instanceof BookmarkHandler){
        adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
      }

      if (adapter == null){ return null; }

      boolean bookmarked = adapter.getBookmarkId(mSura, mAyah, mPage) >= 0;
      return bookmarked;
    }

    @Override
    protected void onPostExecute(Boolean result) {
      if (result != null){
        updateAyahBookmarkIcon(mSura, mAyah, mPage, result);
      }
    }

  }

  private class ShareQuranApp extends AsyncTask<Integer, Void, String> {
    private String mKey;

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
    protected String doInBackground(Integer... params){
      String url = null;
      if (params.length > 0){
        Integer endAyah = null;
        Integer startAyah = null;
        int sura = params[0];
        if (params.length > 1){
          startAyah = params[1];
          if (params.length > 2){
            endAyah = params[2];
          }
        }
        url = QuranAppUtils.getQuranAppUrl(mKey, sura, startAyah, endAyah);
      }
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

  private class ShareAyahTask extends AsyncTask<Void, Void, String> {
    private int sura, ayah;
    private boolean copy;

    public ShareAyahTask(int sura, int ayah, boolean copy) {
      this.sura = sura;
      this.ayah = ayah;
      this.copy = copy;
    }

    @Override
    protected String doInBackground(Void... params) {
      String text = null;
      try {
        DatabaseHandler ayahHandler =
            new DatabaseHandler(getActivity(),
                QuranDataProvider.QURAN_ARABIC_DATABASE);
        Cursor cursor = ayahHandler.getVerses(sura, ayah, sura, ayah,
            DatabaseHandler.ARABIC_TEXT_TABLE);
        if (cursor.moveToFirst()) {
          text = cursor.getString(2);
        }
        cursor.close();
        ayahHandler.closeDatabase();
      }
      catch (Exception e){
      }

      return text;
    }

    @Override
    protected void onPostExecute(String ayah) {
      Activity activity = getActivity();
      if (ayah != null && activity != null) {
        ayah = "(" + ayah + ")" + " " + "["
            + QuranInfo.getSuraName(activity, this.sura, true)
            + " : " + this.ayah + "]" + activity.getString(R.string.via_string);
        if (copy) {
          ClipboardManager cm = (ClipboardManager)activity.
              getSystemService(Activity.CLIPBOARD_SERVICE);
          if (cm != null){
            cm.setText(ayah);
            Toast.makeText(activity, activity.getString(
                    R.string.ayah_copied_popup),
                Toast.LENGTH_SHORT
            ).show();
          }
        } else {
          final Intent intent = new Intent(Intent.ACTION_SEND);
          intent.setType("text/plain");
          intent.putExtra(Intent.EXTRA_TEXT, ayah);
          activity.startActivity(Intent.createChooser(intent,
              activity.getString(R.string.share_ayah)));
        }
      }
      mCurrentTask = null;
    }
  }
/*
  private class ShowTafsirTask extends TranslationTask {
    private int sura, ayah;

    public ShowTafsirTask() {
      super(getActivity(), new Integer[] {mStartSura, mStartAyah, mEndSura, mEndAyah},
          TranslationUtils.getDefaultTranslation(getActivity(), getActivity().getTranslations()), null);
      this.sura = mStartSura;
      this.ayah = mStartAyah;
    }

    @Override
    protected void onPostExecute(List<QuranAyah> result) {
      if (result != null && isCurrentSelection(this.sura, this.ayah)) {
        AyahTranslationFragment f = (AyahTranslationFragment) findFragmentByPosition(TRANSLATION_FRAGMENT_POS);
        final TranslationView view = f == null ? null : f.getTranslationView();
        if (view != null) {
          view.setAyahs(result);
        }
      } else {
        // TODO show button in the fragment showing no translation, download here
        //showGetTranslationDialog();
      }
      mCurrentTask = null;
    }
  }

  private void showGetTranslationDialog() {
    final SherlockFragmentActivity activity = getActivity();
    if (activity == null) {
      return;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    builder.setMessage(R.string.need_translation)
        .setPositiveButton(R.string.get_translations,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog,
                                  int i) {
                dialog.dismiss();
                mTranslationDialog = null;
                Intent tm = new Intent(getActivity(),
                    TranslationManagerActivity.class);
                activity.startActivity(tm);
              }
            }
        )
        .setNegativeButton(R.string.cancel,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog,
                                  int i) {
                dialog.dismiss();
                mTranslationDialog = null;
              }
            }
        );
    mTranslationDialog = builder.create();
    mTranslationDialog.show();
  }
*/
}
