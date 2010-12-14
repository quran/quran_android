package com.quran.labs.androidquran;

import java.lang.ref.SoftReference;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.quran.labs.androidquran.common.GestureQuranActivity;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.GalleryFriendlyScrollView;

public class ExpViewActivity extends GestureQuranActivity {
	private boolean inReadingMode = false;
	// private ImageView imageView = null;
	private Gallery gallery = null;
	private TextView titleText = null;
	private SeekBar seekBar = null;
	
	// private int page = 1;
	private int width = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.quran_exp);
		// imageView = (ImageView)findViewById(R.id.pageview);
		gallery = (Gallery) findViewById(R.id.gallery);
	    gallery.setAdapter(new QuranGalleryImageAdapter(this));
	    
        titleText = (TextView)findViewById(R.id.pagetitle);
        seekBar = (SeekBar)findViewById(R.id.suraSeek);
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser)
					titleText.setText(QuranInfo.getPageTitle(progress+1));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (seekBar.getProgress() !=
					gallery.getSelectedItemPosition()){
					renderPage(seekBar.getProgress());
				}
			}
		});
        
		gestureDetector = new GestureDetector(new QuranGestureDetector());
		
		WindowManager manager = getWindowManager();
		Display display = manager.getDefaultDisplay();
		width = display.getWidth();
		
		int page = loadState(savedInstanceState);
		renderPage(page - 1);
		toggleMode();
	}
	
	public class QuranGalleryImageAdapter extends BaseAdapter {
		private Context context;
		private LayoutInflater mInflater;
		private Map<String, SoftReference<Bitmap>> cache = 
            new HashMap<String, SoftReference<Bitmap>>();
		
	    public QuranGalleryImageAdapter(Context context) {
	    	this.context = context;
			mInflater = LayoutInflater.from(this.context);
	    }

	    public int getCount() {
	    	return 604;
	    }

	    public Object getItem(int position) {
	        return position;
	    }

	    public long getItemId(int position) {
	        return position;
	    }

	    public View getView(int position, View convertView, ViewGroup parent) {
	    	PageHolder holder;
	    	if (convertView == null){
	    		convertView = mInflater.inflate(R.layout.quran_page, null);
				holder = new PageHolder();
				holder.page = (ImageView)convertView.findViewById(R.id.pageImageView);
				holder.scroll = (GalleryFriendlyScrollView)convertView.findViewById(R.id.pageScrollView);
				convertView.setTag(holder);
	    	}
	    	else {
	    		holder = (PageHolder)convertView.getTag();
	    	}
	    	
	        Bitmap bitmap = null;
	        int page = position + 1;
	        if (cache.containsKey("page_" + page)){
	        	SoftReference<Bitmap> bitmapRef = cache.get("page_" + page);
	        	bitmap = bitmapRef.get();
	        	Log.d("exp_v", "reading image for page " + page + " from cache!");
	        }
	        
	        if (bitmap == null){
	        	String filename = getPageFileName(page);
	        	bitmap = QuranUtils.getImageFromSD(filename);
	        	cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
	        }
			holder.page.setImageBitmap(bitmap);
			
			if (!inReadingMode)
				updatePageInfo(position);
	    	return convertView;
	    }
	}
	
	static class PageHolder {
		ImageView page;
		ScrollView scroll;
	}
	
	private int loadState(Bundle savedInstanceState){
		int page = savedInstanceState != null ? savedInstanceState.getInt("page") : ApplicationConstants.PAGES_FIRST;
		if (page == ApplicationConstants.PAGES_FIRST){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") : ApplicationConstants.PAGES_FIRST;
		}
		
		return page;
	}
	
	/*
	private String getPageFileName() {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(page) + ".png";
	}
	*/
	
	private String getPageFileName(int p) {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(p) + ".png";
	}
	
	private void renderPage(int position){
		/*
		String filename = getPageFileName();
		Bitmap bitmap = QuranUtils.getImageFromSD(filename);
		// TODO: handle null case (download page)
		imageView.setImageBitmap(bitmap);
		*/
		gallery.setSelection(position, true);
	}
	
	private void updatePageInfo(int position){
		titleText.setText(QuranInfo.getPageTitle(position + 1));
		seekBar.setProgress(position);
	}
	
	@Override
	public boolean handleSingleTap(MotionEvent e){
		Log.d("exp_v", "in handle single tap");
		int sliceWidth = (int)(0.2 * width);
		if (e.getX() < sliceWidth)
			goToNextPage();
		else if (e.getX() > (width - sliceWidth))
			goToPreviousPage();
		else toggleMode();
		
		return true;
	}
	
	public void toggleMode(){
		Log.d("exp_v", "in toggle mode");
		if (inReadingMode){
	        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        
	        seekBar.setVisibility(TextView.VISIBLE);
	        titleText.setVisibility(TextView.VISIBLE);
		}
		else {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        
	        seekBar.setVisibility(TextView.INVISIBLE);
	        titleText.setVisibility(TextView.INVISIBLE);
		}
		
		inReadingMode = !inReadingMode;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			goToNextPage();
		}
		else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
			goToPreviousPage();
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public void goToNextPage() {
		int position = gallery.getSelectedItemPosition();
		if (position < 603)
			renderPage(position + 1);
	}

	@Override
	public void goToPreviousPage() {
		int position = gallery.getSelectedItemPosition();
		if (position > 0)
			renderPage(position - 1);
	}

}
