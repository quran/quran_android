package com.quran.labs.androidquran.ui.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

public class QuranPageFragment extends Fragment {
   private static final String TAG = "QuranPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private int mPageNumber;
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
   
   public void cleanup(){
      android.util.Log.d(TAG, "cleaning up page " + mPageNumber);
      mImageView.setImageDrawable(null);
      mImageView = null;
   }

   private class PageGestureDetector extends SimpleOnGestureListener {
      @Override
      public boolean onSingleTapConfirmed(MotionEvent event) {
          new TogglePageBookmarkTask().execute(mPageNumber);
          return true;
      }

      @Override
      public boolean onDoubleTap(MotionEvent event) {
         AyahItem result = getAyahFromCoordinates(event.getX(), event.getY());
         if (result != null) {
            mImageView.toggleHighlight(result.getSoura(), result.getAyah());
            mImageView.invalidate();
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
            
            // TODO Temporary UI until new UI is implemented
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
			BookmarksDatabaseHandler db = new BookmarksDatabaseHandler(getActivity());
			db.open();
			boolean result = db.isAyahBookmarked(sura, ayah);
			db.close();
			return result;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			final CharSequence[] options = { result ? "Unbookmark" : "Bookmark",
					"Notes", "Tags", "Tafsir", "Ayah ID" };
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(QuranInfo.getAyahString(sura, ayah));
			builder.setItems(options, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int selection) {
					if (selection == 0)
						new ToggleAyahBookmarkTask().execute(mPageNumber, sura, ayah);
					else if (selection == 1)
						new ShowNotesDialogTask(page, sura, ayah).execute();
					else if (selection == 4)
						Toast.makeText(getActivity(), "Ayah "+QuranInfo.getAyahId(
								sura, ayah), Toast.LENGTH_SHORT).show();
				}
			});
			AlertDialog dlg = builder.create();
			dlg.show();
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
			BookmarksDatabaseHandler db = new BookmarksDatabaseHandler(getActivity());
			db.open();
			String result = db.getAyahNotes(sura, ayah);
			db.close();
			return result;
		}

		@Override
		protected void onPostExecute(final String result) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle("Notes (" + QuranInfo.getAyahString(sura, ayah) + ")");
			
			final EditText input = new EditText(getActivity());
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

	class SaveNotesTask extends AsyncTask<String, Void, Void> {
		int page, sura, ayah;
		
		public SaveNotesTask(int page, int sura, int ayah) {
			this.page = page;
			this.sura = sura;
			this.ayah = ayah;
		}
		
		@Override
		protected Void doInBackground(String... params) {
			BookmarksDatabaseHandler db = new BookmarksDatabaseHandler(getActivity());
			db.open();
			db.saveAyahNotes(page, sura, ayah, params[0]);
			db.close();
			return null;
		}
		
		@Override
		protected void onPostExecute(Void result) {
			Toast.makeText(getActivity(), "Notes Saved", Toast.LENGTH_SHORT).show();
		}
	}

	class ToggleAyahBookmarkTask extends AsyncTask<Integer, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Integer... params) {
			BookmarksDatabaseHandler db = new BookmarksDatabaseHandler(getActivity());
			db.open();
			boolean result = db.toggleAyahBookmark(params[0], params[1], params[2]);
			db.close();
			return result;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			// Temp toast for debugging
			Toast.makeText(getActivity(), result ? "Ayah Bookmarked" : "Ayah Unbookmarked", Toast.LENGTH_SHORT).show();
		}
	}

	class TogglePageBookmarkTask extends AsyncTask<Integer, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Integer... params) {
			BookmarksDatabaseHandler db = new BookmarksDatabaseHandler(getActivity());
			db.open();
			boolean result = db.togglePageBookmark(params[0]);
			db.close();
			return result;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			// Temp toast for debugging
			Toast.makeText(getActivity(), result ? "Page Bookmarked" : "Page Unbookmarked", Toast.LENGTH_SHORT).show();
		}
	}
	
}
