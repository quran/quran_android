package com.quran.labs.androidquran.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.TextUtils;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.service.AudioService;
import com.quran.labs.androidquran.ui.fragment.*;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.Locale;

public class QuranActivity extends SherlockFragmentActivity
        implements ActionBar.TabListener,
                   AddTagDialog.OnTagChangedListener,
                   TagBookmarkDialog.OnBookmarkTagsUpdateListener {
   public final String TAG = "QuranActivity";
   
   private static final int SURA_LIST = 0;
   private static final int JUZ2_LIST = 1;
   private static final int BOOKMARKS_LIST = 2;
   private static final int TAGS_LIST = 3;

   private static final int REFRESH_BOOKMARKS = 1;
   private static final int REFRESH_TAGS = 2;
   
   private int[] mTabs = new int[]{ R.string.quran_sura,
                                    R.string.quran_juz2,
                                    R.string.menu_bookmarks,
                                    R.string.menu_tags};
   private int[] mTabTags = new int[]{ SURA_LIST, JUZ2_LIST, BOOKMARKS_LIST, TAGS_LIST };
   
   private ViewPager mPager = null;
   private PagerAdapter mPagerAdapter = null;
   private BookmarksDBAdapter mBookmarksDBAdapter = null;

   @Override
   public void onCreate(Bundle savedInstanceState){
      if (QuranSettings.isArabicNames(this)){
         Resources resources = getResources();
         Configuration config = resources.getConfiguration();
         config.locale = new Locale("ar");
         resources.updateConfiguration(config,
                 resources.getDisplayMetrics());
      }

      setTheme(R.style.QuranAndroid);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.quran_index);

      ActionBar actionbar = getSupportActionBar();
      actionbar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

      mPager = (ViewPager)findViewById(R.id.index_pager);
      mPager.setOffscreenPageLimit(3);
      mPagerAdapter = new PagerAdapter(getSupportFragmentManager());
      mPager.setAdapter(mPagerAdapter);
      mPager.setOnPageChangeListener(mOnPageChangeListener);
      
      for (int i=0; i<mTabs.length; i++){
         ActionBar.Tab tab = actionbar.newTab();
         tab.setText(mTabs[i]);
         tab.setTag(mTabTags[i]);
         tab.setTabListener(this);
         actionbar.addTab(tab);
      }

      mBookmarksDBAdapter = new BookmarksDBAdapter(this);
   }

   @Override
   public void onResume(){
      super.onResume();
      startService(new Intent(AudioService.ACTION_STOP));
   }

   @Override
   protected void onDestroy() {
      mBookmarksDBAdapter.close();
      super.onDestroy();
   }

   public BookmarksDBAdapter getBookmarksAdapter(){
      return mBookmarksDBAdapter;
   }

   private Handler mHandler = new Handler(){
      @Override
      public void handleMessage(Message msg) {
         if (msg.what == REFRESH_BOOKMARKS){
            String bookmarksTag = mPagerAdapter.getFragmentTag(
                    R.id.index_pager, BOOKMARKS_LIST);
            FragmentManager fm = getSupportFragmentManager();
            Fragment f = fm.findFragmentByTag(bookmarksTag);
            if (f != null && f instanceof AbsMarkersFragment){
               ((AbsMarkersFragment)f).refreshData();
            }
         } else if (msg.what == REFRESH_TAGS){
            String tagsTag = mPagerAdapter.getFragmentTag(
                  R.id.index_pager, TAGS_LIST);
            FragmentManager fm = getSupportFragmentManager();
            Fragment f = fm.findFragmentByTag(tagsTag);
            if (f != null && f instanceof AbsMarkersFragment){
               ((AbsMarkersFragment)f).refreshData();
            }
         }
      }
   };

   @Override
   public void onTabSelected(Tab tab, FragmentTransaction transaction){
      Integer tag = (Integer)tab.getTag();
      mPager.setCurrentItem(tag);
   }
   
   @Override
   public void onTabReselected(Tab tab, FragmentTransaction transaction){
   }

   @Override
   public void onTabUnselected(Tab tab, FragmentTransaction transaction){
   }
   
   OnPageChangeListener mOnPageChangeListener = new OnPageChangeListener(){
      @Override
      public void onPageScrollStateChanged(int state) {
      }

      @Override
      public void onPageScrolled(int position,
            float positionOffset, int positionOffsetPixels) {
      }

      @Override
      public void onPageSelected(int position) {
         ActionBar actionbar = getSherlock().getActionBar();
         Tab tab = actionbar.getTabAt(position);
         actionbar.selectTab(tab);
      }
   };
   
   @Override
   public boolean onCreateOptionsMenu(Menu menu){
      super.onCreateOptionsMenu(menu);
      MenuInflater inflater = getSupportMenuInflater();
      inflater.inflate(R.menu.home_menu, menu);
      return true;
   }
   
   @Override
	public boolean onOptionsItemSelected(MenuItem item) {
      if (item.getItemId() == R.id.search){
         return onSearchRequested();
      }
      else if (item.getItemId() == R.id.settings){
         Intent i = new Intent(this, QuranPreferenceActivity.class);
         startActivity(i);
         return true;
      }
      else if (item.getItemId() == R.id.last_page){
         SharedPreferences prefs =
                 PreferenceManager.getDefaultSharedPreferences(
                         getApplicationContext());
         int page = prefs.getInt(Constants.PREF_LAST_PAGE, 1);
         jumpTo(page);
         return true;
      }
      else if (item.getItemId() == R.id.help) {
         Intent i = new Intent(this, HelpActivity.class);
         startActivity(i);
         return true;
      }
      else if (item.getItemId() == R.id.about) {
    	  Intent i = new Intent(this, AboutUsActivity.class);
    	  startActivity(i);
    	  return true;
      } else if (item.getItemId() == R.id.jump) {
    	  gotoPageDialog();
    	  return true;
      }
	   
      return super.onOptionsItemSelected(item);
	}
   
   public void jumpTo(int page) {
      Intent i = new Intent(this, PagerActivity.class);
      i.putExtra("page", page);
      startActivity(i);
   }
   
   public void jumpToAndHighlight(int page, int sura, int ayah) {
      Intent i = new Intent(this, PagerActivity.class);
      i.putExtra("page", page);
      i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
      i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
      startActivity(i);
   }

   public void gotoPageDialog() {
      FragmentManager fm = getSupportFragmentManager();
      JumpFragment jumpDialog = new JumpFragment();
      jumpDialog.show(fm, JumpFragment.TAG);
   }

   public void addTag(){
      FragmentManager fm = getSupportFragmentManager();
      AddTagDialog addTagDialog = new AddTagDialog();
      addTagDialog.show(fm, AddTagDialog.TAG);
   }

   public void editTag(long id, String name){
      FragmentManager fm = getSupportFragmentManager();
      AddTagDialog addTagDialog =
              new AddTagDialog(id, name);
      addTagDialog.show(fm, AddTagDialog.TAG);
   }

   public void tagBookmark(long id){
      FragmentManager fm = getSupportFragmentManager();
      TagBookmarkDialog tagBookmarkDialog = new TagBookmarkDialog(id);
      tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG);
   }
   
   @Override
   public void onBookmarkTagsUpdated(long bookmarkId) {
      mHandler.sendEmptyMessage(REFRESH_BOOKMARKS);
      mHandler.sendEmptyMessage(REFRESH_TAGS);
   }
   
   @Override
   public void onTagAdded(final String name) {
      if (TextUtils.isEmpty(name))
         return;
      FragmentManager fm = getSupportFragmentManager();
      Fragment f = fm.findFragmentByTag(TagBookmarkDialog.TAG);
      if (f != null && f instanceof TagBookmarkDialog){
         ((TagBookmarkDialog)f).handleTagAdded(name);
      } else {
	     new Thread(new Runnable() {
	        @Override
	        public void run() {
	           mBookmarksDBAdapter.addTag(name);
	           mHandler.sendEmptyMessage(REFRESH_TAGS);
	        }
	     }).start();
      }
   }

   @Override
   public void onTagUpdated(final long id, final String name) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            mBookmarksDBAdapter.updateTag(id, name);
            mHandler.sendEmptyMessage(REFRESH_TAGS);
         }
      }).start();
   }

   @Override
   public void onAddTagSelected() {
      FragmentManager fm = getSupportFragmentManager();
      AddTagDialog dialog = new AddTagDialog();
      dialog.show(fm, AddTagDialog.TAG);
   }
   
   public static class PagerAdapter extends FragmentPagerAdapter {
      public PagerAdapter(FragmentManager fm){
         super(fm);
      }
      
      @Override
      public int getCount(){
         return 4;
      }
      
      @Override
      public Fragment getItem(int position){
         switch (position){
         case QuranActivity.SURA_LIST:
            return SuraListFragment.newInstance();
         case QuranActivity.JUZ2_LIST:
            return JuzListFragment.newInstance();
         case QuranActivity.BOOKMARKS_LIST:
            return BookmarksFragment.newInstance();
         case QuranActivity.TAGS_LIST:
         default:
            return TagsFragment.newInstance();
         }
      }

      /**
       * this is a private method in FragmentPagerAdapter that
       * allows getting the tag that it uses to store the fragment
       * in (for use by getFragmentByTag).  in the future, this could
       * change and cause us issues...
       * @param viewId the view id of the viewpager
       * @param index the index of the fragment to get
       * @return the tag in which it would be stored under
       */
      public static String getFragmentTag(int viewId, int index){
         return "android:switcher:" + viewId + ":" + index;
      }
   }
}
