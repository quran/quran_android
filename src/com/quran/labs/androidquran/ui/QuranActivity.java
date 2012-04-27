package com.quran.labs.androidquran.ui;

import static com.quran.labs.androidquran.data.ApplicationConstants.JUZ2_COUNT;
import static com.quran.labs.androidquran.data.ApplicationConstants.PAGES_LAST;
import static com.quran.labs.androidquran.data.ApplicationConstants.SURAS_COUNT;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDatabaseHandler;
import com.quran.labs.androidquran.database.BookmarksDatabaseHandler.AyahBookmark;
import com.quran.labs.androidquran.util.ArabicStyle;

public class QuranActivity extends SherlockActivity implements ActionBar.TabListener {
   public final String TAG = "QuranActivity";
   
   private final int SURA_LIST = 1;
   private final int JUZ2_LIST = 2;
   private final int BOOKMARKS_LIST = 3;
   
   private final int MENU_SEARCH = 1;
   private final int MENU_SETTINGS = 2;
   
   private int[] mTabs = new int[]{ R.string.quran_sura,
                                    R.string.quran_juz2,
                                    R.string.menu_bookmarks};
   private int[] mTabTags = new int[]{ SURA_LIST, JUZ2_LIST, BOOKMARKS_LIST };
   
   private ListView mList = null;

   @Override
   public void onCreate(Bundle savedInstanceState){
      setTheme(R.style.Theme_Sherlock);
      super.onCreate(savedInstanceState);
      setContentView(R.layout.quran_list);

      ActionBar actionbar = getSupportActionBar();
      actionbar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

      mList = (ListView)findViewById(R.id.suralist);
      View emptyView = findViewById(R.id.emptysuralist);
      mList.setEmptyView(emptyView);
      
      for (int i=0; i<mTabs.length; i++){
         ActionBar.Tab tab = actionbar.newTab();
         tab.setText(mTabs[i]);
         tab.setTag(mTabTags[i]);
         tab.setTabListener(this);
         actionbar.addTab(tab);
      }
   }
   
   @Override
   public void onResume(){
      super.onResume();
      refreshLists();
   }

   @Override
   public void onTabSelected(Tab tab, FragmentTransaction transaction){
      android.util.Log.d(TAG, "onTabSelected");
      Integer tabTag = (Integer)tab.getTag();
      new OnTabSelectedTask().execute(tabTag);
   }
   
	class OnTabSelectedTask extends AsyncTask<Integer, Void, QuranRow[]> {
		private Integer layout = 0;

		@Override
		protected QuranRow[] doInBackground(Integer... params) {
		      QuranRow[] elements = null;
		      switch (params[0]){
		      case JUZ2_LIST:
		    	  layout = R.layout.index_sura_row;
		          elements = getJuz2List();
		          break;
		      case BOOKMARKS_LIST:
		    	  layout = R.layout.index_sura_row;
		    	  elements = getBookmarks();
		    	  break;
		      case SURA_LIST:
		      default:
		         layout = R.layout.index_sura_row;
		         elements = getSuraList();
		      }
		      return elements;
		}
		
		@Override
		protected void onPostExecute(QuranRow[] result) {
			EfficientAdapter adapter = new EfficientAdapter(
					getApplicationContext(), layout, result);
			mList.setAdapter(adapter);
			mList.setOnItemClickListener(new OnItemClickListener(){
				public void onItemClick(AdapterView<?> parent, View v,
						int position, long id){
					ListView p = (ListView)parent;
					QuranRow elem = (QuranRow)p.getAdapter().getItem((int)id);
					if (elem.page > 0)
						jumpTo(elem.page);
				}
			});
		}

	}
   
   @Override
   public boolean onCreateOptionsMenu(Menu menu){
	   super.onCreateOptionsMenu(menu);
	   menu.add(0, MENU_SEARCH, 0, R.string.menu_search).setIcon(R.drawable.ic_ab_search)
	       .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
	   menu.add(0, MENU_SETTINGS, 0, R.string.menu_settings).setIcon(R.drawable.ic_ab_settings)
	   	   .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
	   return true;
   }
   
