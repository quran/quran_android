package com.quran.labs.androidquran.ui.fragment

import com.quran.labs.androidquran.presenter.translation.InlineTranslationPresenter
import android.widget.ProgressBar
import com.quran.labs.androidquran.view.InlineTranslationView
import com.quran.labs.androidquran.view.QuranSpinner
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter
import javax.inject.Inject
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.ui.PagerActivity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Bundle
import com.quran.labs.androidquran.R
import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Button
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.presenter.translation.InlineTranslationPresenter.TranslationScreen
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.mobile.di.AyahActionFragmentProvider
import kotlin.math.abs

class AyahTranslationFragment : AyahActionFragment(), TranslationScreen {
  private lateinit var progressBar: ProgressBar
  private lateinit var translationView: InlineTranslationView
  private lateinit var emptyState: View
  private lateinit var translationControls: View
  private lateinit var translator: QuranSpinner

  private var translationAdapter: TranslationsSpinnerAdapter? = null

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var quranSettings: QuranSettings

  @Inject
  lateinit var translationPresenter: InlineTranslationPresenter

  object Provider : AyahActionFragmentProvider {
    override val order = SlidingPagerAdapter.TRANSLATION_PAGE
    override val iconResId = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_translation
    override fun newAyahActionFragment() = AyahTranslationFragment()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (activity as? PagerActivity)?.pagerActivityComponent?.inject(this)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(
      R.layout.translation_panel, container, false
    )
    translator = view.findViewById(R.id.translator)
    translationView = view.findViewById(R.id.translation_view)
    progressBar = view.findViewById(R.id.progress)
    emptyState = view.findViewById(R.id.empty_state)
    translationControls = view.findViewById(R.id.controls)

    val next = translationControls.findViewById<View>(R.id.next_ayah)
    next.setOnClickListener(onClickListener)
    val prev = translationControls.findViewById<View>(R.id.previous_ayah)
    prev.setOnClickListener(onClickListener)
    val getTranslations = view.findViewById<Button>(R.id.get_translations_button)
    getTranslations.setOnClickListener(onClickListener)
    return view
  }

  override fun onResume() {
    // currently needs to be before we call super.onResume
    translationPresenter.bind(this)
    super.onResume()
  }

  override fun onPause() {
    translationPresenter.unbind(this)
    super.onPause()
  }

  private val onClickListener = View.OnClickListener { v: View ->
    val activity: Activity? = activity
    if (activity is PagerActivity) {
      when (v.id) {
        R.id.get_translations_button -> activity.startTranslationManager()
        R.id.next_ayah -> readingEventPresenter.selectNextAyah()
        R.id.previous_ayah -> readingEventPresenter.selectPreviousAyah()
      }
    }
  }

  public override fun refreshView() {
    val start = start
    val end = end
    if (start == null || end == null) {
      return
    }
    val activity: Activity? = activity
    if (activity is PagerActivity) {
      val translations = activity.translations
      if (translations == null || translations.size == 0) {
        progressBar.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        translationControls.visibility = View.GONE
        return
      }

      var activeTranslationsFilesNames = activity.activeTranslationsFilesNames
      if (activeTranslationsFilesNames == null) {
        activeTranslationsFilesNames = quranSettings.activeTranslations
      }

      val adapter = translationAdapter
      if (adapter == null) {
        translationAdapter = TranslationsSpinnerAdapter(
          activity,
          R.layout.translation_ab_spinner_item,
          activity.translationNames,
          translations,
          activeTranslationsFilesNames
        ) { selectedItems: Set<String?>? ->
          quranSettings.activeTranslations = selectedItems
          refreshView()
        }
        translator.adapter = translationAdapter
      } else {
        adapter.updateItems(
          activity.translationNames,
          translations,
          activeTranslationsFilesNames
        )
      }
      if (start == end) {
        translationControls.visibility = View.VISIBLE
      } else {
        translationControls.visibility = View.GONE
      }
      val verses = 1 + abs(
        quranInfo.getAyahId(start.sura, start.ayah) - quranInfo.getAyahId(end.sura, end.ayah)
      )
      val verseRange = VerseRange(start.sura, start.ayah, end.sura, end.ayah, verses)
      translationPresenter.refresh(verseRange)
    }
  }

  override fun setVerses(translations: Array<LocalTranslation>, verses: List<QuranAyahInfo>) {
    progressBar.visibility = View.GONE
    if (verses.isNotEmpty()) {
      emptyState.visibility = View.GONE
      translationView.setAyahs(translations, verses)
    } else {
      emptyState.visibility = View.VISIBLE
    }
  }
}
