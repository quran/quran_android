package com.quran.labs.androidquran.ui.fragment;

import java.util.List;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.AyahBookmark;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

public class BookmarksFragment extends SherlockFragment {

   private ListView mListView;
   private QuranListAdapter mAdapter;
   private TextView mEmptyTextView;
   private AsyncTask<Void, Void, QuranRow[]> loadingTask = null;

   public static BookmarksFragment newInstance(){
      return new BookmarksFragment();
   }
   
   @Override
   public View onCreateView(LayoutInflater inflater,
         ViewGroup container, Bundle savedInstanceState){
      View view = inflater.inflate(R.layout.quran_list, container, false);
      mListView = (ListView)view.findViewById(R.id.list);
      mEmptyTextView = (TextView)view.findViewById(R.id.empty_list);
      mListView.setEmptyView(mEmptyTextView);
      
      mAdapter = new QuranListAdapter(getActivity(),
            R.layout.index_sura_row, new QuranRow[]{});
      mListView.setAdapter(mAdapter);
      
      mListView.setOnItemClickListener(new OnItemClickListener(){
         public void onItemClick(AdapterView<?> parent, View v,
               int position, long id){
            QuranRow elem = (QuranRow)mAdapter.getItem((int)id);
            if (elem.page > 0){
               ((QuranActivity)getActivity()).jumpTo(elem.page);
            }
         }
      });
      
      return view;
   }
   
   @Override
   public void onResume() {
      super.onResume();
      if (loadingTask == null){
         loadingTask = new BookmarksLoadingTask();
         loadingTask.execute();
      }
   }
   
   @Override
   public void onPause() {
      super.onPause();
      if (loadingTask != null){
         loadingTask.cancel(true);
      }
      loadingTask = null;
   }

   private class BookmarksLoadingTask extends AsyncTask<Void, Void, QuranRow[]> {
      @Override
      protected QuranRow[] doInBackground(Void... params) {
         return getBookmarks();
      }

      @Override
      protected void onPostExecute(QuranRow[] result) {
         if (result.length == 0){
            mEmptyTextView.setText(R.string.no_bookmarks);
         }
         mAdapter.setElements(result);
         mAdapter.notifyDataSetChanged();
         loadingTask = null;
      }
   }

   private QuranRow[] getBookmarks(){
      BookmarksDBAdapter db = new BookmarksDBAdapter(getActivity());
      db.open();
      List<Integer> pageBookmarks = db.getPageBookmarks();
      List<AyahBookmark> ayahBookmarks = db.getAyahBookmarks();
      db.close();
      
      SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(
                  getActivity().getApplicationContext());
      int lastPage = prefs.getInt(Constants.PREF_LAST_PAGE,
            Constants.NO_PAGE_SAVED);
      boolean showLastPage = lastPage != Constants.NO_PAGE_SAVED;
      boolean showPageBookmarkHeader = pageBookmarks.size() != 0;
      boolean showAyahBookmarkHeader = ayahBookmarks.size() != 0;
      int size = pageBookmarks.size() + ayahBookmarks.size() + (showLastPage? 2 : 0) +
            (showPageBookmarkHeader? 1 : 0) + (showAyahBookmarkHeader? 1 : 0);

      Activity activity = getActivity();

      int index = 0;
      QuranRow[] res = new QuranRow[size];
      if (showLastPage){
         QuranRow header = new QuranRow(
               getString(R.string.bookmarks_current_page),
               null, true, 0, 0, null);
         QuranRow currentPosition = new QuranRow(
               QuranInfo.getSuraNameString(activity, lastPage),
               QuranInfo.getPageSubtitle(activity, lastPage),
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
               QuranInfo.getSuraNameString(activity, page),
               QuranInfo.getPageSubtitle(activity, page),
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
               QuranInfo.getAyahString(ayah.sura, ayah.ayah, getActivity()),
               QuranInfo.getPageSubtitle(activity, ayah.page),
               false, ayah.sura, ayah.page,
               R.drawable.bookmark_page);
      }      
      return res;
   }
   
}
