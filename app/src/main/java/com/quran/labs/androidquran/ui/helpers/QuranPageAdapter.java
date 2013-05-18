package com.quran.labs.androidquran.ui.helpers;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.ViewGroup;
import com.quran.labs.androidquran.ui.fragment.QuranPageFragment;
import com.quran.labs.androidquran.ui.fragment.TabletFragment;
import com.quran.labs.androidquran.ui.fragment.TranslationFragment;

public class QuranPageAdapter extends FragmentStatePagerAdapter {
   private static String TAG = "QuranPageAdapter";

   private boolean mIsShowingTranslation = false;
   private boolean mIsDualPages = false;

	public QuranPageAdapter(FragmentManager fm, boolean dualPages){
		super(fm);
      mIsDualPages = dualPages;
	}

   public QuranPageAdapter(FragmentManager fm, boolean dualPages,
                           boolean isShowingTranslation){
      super(fm);
      mIsDualPages = dualPages;
      mIsShowingTranslation = isShowingTranslation;
   }

   public void setTranslationMode(){
      if (!mIsShowingTranslation){
         mIsShowingTranslation = true;
         notifyDataSetChanged();
      }
   }

   public void setQuranMode(){
      if (mIsShowingTranslation){
         mIsShowingTranslation = false;
         notifyDataSetChanged();
      }
   }

   @Override
   public int getItemPosition(Object object){
      /* when the ViewPager gets a notifyDataSetChanged (or invalidated),
       * it goes through its set of saved views and runs this method on
       * each one to figure out whether or not it should remove the view
       * or not.  the default implementation returns POSITION_UNCHANGED,
       * which means that "this page is as is."
       *
       * as noted in http://stackoverflow.com/questions/7263291 in one
       * of the answers, if you're just updating your view (changing a
       * field's value, etc), this is highly inefficient (because you
       * recreate the view for nothing).
       *
       * in our case, however, this is the right thing to do since we
       * change the fragment completely when we notifyDataSetChanged.
       */
      return POSITION_NONE;
   }

	@Override
	public int getCount(){ return mIsDualPages ? 302 : 604; }

	@Override
	public Fragment getItem(int position){
      int count = getCount();
	   android.util.Log.d(TAG, "getting page: " + (count-position));
      if (mIsDualPages){
         return TabletFragment.newInstance((count-position)*2,
                mIsShowingTranslation? TabletFragment.Mode.TRANSLATION :
                        TabletFragment.Mode.ARABIC);
      }

      if (mIsShowingTranslation){
         return TranslationFragment.newInstance(count-position);
      }
	   else { return QuranPageFragment.newInstance(count-position); }
	}
	
	@Override
	public void destroyItem(ViewGroup container, int position, Object object){
      Fragment f = (Fragment)object;
      if (f instanceof QuranPageFragment){
         ((QuranPageFragment)f).cleanup();
      }
      else if (f instanceof TabletFragment){
         ((TabletFragment)f).cleanup();
      }
	   super.destroyItem(container, position, object);
	}
}
