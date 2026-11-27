package com.quran.labs.androidquran.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.QuranAdvancedPreferenceActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.audio.model.QariItem
import com.quran.labs.androidquran.common.audio.repository.CurrentQariManager
import com.quran.labs.androidquran.common.audio.util.QariUtil
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.pageselect.PageSelectActivity
import com.quran.labs.androidquran.ui.TranslationManagerActivity
import com.quran.labs.androidquran.util.OrientationLockUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.util.ThemeUtil
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.feature.downloadmanager.AudioManagerActivity
import com.quran.mobile.feature.pagetheme.PageThemeSheetWrapper
import com.quran.mobile.feature.pagetheme.pageThemeSummary
import dev.zacsweers.metro.Inject

class QuranSettingsFragment : PreferenceFragmentCompat() {

  @Inject
  lateinit var pageTypes: Map<@JvmSuppressWildcards String, @JvmSuppressWildcards PageProvider>

  @Inject
  lateinit var extraPreferences: Set<@JvmSuppressWildcards ExtraPreferencesProvider>

  @Inject
  lateinit var currentQariManager: CurrentQariManager

  @Inject
  lateinit var qariUtil: QariUtil

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    addPreferencesFromResource(R.xml.quran_preferences)

    val appContext = requireContext().applicationContext

    // field injection
    (appContext as QuranApplication).applicationComponent.inject(this)

    if (!OrientationLockUtils.isOrientationLockSupported(resources.configuration)) {
      hideUnsupportedOrientationPreferences()
    }

    // handle Arabic names preference
    val arabicPref: Preference? = findPreference(ARABIC_KEY)
    if (arabicPref is CheckBoxPreference) {
      arabicPref.isChecked = isCurrentlyArabic()
    }

    arabicPref?.setOnPreferenceClickListener {
      val localeList = if (isCurrentlyArabic()) {
        val locales = LocaleListCompat.getDefault()
        val tags = locales.toLanguageTags().split(",")
          .filter { it != "ar" && !it.startsWith("ar-") }
        val tagString = tags.joinToString(",").ifEmpty { "en" }
        LocaleListCompat.forLanguageTags(tagString)
      } else {
        LocaleListCompat.forLanguageTags("ar-EG")
      }
      AppCompatDelegate.setApplicationLocales(localeList)
      true
    }

