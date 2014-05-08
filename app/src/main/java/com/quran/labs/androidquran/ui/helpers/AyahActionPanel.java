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
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.internal.view.menu.MenuBuilder;
import com.actionbarsherlock.internal.view.menu.MenuPresenter;
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
import com.quran.labs.androidquran.widgets.IconPageIndicator;
import com.quran.labs.androidquran.widgets.SlidingUpPanelLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class AyahActionPanel implements
    MenuBuilder.Callback, MenuPresenter.Callback,
    AddTagDialog.OnTagChangedListener, View.OnClickListener,
    TagBookmarkDialog.OnBookmarkTagsUpdateListener {

  // FragmentPagerAdapter Positions
  private static final int TAG_PAGE = 0;
  private static final int TRANSLATION_PAGE = 1;
  private static final int[] PAGE_ICONS = {
      R.drawable.ic_tag, R.drawable.ic_translation};

  private static final float PANEL_HEIGHT = 0.6f;

  // Views
  private SlidingUpPanelLayout mSlidingPanel;
  private ViewPager mSlidingPager;
  private FragmentPagerAdapter mSlidingPagerAdapter;
  private ViewGroup mSlidingLayout;
  private IconPageIndicator mSlidingPagerIndicator;

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
    // TODO handle multiple selection case for tags
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
    mSlidingPagerIndicator.notifyDataSetChanged();
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
    boolean end = false;
    switch (item.getItemId()) {
      case R.id.cab_bookmark_ayah:
        activity.toggleBookmark(mStart.sura, mStart.ayah, mStart.getPage());
        break;
      case R.id.cab_play_from_here:
        activity.playFromAyah(mStart.getPage(), mStart.sura, mStart.ayah);
        break;
      case R.id.cab_share_ayah_link:
        mCurrentTask = new ShareQuranApp(mStart, mEnd).execute();
        end = true;
        break;
      case R.id.cab_share_ayah_text:
        mCurrentTask = new ShareAyahTask(mStart, mEnd, false).execute();
        end = true;
        break;
      case R.id.cab_copy_ayah:
        mCurrentTask = new ShareAyahTask(mStart, mEnd, true).execute();
        end = true;
        break;
      default:
        return false;
    }
    if (end) activity.endActionMode();
    return true;
  }

  @Override public void onMenuModeChange(MenuBuilder menu) {}

  @Override public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {}

  @Override public boolean onOpenSubMenu(MenuBuilder subMenu) {return false;}

  private PagerActivity getActivity(){
    return mActivityRef != null ? mActivityRef.get() : null;
  }

  private AyahTranslationFragment getTranslationFragment() {
    return (AyahTranslationFragment) findFragmentByPosition(TRANSLATION_PAGE);
  }

  private TagBookmarkDialog getTagFragment() {
    return (TagBookmarkDialog) findFragmentByPosition(TAG_PAGE);
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
    mSlidingLayout = (ViewGroup) mSlidingPanel.findViewById(R.id.sliding_layout);
    mSlidingPager = (ViewPager) mSlidingPanel.findViewById(R.id.sliding_layout_pager);
    mSlidingPagerIndicator = (IconPageIndicator) mSlidingPanel.findViewById(R.id.sliding_pager_indicator);

    // Find close button and set listener
    final View closeButton = mSlidingPanel.findViewById(R.id.sliding_menu_close);
    closeButton.setOnClickListener(this);

    // Create and set fragment pager adapter
    mSlidingPagerAdapter = new SlidingPagerAdapter(activity.getSupportFragmentManager());
    mSlidingPager.setAdapter(mSlidingPagerAdapter);

    // Attach the view pager to the action bar
    mSlidingPagerIndicator.setViewPager(mSlidingPager);

    // Set sliding layout parameters
    int displayHeigh = activity.getResources().getDisplayMetrics().heightPixels;
    mSlidingLayout.getLayoutParams().height = (int) (displayHeigh * PANEL_HEIGHT);
    mSlidingPanel.setDragView(mSlidingPanel.findViewById(R.id.ayah_action_bar));
    mSlidingPanel.setEnableDragViewTouchEvents(true);
    mSlidingLayout.setVisibility(View.GONE);
  }

  private class SlidingPagerAdapter extends FragmentPagerAdapter implements
      IconPageIndicator.IconPagerAdapter {

    public SlidingPagerAdapter(FragmentManager fm) {
      super(fm);
    }

    @Override
    public int getCount() {
      return PAGE_ICONS.length;
    }

    @Override
    public Fragment getItem(int position) {
      switch (position) {
        case TAG_PAGE:
          return new TagBookmarkDialog();
        case TRANSLATION_PAGE:
          return new AyahTranslationFragment();
      }
      return null;
    }

    @Override
    public int getIconResId(int index) {
      return PAGE_ICONS[index];
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
