package com.quran.labs.androidquran.ui;

import static com.quran.labs.androidquran.data.ApplicationConstants.JUZ2_COUNT;
import static com.quran.labs.androidquran.data.ApplicationConstants.PAGES_LAST;
import static com.quran.labs.androidquran.data.ApplicationConstants.SURAS_COUNT;

import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranUtils;

public class QuranActivity extends SherlockActivity implements ActionBar.TabListener {
   public final String TAG = "QuranActivity";
   
   private final int SURA_LIST = 1;
   private final int JUZ2_LIST = 2;
   private final int BOOKMARKS_LIST = 3;
   
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
      android.util.Log.d(TAG, "onTabSelected");
      Integer tabTag = (Integer)tab.getTag();
      
      int layout;
      QuranRow[] elements = null;
      switch (tabTag){
      case JUZ2_LIST:
      case BOOKMARKS_LIST:
    	  layout = R.layout.index_sura_row;
    	  elements = getBookmarks();
    	  break;
      case SURA_LIST:
      default:
         layout = R.layout.index_sura_row;
         elements = getSuraList();
      }
      
      EfficientAdapter adapter = new EfficientAdapter(this, layout, elements);
      mList.setAdapter(adapter);
      mList.setOnItemClickListener(new OnItemClickListener(){
         public void onItemClick(AdapterView<?> parent, View v,
                                 int position, long id){
            ListView p = (ListView)parent;
            QuranRow elem = (QuranRow)p.getAdapter().getItem((int)id);
            jumpTo(elem.page);
         }
      });
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

   private class QuranRow {
      public int number;
      public int page;
      public String text;
      public String metadata;
      public boolean isHeader;

      public QuranRow(String text, String metadata, 
    		  boolean isHeader, int number, int page){
         this.text = text;
         this.isHeader = isHeader;
         this.number = number;
         this.page = page;
         this.metadata = metadata;
      }
   }
   
   private QuranRow[] getSuraList(){
      int pos = 0;
      int sura = 1;
      int next = 1;
      QuranRow[] elements = new QuranRow[SURAS_COUNT + JUZ2_COUNT];

      for (int juz=1; juz <= JUZ2_COUNT; juz++){
         elements[pos++] = new QuranRow(QuranInfo.getJuzTitle() + " " +
               juz, null, true, juz, QuranInfo.JUZ_PAGE_START[juz-1]);
         next = (juz == JUZ2_COUNT) ? PAGES_LAST+1 :
            QuranInfo.JUZ_PAGE_START[juz];
         
         while ((sura <= SURAS_COUNT) &&
                (QuranInfo.SURA_PAGE_START[sura-1] < next)) {
            String title = QuranInfo.getSuraTitle() 
                  + " " + QuranInfo.getSuraName(sura-1);
            elements[pos++] = new QuranRow(title, 
            		QuranInfo.getSuraListMetaString(sura),
            		false, sura, QuranInfo.SURA_PAGE_START[sura-1]);
            sura++;
         }
      }

      return elements;
   }
   
   private QuranRow[] getBookmarks(){
	   SharedPreferences prefs = PreferenceManager.
			   getDefaultSharedPreferences(getApplicationContext());
	   List<Integer> bookmarks = QuranUtils.getBookmarks(prefs);
	   int lastPage = prefs.getInt(ApplicationConstants.PREF_LAST_PAGE,
			   ApplicationConstants.NO_PAGE_SAVED);
	   boolean showLastPage = lastPage != ApplicationConstants.NO_PAGE_SAVED;
	   int size = bookmarks.size() + (showLastPage? 1 : 0);
	   
	   int index = 0;
	   QuranRow[] res = new QuranRow[size];
	   if (showLastPage){
		   QuranRow currentPosition = new QuranRow(
				   getString(R.string.bookmarks_current_page),
				   QuranInfo.getSuraNameString(lastPage) + " - " +
				   QuranInfo.getSuraDetailsForBookmark(lastPage),
				   false, QuranInfo.PAGE_SURA_START[lastPage], lastPage);
		   res[index++] = currentPosition;
	   }
	   
	   for (int page : bookmarks){
		   res[index++] = new QuranRow(
				   QuranInfo.getSuraNameString(page),
				   QuranInfo.getSuraDetailsForBookmark(page),
				   false, QuranInfo.PAGE_SURA_START[page], page);
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
              color = R.color.headerTextColor;
          }
          else {
              String info = item.metadata;
              holder.metadata.setVisibility(View.VISIBLE);
              holder.number.setVisibility(View.VISIBLE);
              holder.text.setVisibility(View.VISIBLE);
              holder.header.setVisibility(View.GONE);
              holder.metadata.setText(ArabicStyle.reshape(info));
          }
          holder.page.setTextColor(getResources().getColor(color));
          return convertView;
      }
            
      class ViewHolder {
          TextView text;
          TextView page;
          TextView number;
          TextView metadata;
          TextView header;
      }
  }
}