package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
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
import com.actionbarsherlock.view.SubMenu;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

public abstract class AbsMarkersFragment extends SherlockFragment {

   private ListView mListView;
   private ActionMode mMode;
   private QuranListAdapter mAdapter;
   private TextView mEmptyTextView;
   private AsyncTask<Void, Void, QuranRow[]> loadingTask = null;
   protected int mCurrentSortCriteria = 0;

   protected abstract int getContextualMenuId();
   protected abstract int getEmptyListStringId();
   protected abstract int[] getValidSortOptions();
   protected abstract boolean prepareActionMode(ActionMode mode, Menu menu, QuranRow selected);
   protected abstract boolean actionItemClicked(ActionMode mode, int menuItemId, QuranActivity activity, QuranRow selected);
   protected abstract QuranRow[] getItems();
   
   @Override
   public View onCreateView(LayoutInflater inflater,
         ViewGroup container, Bundle savedInstanceState){
      setHasOptionsMenu(true);
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

   @Override
   public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
      super.onCreateOptionsMenu(menu, inflater);
      MenuItem sortItem = menu.findItem(R.id.sort);
      sortItem.setVisible(true);
      sortItem.setEnabled(true);
      SubMenu subMenu = sortItem.getSubMenu();
      for (int validOption : getValidSortOptions()) {
         MenuItem subMenuItem = subMenu.findItem(validOption);
         subMenuItem.setVisible(true);
         subMenuItem.setEnabled(true);
      }
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      int id = item.getItemId();
      for (int validId : getValidSortOptions()) {
         if (id == validId) {
            mCurrentSortCriteria = id;
            refreshData();
            return true;
         }
      }
      return false;
   }

   protected void finishActionMode() {
      mMode.finish();
   }

   private class ModeCallback implements ActionMode.Callback {

      @Override
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
         MenuInflater inflater = getSherlockActivity().getSupportMenuInflater();
         inflater.inflate(getContextualMenuId(), menu);
         return true;
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
         int position = mListView.getCheckedItemPosition();
         boolean positionValid = position >= 0 && position < mAdapter.getCount();
         QuranRow selected = positionValid ? (QuranRow)mAdapter.getItem(position) : null;
         return prepareActionMode(mode, menu, selected);
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
         QuranActivity activity = (QuranActivity)getActivity();
         int position = mListView.getCheckedItemPosition();
         boolean positionValid = position >= 0 && position < mAdapter.getCount();
         QuranRow selected = positionValid ? (QuranRow)mAdapter.getItem(position) : null;
         return actionItemClicked(mode, item.getItemId(), activity, selected);
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

   class RemoveBookmarkTask extends AsyncTask<QuranRow, Void, Void> {
      @Override
      protected Void doInBackground(QuranRow... params) {
         Activity activity = getActivity();
         if (activity == null){ return null; }

         QuranActivity quranActivity = (QuranActivity)activity;
         BookmarksDBAdapter db = quranActivity.getBookmarksAdapter();

         // TODO Confirm dialog
         for (int i = 0; i < params.length; i++) {
            QuranRow elem = params[i];
            if (elem.isBookmarkHeader() && elem.tagId >= 0) {
               db.removeTag(elem.tagId);
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
         return getItems();
      }

      @Override
      protected void onPostExecute(QuranRow[] result) {
         if (result.length == 0){
            mEmptyTextView.setText(getEmptyListStringId());
         }
         mAdapter.setElements(result);
         mAdapter.notifyDataSetChanged();
         loadingTask = null;
         
         if (mMode != null)
            mMode.invalidate();
      }
   }

}