    // handle theme preference
    val themePref: Preference? = findPreference(Constants.PREF_APP_THEME)
    themePref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
      ThemeUtil.setTheme(newValue as String)
      true
    }
    updatePageThemeSummary()

    // handle translation manager click
    val translationPref: Preference? = findPreference(Constants.PREF_TRANSLATION_MANAGER)
    translationPref?.setOnPreferenceClickListener {
      startActivity(Intent(activity, TranslationManagerActivity::class.java))
      true
    }

    // handle audio manager click
    val audioManagerPref: Preference? = findPreference(Constants.PREF_AUDIO_MANAGER)
    audioManagerPref?.setOnPreferenceClickListener {
      startActivity(Intent(activity, AudioManagerActivity::class.java))
      true
    }
    updateSelectedReciterSummary()

    val pageChangePref: Preference? = findPreference(Constants.PREF_PAGE_TYPE)
    if (pageTypes.size < 2 && pageChangePref != null) {
      val readingPrefs: Preference? = findPreference(Constants.PREF_READING_CATEGORY)
      (readingPrefs as PreferenceGroup).removePreference(pageChangePref)
    }

    // add additional injected preferences (if any)
    extraPreferences
      .sortedBy { it.order }
      .forEach { it.addPreferences(preferenceScreen) }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = super.onCreateView(inflater, container, savedInstanceState)
    val recyclerView = listView
    recyclerView.clipToPadding = false
    ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      recyclerView.updateLayoutParams<ViewGroup.LayoutParams> {
        // top, left, right are handled by QuranActivity
        view.setPadding(0, 0, 0, insets.bottom)
      }

      windowInsets
    }
    return view
  }

  override fun onPreferenceTreeClick(preference: Preference): Boolean {
    val key = preference.key
    if ("key_prefs_advanced" == key) {
      val intent = Intent(activity, QuranAdvancedPreferenceActivity::class.java)
      startActivity(intent)
      return true
    } else if ("prefs_memory_flashcards" == key) {
      val intent = Intent(activity, com.quran.labs.androidquran.flashcards.MemoryFlashcardsActivity::class.java)
      startActivity(intent)
      return true
    } else if ("prefs_children_learning_mode" == key) {
      com.quran.labs.androidquran.kidsclassroom.KidsModeActivity.start(requireContext())
      return true
    } else if ("prefs_live_tasmee_mode" == key) {
      val ctx = requireContext()
      val settings = QuranSettings.getInstance(ctx)
      val initialPage = settings.lastPage.takeIf { it in 1..604 } ?: 1
      val intent = Intent(activity, com.quran.labs.androidquran.ui.PagerActivity::class.java).apply {
        putExtra("page", initialPage)
        putExtra(com.quran.labs.androidquran.ui.PagerActivity.EXTRA_START_LIVE_TASMEE_MODE, true)
      }
      startActivity(intent)
      return true
    } else if (SELECT_RECITER_KEY == key) {
      showSelectReciterDialog()
      return true
    } else if (Constants.PREF_PAGE_TYPE == key) {
      val intent = Intent(activity, PageSelectActivity::class.java)
      startActivity(intent)
      return true
    } else if (Constants.PREF_PAGE_THEME == key) {
      val content = requireActivity().findViewById<ViewGroup>(android.R.id.content)
      lateinit var pageThemeSheet: PageThemeSheetWrapper
      pageThemeSheet = PageThemeSheetWrapper(
        requireContext(),
        QuranSettings.getInstance(requireContext())
      ) {
        content.removeView(pageThemeSheet)
        updatePageThemeSummary()
      }
      content.addView(
        pageThemeSheet,
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      return true
    }

    for (extraPref in extraPreferences) {
      if (extraPref.onPreferenceClick(preference)) {
        return true
      }
    }

    return super.onPreferenceTreeClick(preference)
  }

  private fun updatePageThemeSummary() {
    findPreference<Preference>(Constants.PREF_PAGE_THEME)?.summary =
      pageThemeSummary(requireContext(), QuranSettings.getInstance(requireContext()))
  }

  private fun updateSelectedReciterSummary() {
    val context = context ?: return
    val currentItem = QariItem.fromQari(context, currentQariManager.currentQari())
    findPreference<Preference>(SELECT_RECITER_KEY)?.summary = currentItem.name
  }

  private fun showSelectReciterDialog() {
    val context = requireContext()
    val qaris = qariUtil.getQariList(context)
    if (qaris.isEmpty()) return
    val currentId = currentQariManager.currentQariId()
    val checkedIndex = qaris.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val titles = qaris.map { it.name }.toTypedArray()

    AlertDialog.Builder(context)
      .setTitle(R.string.select_reciter_title)
      .setSingleChoiceItems(titles, checkedIndex) { dialog, which ->
        val selected = qaris.getOrNull(which)
        if (selected != null) {
          currentQariManager.setCurrentQari(selected.id)
          updateSelectedReciterSummary()
        }
        dialog.dismiss()
      }
      .setNegativeButton(R.string.flashcard_delete_cancel_button, null)
      .show()
  }

  private fun isCurrentlyArabic(): Boolean {
    val locale = QuranUtils.getCurrentLocale()
    return locale.language == "ar"
  }

  private fun hideUnsupportedOrientationPreferences() {
    val displayPrefs = findPreference<PreferenceGroup>(getString(R.string.prefs_display_category_key))
    val lockOrientationPref = findPreference<Preference>(Constants.PREF_LOCK_ORIENTATION)
    val landscapeOrientationPref = findPreference<Preference>(Constants.PREF_LANDSCAPE_ORIENTATION)

    if (displayPrefs != null && lockOrientationPref != null) {
      displayPrefs.removePreference(lockOrientationPref)
    }
    if (displayPrefs != null && landscapeOrientationPref != null) {
      displayPrefs.removePreference(landscapeOrientationPref)
    }
  }

  companion object {
    private const val ARABIC_KEY = "useArabicNames"
    private const val SELECT_RECITER_KEY = "prefs_select_reciter"
  }
}
