package com.quran.labs.androidquran.ui.helpers;

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import timber.log.Timber;

/**
 * Implementation of {@link PagerAdapter} that uses a {@link Fragment} to manage each page. This
 * class also handles saving and restoring of fragment's state.
 *
 * <p>This version of the pager is more useful when there are a large number of pages, working more
 * like a list view.  When pages are not visible to the user, their entire fragment may be
 * destroyed, only keeping the saved state of that fragment.  This allows the pager to hold on to
 * much less memory associated with each visited page as compared to {@link FragmentPagerAdapter} at
 * the cost of potentially more overhead when switching between pages.
 *
 * Modified for Quran
 * Modifications List (for ease of merging with upstream later on):
 * - added getFragmentIfExists() to return a fragment without recreating it
 * - keep track of a mode, and remove all fragments when said mode changes
 */
public abstract class FragmentStatePagerAdapter extends PagerAdapter {
  private static final boolean DEBUG = false;

  private final FragmentManager mFragmentManager;
  private FragmentTransaction mCurTransaction = null;

  private ArrayList<Fragment.SavedState> mSavedState = new ArrayList<>();
  private ArrayList<Fragment> mFragments = new ArrayList<>();
  private Fragment mCurrentPrimaryItem = null;
  private String mode;

  public FragmentStatePagerAdapter(FragmentManager fm, String mode) {
    mFragmentManager = fm;
    this.mode = mode;
  }

  /**
   * Return the Fragment associated with a specified position.
   */
  public abstract Fragment getItem(int position);

  @Override
  public void startUpdate(ViewGroup container) {
    if (container.getId() == View.NO_ID) {
      throw new IllegalStateException("ViewPager with adapter " + this
          + " requires a view id");
    }
  }

  /**
   * gets the fragment at a particular position if it exists this was added specifically for Quran
   *
   * @param position the position of the fragment
   * @return the fragment or null
   */
  public Fragment getFragmentIfExists(int position) {
    if (mFragments.size() > position) {
      Fragment f = mFragments.get(position);
      if (f != null) {
        return f;
      }
    }
    return null;
  }

  @SuppressLint("CommitTransaction")
  @Override
  public Object instantiateItem(ViewGroup container, int position) {
    // If we already have this item instantiated, there is nothing
    // to do.  This can happen when we are restoring the entire pager
    // from its saved state, where the fragment manager has already
    // taken care of restoring the fragments we previously had instantiated.
    if (mFragments.size() > position) {
      Fragment f = mFragments.get(position);
      if (f != null) {
        return f;
      }
    }

    if (mCurTransaction == null) {
      mCurTransaction = mFragmentManager.beginTransaction();
    }

    Fragment fragment = getItem(position);
    if (DEBUG) Timber.v("Adding item #%d: f=%s", position, fragment);
    if (mSavedState.size() > position) {
      Fragment.SavedState fss = mSavedState.get(position);
      if (fss != null) {
        fragment.setInitialSavedState(fss);
      }
    }
    while (mFragments.size() <= position) {
      mFragments.add(null);
    }
    fragment.setMenuVisibility(false);
    fragment.setUserVisibleHint(false);
    mFragments.set(position, fragment);
    mCurTransaction.add(container.getId(), fragment);

    return fragment;
  }

  @SuppressLint("CommitTransaction")
  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    Fragment fragment = (Fragment) object;

    if (mCurTransaction == null) {
      mCurTransaction = mFragmentManager.beginTransaction();
    }
    if (DEBUG) Timber.v("Removing item #%d: f=%s v=%s",
        position, object, ((Fragment) object).getView());
    while (mSavedState.size() <= position) {
      mSavedState.add(null);
    }

    mSavedState.set(position, fragment.isAdded()
        ? mFragmentManager.saveFragmentInstanceState(fragment) : null);
    mFragments.set(position, null);

    mCurTransaction.remove(fragment);
  }

  @Override
  public void setPrimaryItem(ViewGroup container, int position, Object object) {
    Fragment fragment = (Fragment) object;
    if (fragment != mCurrentPrimaryItem) {
      if (mCurrentPrimaryItem != null) {
        mCurrentPrimaryItem.setMenuVisibility(false);
        mCurrentPrimaryItem.setUserVisibleHint(false);
      }
      if (fragment != null) {
        fragment.setMenuVisibility(true);
        fragment.setUserVisibleHint(true);
      }
      mCurrentPrimaryItem = fragment;
    }
  }

  @Override
  public void finishUpdate(ViewGroup container) {
    if (mCurTransaction != null) {
      mCurTransaction.commitNowAllowingStateLoss();
      mCurTransaction = null;
    }
  }

  @Override
  public boolean isViewFromObject(View view, Object object) {
    return ((Fragment) object).getView() == view;
  }

  @Override
  public Parcelable saveState() {
    Bundle state = new Bundle();
    if (mSavedState.size() > 0) {
      Fragment.SavedState[] fss = new Fragment.SavedState[mSavedState.size()];
      mSavedState.toArray(fss);
      state.putParcelableArray("states", fss);
    }
    for (int i = 0; i < mFragments.size(); i++) {
      Fragment f = mFragments.get(i);
      if (f != null && f.isAdded()) {
        String key = "f" + i;
        mFragmentManager.putFragment(state, key, f);
      }
    }
    state.putString("mode", this.mode);
    return state;
  }

  @Override
  public void restoreState(Parcelable state, ClassLoader loader) {
    if (state != null) {
      Bundle bundle = (Bundle) state;
      bundle.setClassLoader(loader);
      mSavedState.clear();
      mFragments.clear();

      String lastMode = bundle.getString("mode", "");
      if (!mode.equals(lastMode)) {
        cleanupOldFragments(bundle);
        return;
      }

      Parcelable[] fss = bundle.getParcelableArray("states");
      if (fss != null) {
        for (Parcelable fs : fss) {
          mSavedState.add((Fragment.SavedState) fs);
        }
      }
      Iterable<String> keys = bundle.keySet();
      for (String key : keys) {
        if (key.startsWith("f")) {
          int index = Integer.parseInt(key.substring(1));
          Fragment f = mFragmentManager.getFragment(bundle, key);
          if (f != null) {
            while (mFragments.size() <= index) {
              mFragments.add(null);
            }
            f.setMenuVisibility(false);
            mFragments.set(index, f);
          } else {
            Timber.w("Bad fragment at key %s", key);
          }
        }
      }
    }
  }

  public void cleanupFragment(Fragment fragment) {
    // no op, present for overriding
  }

  private void cleanupOldFragments(Bundle bundle) {
    // remove suppress once lint rule is updated to treat commitNowAllowingStateLoss as a commit
    @SuppressLint("CommitTransaction")
    FragmentTransaction transaction = mFragmentManager.beginTransaction();
    Iterable<String> keys = bundle.keySet();
    for (String key : keys) {
      if (key.startsWith("f")) {
        Fragment f = mFragmentManager.getFragment(bundle, key);
        if (f != null) {
          cleanupFragment(f);
          transaction.remove(f);
        }
      }
    }
    transaction.commitNowAllowingStateLoss();
  }
}