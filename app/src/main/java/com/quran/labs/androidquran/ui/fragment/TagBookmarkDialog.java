package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.app.SherlockDialogFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Tag;
import com.quran.labs.androidquran.ui.helpers.BookmarkHandler;

import java.util.ArrayList;
import java.util.List;

public class TagBookmarkDialog extends SherlockDialogFragment {
   public static final String TAG = "TagBookmarkDialog";

   private boolean mMadeChanges = false;
   private long mBookmarkId = -1;
   private long[] mBookmarkIds = null;
   private Integer mSura;
   private Integer mAyah;
   private int mPage = -1;
   private List<Tag> mTags;
   private List<Long> mBookmarkTags;
   private TagsAdapter mAdapter;

   private ListView mListView;

   private AsyncTask mCurrentTask;

   private static final String MADE_CHANGES = "madeChanges";
   private static final String BOOKMARK_ID = "bookmarkid";
   private static final String BOOKMARK_IDS = "bookmarkids";
   private static final String PAGE = "page";
   private static final String SURA = "sura";
   private static final String AYAH = "ayah";
   private static final String TAG_LIST = "taglist";

   public TagBookmarkDialog(long bookmarkId){
      mBookmarkId = bookmarkId;
   }
   
   public TagBookmarkDialog(long[] bookmarkIds){
      mBookmarkIds = bookmarkIds;
   }
   
   public TagBookmarkDialog(SuraAyah suraAyah){
     this(suraAyah.sura, suraAyah.ayah, suraAyah.getPage());
   }

   public TagBookmarkDialog(Integer sura, Integer ayah, int page){
      mSura = sura;
      mAyah = ayah;
      mPage = page;
   }

   // do not remove - this is required when resuming from onSaveInstanceState
   public TagBookmarkDialog(){
   }

   public void updateAyah(SuraAyah suraAyah) {
     updateAyah(suraAyah.sura, suraAyah.ayah, suraAyah.getPage());
   }

   public void updateAyah(int sura, int ayah, int page) {
      mMadeChanges = false;
      mBookmarkId = -1;
      mSura = sura;
      mAyah = ayah;
      mPage = page;
      new RefreshTagsTask().execute();
   }

  @Override
   public void onSaveInstanceState(Bundle outState) {
      outState.putBoolean(MADE_CHANGES, mMadeChanges);
      outState.putLong(BOOKMARK_ID, mBookmarkId);
      outState.putLongArray(BOOKMARK_IDS, mBookmarkIds);
      outState.putInt(PAGE, mPage);
      outState.putInt(SURA, mSura == null? 0 : mSura);
      outState.putInt(AYAH, mAyah == null? 0 : mAyah);
      outState.putParcelableArrayList(TAG_LIST,
              (ArrayList<? extends Parcelable>) mTags);
      super.onSaveInstanceState(outState);
   }

  public void handleTagAdded(String name) {
	   new AddTagTask().execute(name);
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      if (savedInstanceState != null){
         mMadeChanges = savedInstanceState.getBoolean(MADE_CHANGES);
         mBookmarkId = savedInstanceState.getLong(BOOKMARK_ID);
         mBookmarkIds = savedInstanceState.getLongArray(BOOKMARK_IDS);
         mSura = savedInstanceState.getInt(SURA);
         mAyah = savedInstanceState.getInt(AYAH);
         mPage = savedInstanceState.getInt(PAGE);
         mTags = savedInstanceState.getParcelableArrayList(TAG_LIST);

         if (mSura == 0){ mSura = null; }
         if (mAyah == 0){ mAyah = null; }
      }

      if (mTags == null) {
         new RefreshTagsTask().execute();
      }
   }