   @Override
	public boolean onOptionsItemSelected(MenuItem item) {
      if (item.getItemId() == MENU_SEARCH){
         return onSearchRequested();
      }
      else if (item.getItemId() == MENU_SETTINGS){
         Intent i = new Intent(this, QuranPreferenceActivity.class);
         startActivity(i);
         return true;
      }
	   
      return super.onOptionsItemSelected(item);
	}

   private void refreshLists(){
      EfficientAdapter adapter = null;
      if (mList != null){
         Adapter tmp = mList.getAdapter();
         if (tmp != null && tmp instanceof EfficientAdapter)
            adapter = (EfficientAdapter)tmp;
      }
      ActionBar actionbar = getSupportActionBar();

      if (actionbar != null && adapter != null){
         Tab tab = actionbar.getSelectedTab();
         if (tab == null) return;
         
         Integer tabTag = (Integer)tab.getTag();
         if (tabTag != null && tabTag == BOOKMARKS_LIST){
            // refresh the bookmarks list to update current page
            // and any added new bookmarks.
            new BookmarksRefreshTask(adapter).execute();
         }
         else {
            int lastPage = getLastPage();
            if (lastPage == ApplicationConstants.NO_PAGE_SAVED) return;
            if (tabTag != null && tabTag == SURA_LIST){
               // scroll the list to the correct sura
               int sura = QuranInfo.PAGE_SURA_START[lastPage-1];
               int juz = QuranInfo.getJuzFromPage(lastPage);
               int position = sura + juz - 1;
               mList.setSelectionFromTop(position, 20);
            }
            else if (tabTag != null && tabTag == JUZ2_LIST){
               // scroll the list to the correct juz'
               int juz = QuranInfo.getJuzFromPage(lastPage);
               int position = (juz - 1) * 8;
               mList.setSelectionFromTop(position, 20);
            }
         }
      }
   }
   
	class BookmarksRefreshTask extends AsyncTask<Void, Void, QuranRow[]> {
		private EfficientAdapter adapter;
		
		public BookmarksRefreshTask(EfficientAdapter adapter) {
			this.adapter = adapter;
		}
		
		@Override
		protected QuranRow[] doInBackground(Void... params) {
            return getBookmarks();
		}

		@Override
		protected void onPostExecute(QuranRow[] result) {
            if (adapter != null) {
				adapter.mElements = result;
				adapter.notifyDataSetChanged();
			}
		}
	}
   
   public void jumpTo(int page) {
      Intent i = new Intent(this, PagerActivity.class);
      i.putExtra("page", page);
      startActivity(i);
   }
   
   @Override
   public void onTabReselected(Tab tab, FragmentTransaction transaction){
      android.util.Log.d(TAG, "onTabReselected");
   }

   @Override
   public void onTabUnselected(Tab tab, FragmentTransaction transaction){
      android.util.Log.d(TAG, "onTabUnselected");
   }

   // this class holds the data representation of our ListItem
   // it is used for rendering sura, juz', and bookmarks.
   private class QuranRow {
      public int number;
      public int page;
      public String text;
      public String metadata;
      public boolean isHeader;
      public Integer imageResource;
      public String imageText;

      public QuranRow(String text, String metadata, boolean isHeader, 
    		  int number, int page, Integer imageResource){
         this.text = text;
         this.isHeader = isHeader;
         this.number = number;
         this.page = page;
         this.metadata = metadata;
         this.imageResource = imageResource;
         this.imageText = "";
      }
   }
   
