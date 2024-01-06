package com.quran.labs.androidquran.ui.fragment

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import com.quran.data.core.QuranInfo
import com.quran.data.model.VerseRange
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.presenter.translation.InlineTranslationPresenter
import com.quran.labs.androidquran.presenter.translation.InlineTranslationPresenter.TranslationScreen
import com.quran.labs.androidquran.presenter.translationlist.TranslationListPresenter
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.SlidingPagerAdapter
import com.quran.labs.androidquran.ui.util.TranslationsSpinnerAdapter
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.view.InlineTranslationView
import com.quran.labs.androidquran.view.QuranSpinner
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
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

  private val scope = MainScope()

  object Provider : AyahActionFragmentProvider {
    override val order = SlidingPagerAdapter.TRANSLATION_PAGE
    override val iconResId = com.quran.labs.androidquran.common.toolbar.R.drawable.ic_translation
    override fun newAyahActionFragment() = AyahTranslationFragment()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (activity as? PagerActivity)?.pagerActivityComponent?.inject(this)
  }

  override fun onDetach() {
    scope.cancel()
    super.onDetach()
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

  override fun onTranslationsUpdated(translations: List<LocalTranslation>) {
    if (translations.isEmpty()) {
      progressBar.visibility = View.GONE
      emptyState.visibility = View.VISIBLE
      translationControls.visibility = View.GONE
      translator.visibility = View.GONE
      translationView.visibility = View.GONE
    } else {
      val activeTranslationsFilesNames = quranSettings.activeTranslations

      val adapter = translationAdapter
      if (adapter == null) {
        translationAdapter = TranslationsSpinnerAdapter(
          activity,
          R.layout.translation_ab_spinner_item,
          translations.map { it.resolveTranslatorName() }.toTypedArray(),
          translations,
          activeTranslationsFilesNames,
        ) { selectedItems: Set<String?>? ->
          quranSettings.activeTranslations = selectedItems
          // this is the refresh for when a translation is selected from the spinner
          refreshView()
        }
        translator.adapter = translationAdapter
      } else {
        adapter.updateItems(
          translations.map { it.resolveTranslatorName() }.toTypedArray(),
          translations,
          activeTranslationsFilesNames
        )
      }
      refreshView()
    }
  }

  public override fun refreshView() {
    val start = start
    val end = end
    if (start == null || end == null) {
      return
    }

    val verses = 1 + abs(
      quranInfo.getAyahId(start.sura, start.ayah) - quranInfo.getAyahId(end.sura, end.ayah)
    )
    val verseRange = VerseRange(start.sura, start.ayah, end.sura, end.ayah, verses)
    scope.launch {
      translationPresenter.refresh(verseRange)
    }
  }

  override fun setVerses(translations: Array<LocalTranslation>, verses: List<QuranAyahInfo>) {
    progressBar.visibility = View.GONE
    if (verses.isNotEmpty()) {
      emptyState.visibility = View.GONE
      translationControls.visibility = View.VISIBLE
      translator.visibility = View.VISIBLE
      translationView.visibility = View.VISIBLE
      translationView.setAyahs(translations, verses)
    } else {
      emptyState.visibility = View.VISIBLE
    }
  }
}
