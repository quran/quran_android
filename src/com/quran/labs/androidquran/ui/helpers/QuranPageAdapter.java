package com.quran.labs.androidquran.ui.helpers;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.ViewGroup;

import com.quran.labs.androidquran.ui.fragment.QuranPageFragment;

public class QuranPageAdapter extends FragmentStatePagerAdapter {
   private static String TAG = "QuranPageAdapter";

	public QuranPageAdapter(FragmentManager fm){
		super(fm);
	}

	@Override
	public int getCount(){ return 604; }

	@Override
	public Fragment getItem(int position){
	   android.util.Log.d(TAG, "getting page: " + (604-position));
	   return QuranPageFragment.newInstance(604-position);
	}
	
	@Override
	public void destroyItem(ViewGroup container, int position, Object object){
	   final QuranPageFragment fragment = (QuranPageFragment)object;
	   fragment.cleanup();
	   super.destroyItem(container, position, object);
	}
}