   private QuranRow[] getJuz2List() {
      int[] images = { R.drawable.hizb_full, R.drawable.hizb_quarter,
                       R.drawable.hizb_half, R.drawable.hizb_threequarters };
      Resources res = getResources();
      String[] quarters = res.getStringArray(R.array.quarter_prefix_array);
	   QuranRow[] elements = new QuranRow[JUZ2_COUNT * (8 + 1)];
	   
	   int ctr = 0;
	   for (int i = 0; i < (8 * JUZ2_COUNT); i++){
	      int[] pos = QuranInfo.QUARTERS[i];
	      int page = QuranInfo.getPageFromSuraAyah(pos[0], pos[1]);
	      
	      if (i % 8 == 0){
	         int juz = 1 + (i / 8);
	         elements[ctr++] = new QuranRow(QuranInfo.getJuzTitle() + " " +
	               juz, null, true, juz, QuranInfo.JUZ_PAGE_START[juz-1], null);
	      }
	      String verseString = getString(R.string.quran_ayah) + " " + pos[1];
	      elements[ctr++] = new QuranRow(quarters[i],
	            QuranInfo.getSuraName(pos[0]-1) + ", " + verseString,
	            false, 0, page, images[i % 4]);
	      if (i % 4 == 0)
	         elements[ctr-1].imageText = (1 + (i / 4)) + "";
	   }
	   
	   return elements;
   }
   
   private QuranRow[] getSuraList(){
      int pos = 0;
      int sura = 1;
      int next = 1;
      QuranRow[] elements = new QuranRow[SURAS_COUNT + JUZ2_COUNT];

      for (int juz=1; juz <= JUZ2_COUNT; juz++){
         elements[pos++] = new QuranRow(QuranInfo.getJuzTitle() + " " +
               juz, null, true, juz, QuranInfo.JUZ_PAGE_START[juz-1], null);
         next = (juz == JUZ2_COUNT) ? PAGES_LAST+1 :
            QuranInfo.JUZ_PAGE_START[juz];
         
         while ((sura <= SURAS_COUNT) &&
                (QuranInfo.SURA_PAGE_START[sura-1] < next)) {
            String title = QuranInfo.getSuraTitle() 
                  + " " + QuranInfo.getSuraName(sura-1);
            elements[pos++] = new QuranRow(title, 
            		QuranInfo.getSuraListMetaString(sura),
            		false, sura, QuranInfo.SURA_PAGE_START[sura-1], null);
            sura++;
         }
      }

      return elements;
   }
   
   private int getLastPage(){
      SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(getApplicationContext());
      return getLastPage(prefs);
   }
   
   private int getLastPage(SharedPreferences prefs){
      return prefs.getInt(ApplicationConstants.PREF_LAST_PAGE,
            ApplicationConstants.NO_PAGE_SAVED);
   }
   
   private QuranRow[] getBookmarks(){
	   BookmarksDatabaseHandler db = new BookmarksDatabaseHandler(getApplicationContext());
	   db.open();
	   List<Integer> pageBookmarks = db.getPageBookmarks();
	   List<AyahBookmark> ayahBookmarks = db.getAyahBookmarks();
	   db.close();
      
	   SharedPreferences prefs = PreferenceManager.
			   getDefaultSharedPreferences(getApplicationContext());
	   int lastPage = getLastPage(prefs);
	   boolean showLastPage = lastPage != ApplicationConstants.NO_PAGE_SAVED;
	   boolean showPageBookmarkHeader = pageBookmarks.size() != 0;
	   boolean showAyahBookmarkHeader = ayahBookmarks.size() != 0;
	   int size = pageBookmarks.size() + ayahBookmarks.size() + (showLastPage? 2 : 0) +
			   (showPageBookmarkHeader? 1 : 0) + (showAyahBookmarkHeader? 1 : 0);
	   
	   int index = 0;
	   QuranRow[] res = new QuranRow[size];
	   if (showLastPage){
		   QuranRow header = new QuranRow(
				   getString(R.string.bookmarks_current_page),
				   null, true, 0, 0, null);
		   QuranRow currentPosition = new QuranRow(
				   QuranInfo.getSuraNameString(lastPage),
				   QuranInfo.getSuraDetailsForBookmark(lastPage),
				   false, QuranInfo.PAGE_SURA_START[lastPage-1], lastPage,
				   R.drawable.bookmark_currentpage);
		   res[index++] = header;
		   res[index++] = currentPosition;
	   }
	   
	   if (showPageBookmarkHeader){
		   res[index++] = new QuranRow(getString(R.string.menu_bookmarks_page),
				   null, true, 0, 0, null);
	   }
	   for (int page : pageBookmarks){
		   res[index++] = new QuranRow(
				   QuranInfo.getSuraNameString(page),
				   QuranInfo.getSuraDetailsForBookmark(page),
				   false, QuranInfo.PAGE_SURA_START[page-1], page,
				   R.drawable.bookmark_page);
	   }
	   
	   if (showAyahBookmarkHeader){
		   res[index++] = new QuranRow(getString(R.string.menu_bookmarks_ayah),
				   null, true, 0, 0, null);
	   }
	   for (AyahBookmark ayah : ayahBookmarks){
		   res[index++] = new QuranRow(
				   // TODO Polish up displayed information for Ayahs
				   QuranInfo.getAyahString(ayah.sura, ayah.ayah, getApplicationContext()),
				   QuranInfo.getSuraDetailsForBookmark(ayah.page),
				   false, ayah.sura, ayah.page,
				   R.drawable.bookmark_page);
	   }
	   
	   return res;
   }