   private ListView createTagsListView() {
      final FragmentActivity activity = getActivity();

      mAdapter = new TagsAdapter(activity);

      mListView = new ListView(activity);
      mListView.setAdapter(mAdapter);
      mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
      mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Tag tag = (Tag)mAdapter.getItem(position);
            if (tag.mId >= 0) {
               mMadeChanges = true;
               tag.toggle();
               // If not in dialog mode, save the changes now, otherwise, on OK
               if (!getShowsDialog()) {
                  if (mCurrentTask != null) mCurrentTask.cancel(true);
                  mCurrentTask = new UpdateBookmarkTagsTask().execute();
               }
            }
            else if (tag.mId == -1) {
	           Context context = getActivity();
	           if (context != null &&
                      context instanceof OnBookmarkTagsUpdateListener) {
                  ((OnBookmarkTagsUpdateListener)context).onAddTagSelected();
	           }
            }

            if (view.getTag() != null){
               Object viewTag = view.getTag();
               if (viewTag instanceof ViewHolder){
                  ViewHolder holder = (ViewHolder)viewTag;
                  holder.checkBox.setChecked(tag.isChecked());
               }
            }
         }
      });
      return mListView;
   }

   @Override
   public Dialog onCreateDialog(Bundle savedInstanceState) {
      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setView(createTagsListView());
      builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
            if (mMadeChanges) {
               mCurrentTask = new UpdateBookmarkTagsTask().execute();
            }
         }
      });
      builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
         @Override
         public void onClick(DialogInterface dialog, int which) {
            dismiss();
         }
      });
      return builder.create();
   }

   @Override
   public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      // If in dialog mode, don't do anything (or else it will cause exception)
      if (getShowsDialog()) {
         return super.onCreateView(inflater, container, savedInstanceState);
      }
      // If not in dialog mode, treat as normal fragment onCreateView
      return createTagsListView();
   }

  public class TagsAdapter extends BaseAdapter {
      private LayoutInflater mInflater;

      public TagsAdapter(Context context){
         mInflater = LayoutInflater.from(context);
      }

      @Override
      public int getCount() {
         return mTags == null ? 0 : mTags.size();
      }

      @Override
      public Object getItem(int position) {
         return mTags.get(position);
      }

      @Override
      public long getItemId(int position) {
         return mTags.get(position).mId;
      }

      @Override
      public boolean hasStableIds() {
         return false;
      }

      @Override
      public View getView(int position, View convertView,
                          ViewGroup parent) {
         ViewHolder holder;
         if (convertView == null) {
            convertView = mInflater.inflate(R.layout.tag_row, null);
            holder = new ViewHolder();
            holder.checkBox = (CheckBox)convertView
                    .findViewById(R.id.tag_checkbox);
            holder.tagName = (TextView)convertView
                    .findViewById(R.id.tag_name);
            holder.addImage = (ImageView)convertView
                    .findViewById(R.id.tag_add_image);
            convertView.setTag(holder);
         }
         final Tag tag = (Tag)getItem(position);
         holder = (ViewHolder) convertView.getTag();
         holder.tagName.setText(tag.toString());
         if (tag.mId == -1) {
            holder.addImage.setVisibility(View.VISIBLE);
            holder.checkBox.setVisibility(View.GONE);
         }
         else {
            holder.addImage.setVisibility(View.GONE);
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(tag.isChecked());
            holder.checkBox.setOnClickListener(new OnClickListener() {
               public void onClick(View v) {
                  mMadeChanges = true;
                  tag.toggle();
               }
            });
         }
         return convertView;
      }
   }
   
   class RefreshTagsTask extends AsyncTask<Void, Void, ArrayList<Tag>> {
      @Override
      protected ArrayList<Tag> doInBackground(Void... params) {
         BookmarksDBAdapter adapter = null;
         Activity activity = getActivity();
         if (activity != null && activity instanceof BookmarkHandler) {
            adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
         }

         if (adapter == null) {
            return null;
         }

         String newTagString = activity.getString(R.string.new_tag);

         ArrayList<Tag> newTags = new ArrayList<Tag>();
         newTags.addAll(adapter.getTags());
         newTags.add(new Tag(-1, newTagString));
         if (mBookmarkIds == null) {
            if (mBookmarkId < 0 && mPage > 0) {
               mBookmarkId = adapter.getBookmarkId(mSura, mAyah, mPage);
            }
            mBookmarkTags = mBookmarkId < 0 ? null :
                    adapter.getBookmarkTagIds(mBookmarkId);
         } else {
            mBookmarkTags = null;
         }
         return newTags;
      }
      
      @Override
      protected void onPostExecute(ArrayList<Tag> result) {
         if (result != null) {
            for (Tag tag : result) {
               if (mBookmarkTags != null && mBookmarkTags.contains(tag.mId)) {
                  tag.setChecked(true);
               }
            }
            mMadeChanges = false;
            mTags = result;
            if (mAdapter != null) {
               mAdapter.notifyDataSetChanged();
            }
         }
      }
   }

   class ViewHolder {
      CheckBox checkBox;
      TextView tagName;
      ImageView addImage;
   }
   
   class AddTagTask extends AsyncTask<String, Void, Tag> {
       @Override
       protected Tag doInBackground(String... params) {
          BookmarksDBAdapter adapter = null;
          Activity activity = getActivity();
          if (activity != null && activity instanceof BookmarkHandler){
             adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
          }

          if (adapter == null){ return null; }
          long id = adapter.addTag(params[0]);
          Tag t = new Tag(id, params[0]);
          return t;
       }
       @Override
       protected void onPostExecute(Tag result) {
          if (result != null && mTags != null && mAdapter != null) {
             mMadeChanges = true;
             result.setChecked(true);
             mTags.add(mTags.size() - 1, result);
             mAdapter.notifyDataSetChanged();
          }
       };
   }
   
   class UpdateBookmarkTagsTask extends AsyncTask<Void, Void, Void> {

      @Override
      protected Void doInBackground(Void... params) {
         BookmarksDBAdapter adapter = null;
         Activity activity = getActivity();
         if (activity != null && activity instanceof BookmarkHandler) {
            adapter = ((BookmarkHandler) activity).getBookmarksAdapter();
         }

         if (adapter == null) {
            return null;
         }

         if (mBookmarkIds == null) {
            if (mBookmarkId < 0) {
               mBookmarkId = adapter.addBookmarkIfNotExists(mSura,
                       mAyah, mPage);
            }
            adapter.tagBookmark(mBookmarkId, mTags);
         } else {
            adapter.tagBookmarks(mBookmarkIds, mTags);
         }
         return null;
      }

      @Override
      protected void onPostExecute(Void result) {
         mCurrentTask = null;
         mMadeChanges = false;
         final Activity activity = getActivity();
         if (activity != null && activity instanceof OnBookmarkTagsUpdateListener) {
            ((OnBookmarkTagsUpdateListener)activity).onBookmarkTagsUpdated();
         }
      }
   }

   public interface OnBookmarkTagsUpdateListener {
      public void onBookmarkTagsUpdated();
      public void onAddTagSelected();
   }
   
}
