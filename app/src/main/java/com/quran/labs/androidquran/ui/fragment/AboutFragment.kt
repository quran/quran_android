package com.quran.labs.androidquran.ui.fragment

import com.quran.labs.androidquran.R

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.BuildConfig

class AboutFragment : PreferenceFragmentCompat() {

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    addPreferencesFromResource(R.xml.about)

    val flavor = BuildConfig.FLAVOR + "Images"
    val parent = findPreference("aboutDataSources") as PreferenceCategory
    sImagePrefKeys.filter { it != flavor }.map {
      parent.removePreference(findPreference(it))
    }
  }

  // TODO: remove this function when issue https://issuetracker.google.com/issues/111662669 solved/released
  override fun onCreateAdapter(preferenceScreen: PreferenceScreen?): RecyclerView.Adapter<*> {
    return object : PreferenceGroupAdapter(preferenceScreen) {
      @SuppressLint("RestrictedApi")
      override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val preference = getItem(position)
        if (preference is PreferenceCategory) {
          setZeroPaddingToLayoutChildren(holder.itemView)
        }
      }
    }
  }

  private fun setZeroPaddingToLayoutChildren(view: View) {
    if (view !is ViewGroup) return

    for (i in 0 until view.childCount) {
      setZeroPaddingToLayoutChildren(view.getChildAt(i))
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        view.setPaddingRelative(0, view.paddingTop, view.paddingEnd, view.paddingBottom)
      } else {
        view.setPadding(0, view.paddingTop, view.paddingRight, view.paddingBottom)
      }
    }
  }

  companion object {
    private val sImagePrefKeys = arrayOf("madaniImages", "naskhImages", "qaloonImages")
  }
}
