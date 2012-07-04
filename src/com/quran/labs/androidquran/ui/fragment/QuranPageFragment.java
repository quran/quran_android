package com.quran.labs.androidquran.ui.fragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.AyahTag;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

public class QuranPageFragment extends SherlockFragment {
   private static final String TAG = "QuranPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private int mPageNumber;
   private boolean mIsBookmarked;
   private HighlightingImageView mImageView;
   private PaintDrawable mLeftGradient, mRightGradient = null;

   public static QuranPageFragment newInstance(int page){
      final QuranPageFragment f = new QuranPageFragment();
      final Bundle args = new Bundle();
      args.putInt(PAGE_NUMBER_EXTRA, page);
      f.setArguments(args);
      return f;
   }

   @Override
   public void onCreate(Bundle savedInstanceState){
      super.onCreate(savedInstanceState);
      mPageNumber = getArguments() != null? getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
      int width = getActivity().getWindowManager()
            .getDefaultDisplay().getWidth();
      mLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      mRightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      setHasOptionsMenu(true);
      
      new IsPageBookmarkedTask().execute(mPageNumber);
   }

   @Override
   public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
      final View view = inflater.inflate(R.layout.quran_page_layout, container, false);
      view.setBackgroundDrawable((mPageNumber % 2 == 0? mLeftGradient : mRightGradient));

      ImageView leftBorder = (ImageView)view.findViewById(R.id.left_border);
      ImageView rightBorder = (ImageView)view.findViewById(R.id.right_border);
      if (mPageNumber % 2 == 0){
         rightBorder.setVisibility(View.GONE);
         leftBorder.setBackgroundResource(R.drawable.border_left);
      }
      else {
         rightBorder.setVisibility(View.VISIBLE);
         rightBorder.setBackgroundResource(R.drawable.border_right);
         leftBorder.setBackgroundResource(R.drawable.dark_line);
      }

      mImageView = (HighlightingImageView)view.findViewById(R.id.page_image);
      
