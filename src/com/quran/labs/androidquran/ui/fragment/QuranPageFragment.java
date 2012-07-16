package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;
import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

public class QuranPageFragment extends SherlockFragment {
   private static final String TAG = "QuranPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private int mPageNumber;
   private HighlightingImageView mImageView;
   private ScrollView mScrollView;
   private PaintDrawable mLeftGradient, mRightGradient = null;

   private AlertDialog mTranslationDialog = null;

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
      mPageNumber = getArguments() != null?
              getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
      int width = getActivity().getWindowManager()
            .getDefaultDisplay().getWidth();
      mLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      mRightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      setHasOptionsMenu(true);
   }

   @Override
   public View onCreateView(LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState){
      final View view = inflater.inflate(R.layout.quran_page_layout,
              container, false);
      view.setBackgroundDrawable((mPageNumber % 2 == 0?
              mLeftGradient : mRightGradient));

      int lineImageId = R.drawable.dark_line;
      int leftBorderImageId = R.drawable.border_left;
      int rightBorderImageId = R.drawable.border_right;
      SharedPreferences prefs = PreferenceManager
              .getDefaultSharedPreferences(getActivity());
      if (prefs.getBoolean(Constants.PREF_NIGHT_MODE, false)){
         leftBorderImageId = R.drawable.night_left_border;
         rightBorderImageId = R.drawable.night_right_border;
         lineImageId = R.drawable.light_line;
      }

      ImageView leftBorder = (ImageView)view.findViewById(R.id.left_border);
      ImageView rightBorder = (ImageView)view.findViewById(R.id.right_border);
      if (mPageNumber % 2 == 0){
         rightBorder.setVisibility(View.GONE);
         leftBorder.setBackgroundResource(leftBorderImageId);
      }
      else {
         rightBorder.setVisibility(View.VISIBLE);
         rightBorder.setBackgroundResource(rightBorderImageId);
         leftBorder.setBackgroundResource(lineImageId);
      }

      mImageView = (HighlightingImageView)view.findViewById(R.id.page_image);
      mScrollView = (ScrollView)view.findViewById(R.id.page_scroller);
      
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
   public void onPause(){
      if (mTranslationDialog != null){
         mTranslationDialog.dismiss();
         mTranslationDialog = null;
      }
      super.onPause();
   }

   public void cleanup(){
      android.util.Log.d(TAG, "cleaning up page " + mPageNumber);
      mImageView.setImageDrawable(null);
      mImageView = null;
      if (mTranslationDialog != null){
         mTranslationDialog.dismiss();
         mTranslationDialog = null;
      }
   }

   public void highlightAyah(int sura, int ayah){
      mImageView.highlightAyah(sura, ayah);
      if (mScrollView != null){
         AyahBounds yBounds = mImageView.getYBoundsForCurrentHighlight();
         if (yBounds != null){
            int screenHeight = QuranScreenInfo.getInstance().getHeight();
            int y = yBounds.getMinY() - (int)(0.05 * screenHeight);
            mScrollView.smoothScrollTo(mScrollView.getScrollX(), y);
         }
      }
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
         unhighlightAyah();
         return true;
      }

      @Override
      public void onLongPress(MotionEvent event) {
         QuranAyah result = getAyahFromCoordinates(event.getX(), event.getY());
         if (result != null) {
            mImageView.highlightAyah(result.getSura(), result.getAyah());
            mImageView.invalidate();
            mImageView.performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS);
            
            // TODO Temporary UI until new UI is implemented
            new ShowAyahMenuTask(mPageNumber, result.getSura(),
                    result.getAyah()).execute();
         }
      }

      private QuranAyah getAyahFromCoordinates(float x, float y) {
         float[] pageXY = mImageView.getPageXY(x, y);
         QuranAyah result = null;
         if (pageXY != null) {
            String filename = QuranFileUtils.getAyaPositionFileName();
            AyahInfoDatabaseHandler handler =
                    new AyahInfoDatabaseHandler(filename);
            try {
               result = handler.getVerseAtPoint(mPageNumber,
                       pageXY[0], pageXY[1]);
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
         final Activity activity = getActivity();
         if (activity == null){ return; }

         int[] optionIds = {
                 result? R.string.unbookmark_ayah : R.string.bookmark_ayah,
                 R.string.translation_ayah, R.string.share_ayah,
                 R.string.play_from_here };
         CharSequence[] options = new CharSequence[optionIds.length];
         for (int i=0; i<optionIds.length; i++){
            options[i] = activity.getString(optionIds[i]);
         }

			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
			builder.setTitle(QuranInfo.getAyahString(sura, ayah, activity));
			builder.setItems(options, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int selection) {
					if (selection == 0){
						new ToggleAyahBookmarkTask().execute(mPageNumber,
                          sura, ayah);
               }
					else if (selection == 1){
						new ShowTafsirTask(sura, ayah).execute();
               }
					else if (selection == 2){
						new ShareAyahTask(sura, ayah).execute();
               }
               else if (selection == 3){
                  if (activity instanceof PagerActivity){
                     PagerActivity pagerActivity = (PagerActivity)activity;
                     pagerActivity.playFromAyah(mPageNumber, sura, ayah);
                  }
               }
				}
			});
			AlertDialog dlg = builder.create();
			dlg.show();
		}
	}

	class ToggleAyahBookmarkTask extends AsyncTask<Integer, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Integer... params) {
         Activity activity = getActivity();
         Boolean result = null;
         if (activity != null){
            BookmarksDBAdapter dba = new BookmarksDBAdapter(activity);
            dba.open();
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
            int strId = result? R.string.bookmarked_ayah :
                    R.string.unbookmarked_ayah;
			   Toast.makeText(activity, activity.getString(strId),
                    Toast.LENGTH_SHORT).show();
         }
		}
	}
	
	class ShareAyahTask extends AsyncTask<Void, Void, String> {
		private int sura, ayah;
		
		public ShareAyahTask(int sura, int ayah) {
			this.sura = sura;
			this.ayah = ayah;
		}
		
		@Override
		protected String doInBackground(Void... params) {
			DatabaseHandler ayahHandler =
                 new DatabaseHandler(QuranDataProvider.QURAN_ARABIC_DATABASE);
			Cursor cursor = ayahHandler.getVerses(sura, ayah, sura, ayah,
                 DatabaseHandler.ARABIC_TEXT_TABLE);
			String text = null;
			if (cursor.moveToFirst()) {
				text = cursor.getString(2);
			}
			cursor.close();
			ayahHandler.closeDatabase();
			return text;
		}
		
		@Override
		protected void onPostExecute(String ayah) {
         Activity activity = getActivity();
			if (ayah != null && activity != null) {
				ayah += activity.getString(R.string.via_string);
				final Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, ayah);
				startActivity(Intent.createChooser(intent,
                    activity.getString(R.string.share)));
			}
		}
	}
	
	class ShowTafsirTask extends AsyncTask<Void, Void, String> {
		private int sura, ayah;
		
		public ShowTafsirTask(int sura, int ayah) {
			this.sura = sura;
			this.ayah = ayah;
		}

		@Override
		protected String doInBackground(Void... params) {
         Activity activity = getActivity();
			String db = PreferenceManager.getDefaultSharedPreferences(activity)
				.getString(Constants.PREF_ACTIVE_TRANSLATION, null);
			if (db != null) {
				DatabaseHandler tafsirHandler = new DatabaseHandler(db);
				Cursor cursor = tafsirHandler.getVerse(sura, ayah);
				if (cursor.moveToFirst()) {
					String text = cursor.getString(2);
					cursor.close();
					tafsirHandler.closeDatabase();
					return text;
				}
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(String text) {
			Activity activity = getActivity();
			if (activity != null) {
				AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				   .setMessage(text)
				   .setCancelable(true)
				   .setPositiveButton(getString(R.string.dialog_ok),
                       new DialogInterface.OnClickListener() {
					   @Override
					   public void onClick(DialogInterface dialog, int which) {
						   dialog.dismiss();
                     mTranslationDialog = null;
					   }
				   }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                  @Override
                  public void onCancel(DialogInterface dialogInterface) {
                     mTranslationDialog = null;
                  }
               });
            mTranslationDialog = builder.create();
            mTranslationDialog.show();
			}
		}
	}
}
