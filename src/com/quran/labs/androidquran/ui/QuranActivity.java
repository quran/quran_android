package com.quran.labs.androidquran.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;

import android.util.Log;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment;
import com.quran.labs.androidquran.ui.fragment.JuzListFragment;
import com.quran.labs.androidquran.ui.fragment.SuraListFragment;
import com.quran.labs.androidquran.util.QuranSettings;

import java.util.Locale;

public class QuranActivity extends SherlockFragmentActivity
        implements ActionBar.TabListener {
   public final String TAG = "QuranActivity";
   
   private static final int SURA_LIST = 0;
   private static final int JUZ2_LIST = 1;
   private static final int BOOKMARKS_LIST = 2;
   
   private int[] mTabs = new int[]{ R.string.quran_sura,
                                    R.string.quran_juz2,
                                    R.string.menu_bookmarks};
   private int[] mTabTags = new int[]{ SURA_LIST, JUZ2_LIST, BOOKMARKS_LIST };
   
   private ViewPager mPager = null;
   private PagerAdapter mPagerAdapter = null;

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
   }

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
      else if (item.getItemId() == R.id.about) {
    	  Intent i = new Intent(this, AboutUsActivity.class);
    	  startActivity(i);
    	  return true;
      }
	   
      return super.onOptionsItemSelected(item);
	}
   
   public void jumpTo(int page) {
      Intent i = new Intent(this, PagerActivity.class);
      i.putExtra("page", page);
      startActivity(i);
   }
   
   public static class PagerAdapter extends FragmentPagerAdapter {
      public PagerAdapter(FragmentManager fm){
         super(fm);
      }
      
      @Override
      public int getCount(){
         return 3;
      }
      
      @Override
      public Fragment getItem(int position){
         switch (position){
         case QuranActivity.SURA_LIST:
            return SuraListFragment.newInstance();
         case QuranActivity.JUZ2_LIST:
            return JuzListFragment.newInstance();
         case QuranActivity.BOOKMARKS_LIST:
         default:
            return BookmarksFragment.newInstance();
         }
      }
   }
}
