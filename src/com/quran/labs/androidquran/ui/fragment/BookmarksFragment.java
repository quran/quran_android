package com.quran.labs.androidquran.ui.fragment;

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
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Bookmark;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.quran.labs.androidquran.database.BookmarksDBAdapter.BookmarkCategory;

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
      mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
      mListView.setItemsCanFocus(false);
      
      mListView.setOnItemClickListener(new OnItemClickListener(){
         public void onItemClick(AdapterView<?> parent, View v,
               int position, long id){
            QuranRow elem = (QuranRow)mAdapter.getItem((int)id);
            
            // If we're in CAB mode don't handle the click
            if (mMode != null){
               mListView.setItemChecked(position,
                       mListView.isItemChecked(position));
               mMode.invalidate();
               return;
            } else {
               mListView.setItemChecked(position, false);
            }
            
            // We're not in CAB mode so handle the click normally
            if (!elem.isHeader()) {
               if (elem.isAyahBookmark()){
                  ((QuranActivity)getActivity()).jumpToAndHighlight(
                        elem.page, elem.sura, elem.ayah);
               }
               else {
                  ((QuranActivity)getActivity()).jumpTo(elem.page);
               }
            }
         }
      });
      
      mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
         public boolean onItemLongClick(AdapterView<?> parent, View view,
               int position, long id) {
            if (!mListView.isItemChecked(position)) {
               mListView.setItemChecked(position, true);
               if (mMode != null)
                  mMode.invalidate();
               else
                  mMode = getSherlockActivity().startActionMode(
                          new ModeCallback());
            } else if (mMode != null) {
               mMode.finish();
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
         MenuInflater inflater =
                 getSherlockActivity().getSupportMenuInflater();
         inflater.inflate(R.menu.bookmark_menu, menu);
         return true;
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
         MenuItem editItem = menu.findItem(R.id.cab_edit_bookmark);
         MenuItem removeItem = menu.findItem(R.id.cab_remove_bookmark);
         int position = mListView.getCheckedItemPosition();
         if (position < 0 || position >= mAdapter.getCount()) {
            editItem.setVisible(false);
            removeItem.setVisible(false);
            return true;
         }

         QuranRow selected = (QuranRow)mAdapter.getItem(position);
         if (selected.isBookmarkHeader()) {
            if (selected.categoryId > 0){
               editItem.setVisible(true);
               removeItem.setVisible(true);
            }
            else {
               editItem.setVisible(false);
               removeItem.setVisible(false);
            }
         }
         else if (selected.isBookmark()) {
            editItem.setVisible(false);
            removeItem.setVisible(true);
         }
         else {
            editItem.setVisible(false);
            removeItem.setVisible(false);
         }
         return true;
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
         QuranActivity activity = (QuranActivity)getActivity();
         switch (item.getItemId()) {
         case R.id.cab_remove_bookmark:
            int position = mListView.getCheckedItemPosition();
            if (position < 0 || position >= mAdapter.getCount())
               return false;
            QuranRow row = (QuranRow)mAdapter.getItem(position);
            new RemoveBookmarkTask().execute(row);
            return true;
         case R.id.cab_new_bookmark:
            activity.addCategory();
            mMode.finish();
            return true;
         case R.id.cab_edit_bookmark:
            int selectedBookmark = mListView.getCheckedItemPosition();
            if (selectedBookmark < 0 ||
                    selectedBookmark >= mAdapter.getCount()){
               return false;
            }
            QuranRow category = (QuranRow)mAdapter.getItem(selectedBookmark);
            if (!category.isBookmarkHeader() || category.categoryId < 0){
               return false;
            }
            activity.editCategory(category.categoryId,
                    category.text, category.metadata);
            mMode.finish();
            return true;
         default:
            return false;
         }
      }

      @Override
      public void onDestroyActionMode(ActionMode mode) {
         int checkedPosition = mListView.getCheckedItemPosition();
         if (checkedPosition >= 0 && checkedPosition < mAdapter.getCount()){
            mListView.setItemChecked(checkedPosition, false);
         }
         if (mode == mMode){ mMode = null; }
      }
      
   }

   private class RemoveBookmarkTask extends AsyncTask<QuranRow, Void, Void> {
      @Override
      protected Void doInBackground(QuranRow... params) {
         Activity activity = getActivity();
         if (activity == null){ return null; }

         QuranActivity quranActivity = (QuranActivity)activity;
         BookmarksDBAdapter db = quranActivity.getBookmarksAdapter();

         for (int i = 0; i < params.length; i++) {
            QuranRow elem = params[i];
            if (elem.isBookmarkHeader() && elem.categoryId >= 0) {
               db.removeCategory(elem.categoryId, true);
            }
            else if (elem.isBookmark() && elem.bookmarkId >= 0) {
               db.removeBookmark(elem.bookmarkId);
            }
         }
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

   public void refreshData(){
      if (loadingTask != null){
         loadingTask.cancel(true);
      }
      loadingTask = new BookmarksLoadingTask();
      loadingTask.execute();
   }
   
   private class BookmarksLoadingTask extends
           AsyncTask<Void, Void, QuranRow[]> {
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
         
         if (mMode != null)
            mMode.invalidate();
      }
   }

   private QuranRow[] getBookmarks(){
      Activity activity = getActivity();
      if (activity == null){ return null; }

      QuranActivity quranActivity = (QuranActivity)activity;
      BookmarksDBAdapter db = quranActivity.getBookmarksAdapter();
      List<BookmarkCategory> categories = db.getCategories();
      List<Bookmark> bookmarks = db.getBookmarks();

      // add uncategorized category
      categories.add(new BookmarkCategory(0,
              getString(R.string.sample_bookmark_uncategorized), null));
      
      List<QuranRow> rows = new ArrayList<QuranRow>();
      
      SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(
                  getActivity().getApplicationContext());
      int lastPage = prefs.getInt(Constants.PREF_LAST_PAGE,
            Constants.NO_PAGE_SAVED);
      if (lastPage != Constants.NO_PAGE_SAVED){
         QuranRow header = new QuranRow(
               getString(R.string.bookmarks_current_page),
               null, QuranRow.HEADER, 0, 0, null);
         rows.add(header);
         QuranRow currentPosition = new QuranRow(
               QuranInfo.getSuraNameString(activity, lastPage),
               QuranInfo.getPageSubtitle(activity, lastPage),
               QuranInfo.PAGE_SURA_START[lastPage-1], lastPage,
               R.drawable.bookmark_page);
         rows.add(currentPosition);
      }

      Map<Long, List<Bookmark>> bookmarkMap =
              new HashMap<Long, List<Bookmark>>();
      for (Bookmark bookmark : bookmarks){
         Long category = bookmark.mCategoryId;
         if (category == null){ category = 0l; }

         List<Bookmark> catBookmarks = bookmarkMap.get(category);
         if (catBookmarks == null){
            catBookmarks = new ArrayList<Bookmark>();
         }
         catBookmarks.add(bookmark);
         bookmarkMap.put(category, catBookmarks);
      }
      
      for (BookmarkCategory category : categories) {
         List<Bookmark> catBookmarks = bookmarkMap.get(category.mId);

         // add the category itself
         QuranRow bookmarkHeader = new QuranRow(
                 category.mName,
                 category.mDescription,
                 QuranRow.BOOKMARK_HEADER, 0, 0, null);
         bookmarkHeader.categoryId = category.mId;

         // don't add "uncategorized" if there is nothing there
         if (category.mId == 0 && catBookmarks == null){ continue; }
         rows.add(bookmarkHeader);

         // no bookmarks in this category, so move on
         if (catBookmarks == null){ continue; }

         // and now the bookmarks
         for (Bookmark bookmark : catBookmarks) {
            QuranRow row = null;
            if (bookmark.mSura == null) {
               int sura = QuranInfo.getSuraNumberFromPage(bookmark.mPage);
               row = new QuranRow(
                     QuranInfo.getSuraNameString(activity, bookmark.mPage),
                     QuranInfo.getPageSubtitle(activity, bookmark.mPage),
                     QuranRow.PAGE_BOOKMARK, sura, bookmark.mPage,
                     R.drawable.bookmark_page);
            } else {
               row = new QuranRow(
                     QuranInfo.getAyahString(bookmark.mSura,
                             bookmark.mAyah, getActivity()),
                     QuranInfo.getPageSubtitle(activity, bookmark.mPage),
                     QuranRow.AYAH_BOOKMARK, bookmark.mSura,
                       bookmark.mAyah, bookmark.mPage,
                     R.drawable.bookmark_ayah);
            }
            row.categoryId = (bookmark.mCategoryId == null)?
                    0 : bookmark.mCategoryId;
            row.bookmarkId = bookmark.mId;
            rows.add(row);
         }
      }
      
      return rows.toArray(new QuranRow[rows.size()]);
   }
}
