package com.quran.labs.androidquran.ui.fragment;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
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
   private ActionMode mMode;
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
      
      mMode = null;
      mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      mListView.setItemsCanFocus(false);
      
      mListView.setOnItemClickListener(new OnItemClickListener(){
         public void onItemClick(AdapterView<?> parent, View v,
               int position, long id){
            QuranRow elem = (QuranRow)mAdapter.getItem((int)id);
            
            // If we're not in CAB mode or the element shouldn't
            // be checked then undo the checking
            if (mMode == null || !elem.isBookmark())
               mListView.setItemChecked(position, false);
            
            // If we're in CAB mode don't handle the click
            if (mMode != null)
               return;
            
            // We're not in CAB mode so handle the click normally
            if (!elem.isHeader()) {
               if (elem.isAyahBookmark())
                  ((QuranActivity)getActivity()).jumpToAndHighlight(
                        elem.page, elem.sura, elem.ayah);
               else
                  ((QuranActivity)getActivity()).jumpTo(elem.page);
            }
         }
      });
      
      mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
         public boolean onItemLongClick(AdapterView<?> parent, View view,
               int position, long id) {
            final QuranRow elem = (QuranRow)mAdapter.getItem((int)id);
            
            // Activate CAB only for bookmarks
            if (!elem.isBookmark())
               return false;
            
            // Long click toggles checked state
            boolean itemIsChecked = mListView.isItemChecked(position);
            mListView.setItemChecked(position, !itemIsChecked);
            
            // If no more rows are selected, finish the CAB
            boolean hasCheckedElement = false;
            SparseBooleanArray checked = mListView.getCheckedItemPositions();
            for (int i = 0; i < checked.size() && !hasCheckedElement; i++) {
               hasCheckedElement = checked.valueAt(i);
            }
            if (hasCheckedElement) {
               if (mMode == null) {
                  mMode = getSherlockActivity().startActionMode(new ModeCallback());
               }
            } else {
               if (mMode != null) {
                  mMode.finish();
               }
            }
            return true;
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
      // If currently in CAB mode, end it
      if (mMode != null) {
         mMode.finish();
      }
   }

   private class ModeCallback implements ActionMode.Callback {

      @Override
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
         MenuInflater inflater = getSherlockActivity().getSupportMenuInflater();
         inflater.inflate(R.menu.bookmark_menu, menu);
         return true;
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
         return false;
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
         switch (item.getItemId()) {
         case R.id.cab_remove_bookmark:
            List<QuranRow> rows = new ArrayList<QuranRow>();
            SparseBooleanArray sel = mListView.getCheckedItemPositions();
            for (int i = 0; i < sel.size(); i++) {
               if (sel.valueAt(i)) {
                  QuranRow elem = (QuranRow)mAdapter.getItem(sel.keyAt(i));
                  if (elem.isBookmark())
                     rows.add(elem);
               }
            }
            QuranRow[] rowsArr = rows.toArray(new QuranRow[rows.size()]);
            new RemoveBookmarkTask().execute(rowsArr);
            return true;
         case R.id.cab_select_all_bookmarks:
            for (int i = 0; i < mAdapter.getCount(); i++) {
               QuranRow elem = (QuranRow)mAdapter.getItem(i);
               if (elem.isBookmark()) {
                  mListView.setItemChecked(i, true);
               }
            }
            return true;
         default:
            return false;
         }
      }

      @Override
      public void onDestroyActionMode(ActionMode mode) {
         for (int i = 0; i < mAdapter.getCount(); i++)
            mListView.setItemChecked(i, false);
         if (mode == mMode)
            mMode = null;
      }
      
   }
   
   private class RemoveBookmarkTask extends AsyncTask<QuranRow, Void, Void> {
      @Override
      protected Void doInBackground(QuranRow... params) {
         BookmarksDBAdapter db = new BookmarksDBAdapter(getActivity());
         db.open();
         for (int i = 0; i < params.length; i++) {
            QuranRow elem = params[i];
            // Ideally should have a method for setting bookmark rather than toggling,
            // but don't want to break anything until after Ramadan :)
            if (elem.isPageBookmark())
               db.togglePageBookmark(elem.page);
            else if (elem.isAyahBookmark())
               db.toggleAyahBookmark(elem.page, elem.sura, elem.ayah);
         }
         db.close();
         return null;
      }
      
      @Override
      protected void onPostExecute(Void result) {
          if (loadingTask == null){
              loadingTask = new BookmarksLoadingTask();
              loadingTask.execute();
           }
      }
   }

   private class BookmarksLoadingTask extends AsyncTask<Void, Void, QuranRow[]> {
      @Override
      protected QuranRow[] doInBackground(Void... params) {
         return getBookmarks();
      }

      @Override
      protected void onPostExecute(QuranRow[] result) {
         // If in CAB mode, wait until we reach here before finishing it to make
         // the UI seem smoother. Otherwise, on slow devices, it closes the CAB,
         // then a bit later the list refreshes and the removed bookmarks disappear.
         // Maybe have an indeterminate progress bar instead.
         if (mMode != null)
            mMode.finish();
         
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
               null, QuranRow.HEADER, 0, 0, null);
         QuranRow currentPosition = new QuranRow(
               QuranInfo.getSuraNameString(activity, lastPage),
               QuranInfo.getPageSubtitle(activity, lastPage),
               QuranInfo.PAGE_SURA_START[lastPage-1], lastPage,
               R.drawable.bookmark_currentpage);
         res[index++] = header;
         res[index++] = currentPosition;
      }
      
      if (showPageBookmarkHeader){
         res[index++] = new QuranRow(getString(R.string.menu_bookmarks_page),
               null, QuranRow.HEADER, 0, 0, null);
      }
      for (int page : pageBookmarks){
         res[index++] = new QuranRow(
               QuranInfo.getSuraNameString(activity, page),
               QuranInfo.getPageSubtitle(activity, page),
               QuranRow.PAGE_BOOKMARK, QuranInfo.PAGE_SURA_START[page-1], page,
               R.drawable.bookmark_page);
      }
      
      if (showAyahBookmarkHeader){
         res[index++] = new QuranRow(getString(R.string.menu_bookmarks_ayah),
               null, QuranRow.HEADER, 0, 0, null);
      }
      for (AyahBookmark ayah : ayahBookmarks){
         res[index++] = new QuranRow(
               QuranInfo.getAyahString(ayah.sura, ayah.ayah, getActivity()),
               QuranInfo.getPageSubtitle(activity, ayah.page),
               QuranRow.AYAH_BOOKMARK, ayah.sura, ayah.ayah, ayah.page,
               R.drawable.bookmark_ayah);
      }      
      return res;
   }
   
}