   private class EfficientAdapter extends BaseAdapter {
      private LayoutInflater mInflater;
      private QuranRow[] mElements;
      private int mLayout;
      
      public EfficientAdapter(Context context, int layout, QuranRow[] items){
          mInflater = LayoutInflater.from(context);
          mElements = items;
          mLayout = layout;
      }

      public int getCount() {
          return mElements.length;
      }

      public Object getItem(int position) {
          return mElements[position];
      }

      public long getItemId(int position) {
          return position;
      }

      public View getView(final int position, View convertView, ViewGroup parent) {
          ViewHolder holder;
          
          if (convertView == null) {
              convertView = mInflater.inflate(mLayout, null);
              holder = new ViewHolder();
              holder.text = (TextView)convertView.findViewById(R.id.suraName);
              holder.text.setTypeface(ArabicStyle.getTypeface());
              holder.metadata = (TextView)convertView.findViewById(R.id.suraDetails);
              holder.metadata.setTypeface(ArabicStyle.getTypeface());
              holder.page = (TextView)convertView.findViewById(R.id.pageNumber);
              holder.number = (TextView)convertView.findViewById(R.id.suraNumber);
              holder.header = (TextView)convertView.findViewById(R.id.headerName);
              holder.image = (TextView)convertView.findViewById(R.id.rowIcon);
              convertView.setTag(holder);
          }
          else { holder = (ViewHolder) convertView.getTag(); }

    	  QuranRow item = mElements[position];
          holder.page.setText(ArabicStyle.reshape(String.valueOf(item.page)));
          holder.text.setText(ArabicStyle.reshape(item.text));
          holder.header.setText(ArabicStyle.reshape(item.text));
          holder.number.setText(ArabicStyle.reshape(String.valueOf(item.number)));
          
          int color = R.color.suraDetailsColor;
          if (mElements[position].isHeader){
              holder.text.setVisibility(View.GONE);
              holder.header.setVisibility(View.VISIBLE);
              holder.metadata.setVisibility(View.GONE);
              holder.number.setVisibility(View.GONE);
              holder.image.setVisibility(View.GONE);
              color = R.color.headerTextColor;
          }
          else {
              String info = item.metadata;
              holder.metadata.setVisibility(View.VISIBLE);
              holder.text.setVisibility(View.VISIBLE);
              holder.header.setVisibility(View.GONE);
              holder.metadata.setText(ArabicStyle.reshape(info));
              
              if (item.imageResource == null){
            	  holder.number.setVisibility(View.VISIBLE);
            	  holder.image.setVisibility(View.GONE);
              }
              else {
            	  holder.image.setBackgroundResource(item.imageResource);
            	  holder.image.setText(item.imageText);
            	  holder.image.setVisibility(View.VISIBLE);
            	  holder.number.setVisibility(View.GONE);
              }
          }
          holder.page.setTextColor(getResources().getColor(color));
          int pageVisibility = item.page == 0? View.GONE : View.VISIBLE;
          holder.page.setVisibility(pageVisibility);
          return convertView;
      }
            
      class ViewHolder {
          TextView text;
          TextView page;
          TextView number;
          TextView metadata;
          TextView header;
          TextView image;
      }
  }
}