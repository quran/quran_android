package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.BookmarkHandler;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;
import com.quran.labs.androidquran.util.QuranSettings;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public abstract class AbsMarkersFragment extends Fragment {

   private ListView mListView;
   private ActionMode mMode;
   private QuranListAdapter mAdapter;
   private TextView mEmptyTextView;
   private AsyncTask<Void, Void, QuranRow[]> loadingTask = null;
   private SharedPreferences mPrefs = null;
   protected int mCurrentSortCriteria = 0;

   protected abstract int getContextualMenuId();
   protected abstract int getEmptyListStringId();
   protected abstract int[] getValidSortOptions();
   protected abstract String getSortPref();
   protected abstract boolean isValidSelection(QuranRow selected);
   protected abstract boolean prepareActionMode(ActionMode mode,
                                                Menu menu,
                                                QuranRow[] selected);

   protected abstract boolean actionItemClicked(ActionMode mode,
                                                int menuItemId,
                                                QuranActivity activity,
                                                QuranRow[] selected);

   protected abstract QuranRow[] getItems();

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      mMode = null;
      mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
      mCurrentSortCriteria = mPrefs.getInt(getSortPref(), 0);
   };
   
   @Override
   public View onCreateView(LayoutInflater inflater,
         ViewGroup container, Bundle savedInstanceState){
      setHasOptionsMenu(true);
      View view = inflater.inflate(R.layout.quran_list, container, false);
     mListView = (ListView)view.findViewById(R.id.list);
     mEmptyTextView = (TextView)view.findViewById(R.id.empty_list);
     mListView.setEmptyView(mEmptyTextView);

     mAdapter = new QuranListAdapter(getActivity(),
         R.layout.index_sura_row, new QuranRow[]{}, false);

     mListView.setAdapter(mAdapter);

     mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
     mListView.setItemsCanFocus(false);

     mListView.setOnItemClickListener(new OnItemClickListener(){
       public void onItemClick(AdapterView<?> parent, View v,
           int position, long id){
         QuranRow elem = (QuranRow)mAdapter.getItem((int)id);

         // If we're in CAB mode don't handle the click
         if (mMode != null){
           boolean checked = isValidSelection(elem) && mListView.isItemChecked(position);
           mListView.setItemChecked(position, checked);
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
         QuranRow elem = (QuranRow)mAdapter.getItem((int)id);
         if (!isValidSelection(elem)) {
           return false;
         } else if (!mListView.isItemChecked(position)) {
           mListView.setItemChecked(position, true);
           if (mMode != null)
             mMode.invalidate();
           else
             mMode = ((ActionBarActivity) getActivity())
                 .startSupportActionMode(new ModeCallback());
         } else if (mMode != null) {
           mMode.finish();
         }

         return true;
       }
     });

      return view;
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
  public void onResume() {
    super.onResume();
    final Activity activity = getActivity();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
        QuranSettings.isArabicNames(activity)) {
      updateScrollBarPositionHoneycomb();
    }

    loadingTask = new BookmarksLoadingTask();
    loadingTask.execute();
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void updateScrollBarPositionHoneycomb() {
    mListView.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_LEFT);
  }

  @Override
   public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
      super.onCreateOptionsMenu(menu, inflater);
      MenuItem sortItem = menu.findItem(R.id.sort);
      if (sortItem != null){
         sortItem.setVisible(true);
         sortItem.setEnabled(true);
         SubMenu subMenu = sortItem.getSubMenu();
         for (int validOption : getValidSortOptions()) {
            MenuItem subMenuItem = subMenu.findItem(validOption);
            subMenuItem.setVisible(true);
            subMenuItem.setEnabled(true);
         }
      }
   }

   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      int id = item.getItemId();
      for (int validId : getValidSortOptions()) {
         if (id == validId) {
            mCurrentSortCriteria = id;
            mPrefs.edit().putInt(getSortPref(), id).commit();
            refreshData();
            return true;
         }
      }
      return false;
   }

   private class ModeCallback implements ActionMode.Callback {

      @Override
      public boolean onCreateActionMode(ActionMode mode, Menu menu) {
         MenuInflater inflater = getActivity().getMenuInflater();
         inflater.inflate(getContextualMenuId(), menu);
         return true;
      }

      private QuranRow[] getSelectedRows() {
         List<QuranRow> rows = new ArrayList<QuranRow>();
         SparseBooleanArray sel = mListView.getCheckedItemPositions();
         for (int i = 0; i < sel.size(); i++) {
            if (sel.valueAt(i)) {
               int position = sel.keyAt(i);
               if (position < 0 || position >= mAdapter.getCount())
                  continue;
               QuranRow elem = (QuranRow)mAdapter.getItem(position);
               rows.add(elem);
            }
         }
         return rows.toArray(new QuranRow[rows.size()]);
      }

      @Override
      public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
         QuranRow[] selected = getSelectedRows();
         return prepareActionMode(mode, menu, selected);
      }

      @Override
      public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
         QuranActivity activity = (QuranActivity)getActivity();
         QuranRow[] selected = getSelectedRows();
         mMode.finish();
         return actionItemClicked(mode, item.getItemId(), activity, selected);
      }

      @Override
      public void onDestroyActionMode(ActionMode mode) {
         for (int i = 0; i < mAdapter.getCount(); i++)
            mListView.setItemChecked(i, false);
         if (mode == mMode){ mMode = null; }
      }
      
   }

   class RemoveBookmarkTask extends AsyncTask<QuranRow, Void, Boolean> {
      boolean mUntagOnly = false;
      public RemoveBookmarkTask(){}
      public RemoveBookmarkTask(boolean untagOnly){ mUntagOnly = untagOnly; }

      @Override
      protected Boolean doInBackground(QuranRow... params) {
         BookmarksDBAdapter adapter = null;
         Activity activity = getActivity();
         if (activity != null && activity instanceof BookmarkHandler){
            adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
         }

         if (adapter == null){ return null; }

         // TODO Confirm dialog
         boolean bookmarkDeleted = false;
         for (int i = 0; i < params.length; i++) {
            QuranRow elem = params[i];
            if (elem.isBookmarkHeader() && elem.tagId >= 0) {
               adapter.removeTag(elem.tagId);
            }
            else if (elem.isBookmark() && elem.bookmarkId >= 0) {
               if (elem.tagId >= 0 && mUntagOnly) {
                  adapter.untagBookmark(elem.bookmarkId, elem.tagId);
               } else {
                  adapter.removeBookmark(elem.bookmarkId);
                  bookmarkDeleted = true;
               }
            }
         }
         return bookmarkDeleted;
      }
      
      @Override
      protected void onPostExecute(Boolean result) {
         if (loadingTask == null){
            loadingTask = new BookmarksLoadingTask();
            loadingTask.execute();
         }
         QuranActivity activity = (QuranActivity)getActivity();
         if (activity != null)
            activity.onBookmarkDeleted();
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
