package com.quran.labs.androidquran.ui.fragment;

import java.lang.ref.WeakReference;

import android.content.Context;
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
import android.widget.ImageView;
import android.widget.Toast;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahItem;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
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
          // FIXME Context usage correct?? -AF
          new PageBookmarkTask(getActivity()).execute(mPageNumber);
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
            // FIXME Context usage correct?? -AF
            new AyahBookmarkTask(getActivity()).execute(mPageNumber, result.getSoura(), result.getAyah());
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
   
	class AyahBookmarkTask extends AsyncTask<Integer, Void, Boolean> {
		private final WeakReference<Context> contextReference;
		
		private int page = 0;
		private int sura = 0;
		private int ayah = 0;

		public AyahBookmarkTask(Context context) {
			// FIXME Is this correct?? How to pass context correctly? -AF
	        // use a WeakReference to ensure the Context can be garbage collected
			contextReference = new WeakReference<Context>(context);
		}
		
		@Override
		protected Boolean doInBackground(Integer... params) {
			page = params[0];
			sura = params[1];
			ayah = params[2];

			BookmarksDatabaseHandler db = new BookmarksDatabaseHandler(contextReference.get());
			db.open();
			boolean result = db.toggleAyahBookmark(page, sura, ayah);
			db.close();
			return result;
		}

		@Override
		protected void onPostExecute(Boolean result) {
            Toast.makeText(getActivity(), result ? "Ayah Bookmarked" : "Ayah Unbookmarked", Toast.LENGTH_SHORT).show();
		}
	}
   
	class PageBookmarkTask extends AsyncTask<Integer, Void, Boolean> {
		private final WeakReference<Context> contextReference;
		
		private int page = 0;
		
		public PageBookmarkTask(Context context) {
			// FIXME Is this correct?? How to pass context correctly? -AF
			// use a WeakReference to ensure the Context can be garbage collected
			contextReference = new WeakReference<Context>(context);
		}
		
		@Override
		protected Boolean doInBackground(Integer... params) {
			page = params[0];
			BookmarksDatabaseHandler db = new BookmarksDatabaseHandler(contextReference.get());
			db.open();
			boolean result = db.togglePageBookmark(page);
			db.close();
			return result;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			Toast.makeText(getActivity(), result ? "Page Bookmarked" : "Page Unbookmarked", Toast.LENGTH_SHORT).show();
		}
	}
	
}