      final GestureDetector gestureDetector = new GestureDetector(
            new PageGestureDetector());
      OnTouchListener gestureListener = new OnTouchListener() {
         @Override
         public boolean onTouch(View v, MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
         }
      };
      mImageView.setOnTouchListener(gestureListener);
      mImageView.setClickable(true);
      mImageView.setLongClickable(true);
      return view;
   }

   @Override
   public void onActivityCreated(Bundle savedInstanceState){
      super.onActivityCreated(savedInstanceState);
      if (PagerActivity.class.isInstance(getActivity())){
         QuranPageWorker worker = ((PagerActivity)getActivity()).getQuranPageWorker();
         worker.loadPage(mPageNumber, mImageView);
      }
   }
   
   @Override
   public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
      super.onCreateOptionsMenu(menu, inflater);
      inflater.inflate(R.menu.quran_menu, menu);
   }
   
   @Override
   public void onPrepareOptionsMenu(Menu menu) {
      super.onPrepareOptionsMenu(menu);
      MenuItem item = menu.findItem(R.id.favorite_item);
      if (item != null){
         if (mIsBookmarked){ item.setIcon(R.drawable.favorite); }
         else { item.setIcon(R.drawable.not_favorite); }
      }
   }
   
   @Override
   public boolean onOptionsItemSelected(MenuItem item) {
      if (item.getItemId() == R.id.favorite_item){
         new TogglePageBookmarkTask().execute(mPageNumber);
         return true;
      }
      return super.onOptionsItemSelected(item);
   }
   
   public void cleanup(){
      android.util.Log.d(TAG, "cleaning up page " + mPageNumber);
      mImageView.setImageDrawable(null);
      mImageView = null;
   }

   public void highlightAyah(int sura, int ayah){
      mImageView.highlightAyah(sura, ayah);
      mImageView.invalidate();
   }

   public void unhighlightAyah(){
      mImageView.unhighlight();
   }

   private class PageGestureDetector extends SimpleOnGestureListener {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent event) {
    	  ((PagerActivity)getActivity()).toggleActionBar();
          return true;
      }

      @Override
      public boolean onDoubleTap(MotionEvent event) {
         AyahItem result = getAyahFromCoordinates(event.getX(), event.getY());
         if (result != null) {
            new TogglePageBookmarkTask().execute(mPageNumber);
            return true;
         }
         return false;
      }

      @Override
      public void onLongPress(MotionEvent event) {
         AyahItem result = getAyahFromCoordinates(event.getX(), event.getY());
         if (result != null) {
            mImageView.highlightAyah(result.getSoura(), result.getAyah());
            mImageView.invalidate();
            mImageView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            
            // TODO Temporary UI (including all sub-tasks) until new UI is implemented
            new ShowAyahMenuTask(mPageNumber, result.getSoura(), result.getAyah()).execute();
         }
      }

      private AyahItem getAyahFromCoordinates(float x, float y) {
         float[] pageXY = mImageView.getPageXY(x, y);
         AyahItem result = null;
         if (pageXY != null) {
            String filename = QuranFileUtils.getAyaPositionFileName();
            AyahInfoDatabaseHandler handler = new AyahInfoDatabaseHandler(filename);
            try {
               result = handler.getVerseAtPoint(mPageNumber, pageXY[0], pageXY[1]);
            } catch (Exception e) {
               Log.e(TAG, e.getMessage(), e);
            } finally {
               handler.closeDatabase();
            }
         }
         return result;
      }
   }
   
   class ShowAyahMenuTask extends AsyncTask<Void, Void, Boolean> {
		int page, sura, ayah;
		
		public ShowAyahMenuTask(int page, int sura, int ayah) {
			this.page = page;
			this.sura = sura;
			this.ayah = ayah;
		}
		
		@Override
		protected Boolean doInBackground(Void... params) {
			BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
			dba.open();
			boolean result = dba.isAyahBookmarked(sura, ayah);
			dba.close();
			return result;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			final CharSequence[] options = { result ? "Unbookmark" : "Bookmark",
					"Notes", "Tags", "Tafsir", "Ayah ID" };
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(QuranInfo.getAyahString(sura, ayah, getActivity()));
			builder.setItems(options, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int selection) {
					if (selection == 0)
						new ToggleAyahBookmarkTask().execute(mPageNumber, sura, ayah);
					else if (selection == 1)
						new ShowNotesDialogTask(page, sura, ayah).execute();
					else if (selection == 2)
						new ShowTagsDialogTask(page, sura, ayah).execute();
					else if (selection == 3)
						Toast.makeText(getActivity(), "Not Implemented", Toast.LENGTH_SHORT).show();
					else if (selection == 4)
						Toast.makeText(getActivity(), "Ayah "+QuranInfo.getAyahId(
								sura, ayah), Toast.LENGTH_SHORT).show();
				}
			});
			AlertDialog dlg = builder.create();
			dlg.show();
		}
	}

	// Temporary UI until new UI is implemented
	class ShowTagsDialogTask extends AsyncTask<Void, Void, List<AyahTag>> {
		int page, sura, ayah;
		List<Integer> ayahTagIds;
		
		public ShowTagsDialogTask(int page, int sura, int ayah) {
			this.page = page;
			this.sura = sura;
			this.ayah = ayah;
		}
		
		@Override
		protected List<AyahTag> doInBackground(Void... params) {
			BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
			dba.open();
			ayahTagIds = dba.getAyahTagIds(page, sura, ayah);
			List<AyahTag> result = dba.getTagList();
			dba.close();
			return result;
		}
		
		@Override
		protected void onPostExecute(final List<AyahTag> result) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle("Tags (" + QuranInfo.getAyahString(sura, ayah, getActivity()) + ")");
			
			// Map tagIds to corresponding CheckBoxes
			final HashMap<Integer, CheckBox> checkBoxes = new HashMap<Integer, CheckBox>();
			for (AyahTag tag : result) {
				CheckBox cb = new CheckBox(getActivity());
				cb.setText(tag.name);
				cb.setChecked(ayahTagIds.contains(tag.id));
				checkBoxes.put(tag.id, cb);
			}
			
			final TableLayout tl = new TableLayout(getActivity());
			
			// Button for adding new Tag
			final Button btnNewTag = new Button(getActivity());
			btnNewTag.setText("New Tag");
			btnNewTag.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
					builder.setTitle("New Tag");
					LinearLayout ll = new LinearLayout(getActivity());
					ll.setOrientation(LinearLayout.VERTICAL);
					final EditText etName = new EditText(getActivity());
					final EditText etDesc = new EditText(getActivity());
					final EditText etColor = new EditText(getActivity());
					etName.setHint("Name");
					etDesc.setHint("Description");
					etColor.setHint("Color");
					etColor.setInputType(InputType.TYPE_CLASS_NUMBER);
					ll.addView(etName);
					ll.addView(etDesc);
					ll.addView(etColor);
					builder.setView(ll);
					builder.setPositiveButton("Save",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									new SaveTagTask(checkBoxes, tl).execute(etName.getText().toString(),
											etDesc.getText().toString(), etColor.getText().toString());
								}
							});
					builder.setNegativeButton("Cancel",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {/*Do Nothing*/}
							});
					AlertDialog dlg = builder.create();
					dlg.show();
				}
			});
			
			// Populate tag list (TableView layout)
			// First row has button for adding new tag
			TableRow trBtnNewTag = new TableRow(getActivity());
			trBtnNewTag.addView(btnNewTag);
			trBtnNewTag.setGravity(Gravity.RIGHT);
			tl.addView(trBtnNewTag);
			// Remaining rows have tag checkbox + button to delete the tag
			for (Integer key : checkBoxes.keySet()) {
				final Integer tagId = key;
				final TableRow tr = new TableRow(getActivity());
				Button btnDelTag = new Button(getActivity());
				btnDelTag.setText("Delete");
				btnDelTag.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						tl.removeView(tr);
						new DeleteTagTask().execute(tagId);
					}
				});
				tr.addView(checkBoxes.get(key));
				tr.addView(btnDelTag);
				tl.addView(tr);
			}
			
			// Put TableLayout in ScrollView (to allow scrolling if tag list is long)
			final ScrollView sv = new ScrollView(getActivity());
			sv.addView(tl);
			
			// Show dialog
			builder.setView(sv);
			builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					List<Integer> ayahTagIds = new ArrayList<Integer>();
					for (Integer tagId : checkBoxes.keySet()) {
						if (checkBoxes.get(tagId).isChecked())
							ayahTagIds.add(tagId);
					}
					new UpdateAyahTagsTask(sura, ayah, ayahTagIds).execute();
				}
			});
			builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {/*Do Nothing*/}
			});
			AlertDialog dlg = builder.create();
			dlg.show();
		}
	}
	
	class UpdateAyahTagsTask extends AsyncTask<Void, Void, Void> {
		int sura, ayah;
		List<Integer> ayahTagIds;
		
		public UpdateAyahTagsTask(int sura, int ayah, List<Integer> ayahTagIds) {
			this.sura = sura;
			this.ayah = ayah;
			this.ayahTagIds = ayahTagIds;
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
			dba.open();
			dba.updateAyahTags(sura, ayah, ayahTagIds);
			dba.close();
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			Toast.makeText(getActivity(), "Tags Updated", Toast.LENGTH_SHORT).show();
		}
	}
	
	class DeleteTagTask extends AsyncTask<Integer, Void, Void> {
		@Override
		protected Void doInBackground(Integer... params) {
			BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
			dba.open();
			dba.deleteTag(params[0]);
			dba.close();
			return null;
		}
	}
	
	class SaveTagTask extends AsyncTask<String, Void, Integer> {
		WeakReference<HashMap<Integer, CheckBox>> checkBoxesReference;
		WeakReference<TableLayout> tlReference;
		String name;
		
		public SaveTagTask(HashMap<Integer, CheckBox> checkBoxes, TableLayout tl) {
			this.checkBoxesReference = new WeakReference<HashMap<Integer,CheckBox>>(checkBoxes);
			this.tlReference = new WeakReference<TableLayout>(tl);
		}
		
		@Override
		protected Integer doInBackground(String... params) {
			name = params[0];
			Integer color = null;
			if (params[2] != null && !params[2].equals(""))
					color = Integer.valueOf(params[2]);
			BookmarksDBAdapter dba = new BookmarksDBAdapter(getActivity());
			dba.open();
			int tagId = dba.saveTag(name, params[1], color);
			dba.close();
			return tagId;
		}
		
		@Override
		protected void onPostExecute(final Integer result) {
			if (checkBoxesReference == null || tlReference == null)
				return;
			
			final HashMap<Integer, CheckBox> checkBoxes = checkBoxesReference.get();
			final TableLayout tl = tlReference.get();
			if (checkBoxes == null || tl == null || checkBoxes.containsKey(result))
				return;
			
			final TableRow tr = new TableRow(getActivity());
			Button delBtn = new Button(getActivity());
			delBtn.setText("Delete");
			delBtn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					tl.removeView(tr);
					new DeleteTagTask().execute(result);
				}
			});
			CheckBox cb = new CheckBox(getActivity());
			cb.setText(name);
			cb.setChecked(true);
			checkBoxes.put(result, cb);
			tr.addView(cb);
			tr.addView(delBtn);
			tl.addView(tr);
		}
	}

	class ShowNotesDialogTask extends AsyncTask<Void, Void, String> {
		int page, sura, ayah;
		
		public ShowNotesDialogTask(int page, int sura, int ayah) {
			this.page = page;
			this.sura = sura;
			this.ayah = ayah;
		}
		
		@Override
		protected String doInBackground(Void... params) {
         String result = "";
         Activity activity = getActivity();
         if (activity != null){
            BookmarksDBAdapter dba = new BookmarksDBAdapter(activity);
            dba.open();
			   result = dba.getAyahNotes(sura, ayah);
			   dba.close();
			   if (result == null)
				   result = "";
         }
			return result;
		}

		@Override
		protected void onPostExecute(final String result) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle("Notes (" + QuranInfo.getAyahString(sura, ayah, getActivity()) + ")");

         Activity activity = getActivity();
         if (activity != null){
            final EditText input = new EditText(activity);
            input.setText(result);
            builder.setView(input);
            builder.setPositiveButton("Save",
                  new DialogInterface.OnClickListener() {
                     public void onClick(DialogInterface dialog, int which) {
                        String newNotes = input.getText().toString();
                        if (result != null && !result.equals(newNotes))
                           new SaveNotesTask(page, sura, ayah).execute(newNotes);
                     }
                  });
            builder.setNegativeButton("Cancel",
                  new DialogInterface.OnClickListener() {
                     public void onClick(DialogInterface dialog, int which) {/*Do Nothing*/}
                  });
            AlertDialog dlg = builder.create();
            dlg.show();
         }
		}
	}

	class SaveNotesTask extends AsyncTask<String, Void, Void> {
		int page, sura, ayah;
		
		public SaveNotesTask(int page, int sura, int ayah) {
			this.page = page;
			this.sura = sura;
			this.ayah = ayah;
		}
		
		@Override
		protected Void doInBackground(String... params) {
         Activity activity = getActivity();
         if (activity != null){
            BookmarksDBAdapter dba = new BookmarksDBAdapter(activity);
            dba.open();
            dba.saveAyahNotes(page, sura, ayah, params[0]);
            dba.close();
         }
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
         Activity activity = getActivity();
         if (activity != null){
            Toast.makeText(activity, "Notes Saved",
                    Toast.LENGTH_SHORT).show();
         }
		}
	}

	class ToggleAyahBookmarkTask extends AsyncTask<Integer, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Integer... params) {
         Activity activity = getActivity();
         Boolean result = null;
         if (activity != null){
            BookmarksDBAdapter dba = new BookmarksDBAdapter(activity);			dba.open();
			   result = dba.toggleAyahBookmark(params[0], params[1], params[2]);
			   dba.close();
         }
			return result;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			// Temp toast for debugging
         Activity activity = getActivity();
         if (result != null && activity != null){
			   Toast.makeText(activity, result ? "Ayah Bookmarked" :
                    "Ayah Unbookmarked", Toast.LENGTH_SHORT).show();
         }
		}
	}

	class TogglePageBookmarkTask extends AsyncTask<Integer, Void, Void> {
		@Override
		protected Void doInBackground(Integer... params) {
         Activity activity = getActivity();
         if (activity != null){
            BookmarksDBAdapter dba = new BookmarksDBAdapter(activity);
            dba.open();
            mIsBookmarked = dba.togglePageBookmark(params[0]);
            dba.close();
         }
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
         SherlockFragmentActivity sherlockActivity = getSherlockActivity();
         if (sherlockActivity != null){
            sherlockActivity.invalidateOptionsMenu();
         }
		}
	}
	
	class IsPageBookmarkedTask extends AsyncTask<Integer, Void, Void> {
	   @Override
	   protected Void doInBackground(Integer... params) {
         Activity activity = getActivity();
         if (activity != null){
            BookmarksDBAdapter dba = new BookmarksDBAdapter(activity);
            dba.open();
            mIsBookmarked = dba.isPageBookmarked(params[0]);
            dba.close();
         }
	      return null;
	   }

	   @Override
	   protected void onPostExecute(Void result) {
         SherlockFragmentActivity sherlockActivity = getSherlockActivity();
         if (sherlockActivity != null){
	         sherlockActivity.invalidateOptionsMenu();
         }
	   }
	}
	
}
