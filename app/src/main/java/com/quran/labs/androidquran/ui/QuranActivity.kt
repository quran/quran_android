package com.quran.labs.androidquran.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.quran.labs.androidquran.AboutUsActivity
import com.quran.labs.androidquran.HelpActivity
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranPreferenceActivity
import com.quran.labs.androidquran.R
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.SearchActivity
import com.quran.labs.androidquran.ShortcutsActivity
import com.quran.mobile.di.InlineVoiceSearchController
import com.quran.mobile.di.InlineVoiceSearchState
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.model.bookmark.RecentPageModel
import com.quran.labs.androidquran.presenter.data.QuranIndexEventLogger
import com.quran.labs.androidquran.presenter.translation.TranslationManagerPresenter
import com.quran.labs.androidquran.service.AudioService
import com.quran.labs.androidquran.ui.fragment.AddTagDialog
import com.quran.labs.androidquran.ui.fragment.AddTagDialog.Companion.newInstance
import com.quran.labs.androidquran.ui.fragment.BookmarksFragment
import com.quran.labs.androidquran.ui.fragment.JumpFragment
import com.quran.labs.androidquran.ui.fragment.JuzListFragment
import com.quran.labs.androidquran.ui.fragment.SuraListFragment
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog
import com.quran.labs.androidquran.ui.fragment.TagBookmarkDialog.OnBookmarkTagsUpdateListener
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.util.AudioUtils
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.SlidingTabLayout
import com.quran.mobile.di.ExtraScreenProvider
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.math.abs

/**
 * The home screen activity for the app. Displays a toolbar and 3 fragments:
 *
 *  * [SuraListFragment]
 *  * [JuzListFragment]
 *  * [BookmarksFragment]
 *
 * When this activity is created, it may run a background check to see if updated translations
 * are available, and if so, show a dialog asking the user if they want to download them.
 *
 * This activity is called from several places:
 *  * [com.quran.labs.androidquran.QuranDataActivity]
 *  * [ShortcutsActivity]
 */
class QuranActivity : AppCompatActivity(),
    OnBookmarkTagsUpdateListener,
    JumpDestination {
  private var upgradeDialog: AlertDialog? = null
  private var showedTranslationUpgradeDialog = false
  private var isRtl = false
  private var isPaused = false
  private var searchItem: MenuItem? = null
  private var voiceSearchItem: MenuItem? = null
  private var micAnimator: ObjectAnimator? = null
  private var supportActionMode: ActionMode? = null
  private val compositeDisposable = CompositeDisposable()
  lateinit var latestPageObservable: Observable<Int>

  private var backStackListener: FragmentManager.OnBackStackChangedListener? = null
  private lateinit var searchItemCollapserCallback: OnBackPressedCallback
  private lateinit var supportActionModeClearingCallback: OnBackPressedCallback

  @Inject
  lateinit var settings: QuranSettings
  @Inject
  lateinit var audioUtils: AudioUtils
  @Inject
  lateinit var recentPageModel: RecentPageModel
  @Inject
  lateinit var translationManagerPresenter: TranslationManagerPresenter
  @Inject
  lateinit var quranIndexEventLogger: QuranIndexEventLogger
  @Inject
  lateinit var extraScreens: Set<@JvmSuppressWildcards ExtraScreenProvider>

  @Inject
  lateinit var inlineVoiceSearchControllers: Set<@JvmSuppressWildcards InlineVoiceSearchController>

  @Inject
  lateinit var quranInfo: QuranInfo

  private val inlineController: InlineVoiceSearchController?
    get() = inlineVoiceSearchControllers.firstOrNull()

  private val recordPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      startInlineRecording()
    }
  }

  private var jumpToPageOnResume: Int? = null

  public override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val quranApp = application as QuranApplication
    quranApp.applicationComponent
      .activityComponentFactory()
      .generate(this)
      .quranActivityComponentFactory()
      .generate()
      .inject(this)

    registerBackPressedCallbacks()
    setContentView(R.layout.quran_index)
    isRtl = isRtl()

    val root = findViewById<ViewGroup>(R.id.root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        leftMargin = insets.left
        rightMargin = insets.right
      }

      // if we return WindowInsetsCompat.CONSUMED, the SnackBar won't
      // be properly positioned on Android 29 and below (will be under
      // the navigation bar).
      windowInsets
    }

    val tb = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(tb)
    val ab = supportActionBar
    ab?.setTitle(R.string.app_name)

    val pager = findViewById<ViewPager>(R.id.index_pager)
    pager.offscreenPageLimit = 3
    val pagerAdapter = PagerAdapter(supportFragmentManager)
    pager.adapter = pagerAdapter
    val indicator = findViewById<SlidingTabLayout>(R.id.indicator)
    indicator.setViewPager(pager)
    jumpToPageOnResume = if (isRtl) {
      TITLES.size - 1
    } else {
      0
    }

    if (savedInstanceState != null) {
      showedTranslationUpgradeDialog = savedInstanceState.getBoolean(
          SI_SHOWED_UPGRADE_DIALOG, false
      )
    }

    latestPageObservable = recentPageModel.getLatestPageObservable()
    val intent = intent
    if (intent != null) {
      val extras = intent.extras
      if (extras != null) {
        if (extras.getBoolean(EXTRA_SHOW_TRANSLATION_UPGRADE, false)) {
          if (!showedTranslationUpgradeDialog) {
            showTranslationsUpgradeDialog()
          }
        }
      }
      if (ShortcutsActivity.ACTION_JUMP_TO_LATEST == intent.action) {
        jumpToLastPage()
      }
    }
    updateTranslationsListAsNeeded()
    quranIndexEventLogger.logAnalytics()

    observeInlineVoiceSearchState()
  }

  private fun observeInlineVoiceSearchState() {
    val controller = inlineController ?: return
    lifecycleScope.launch {
      controller.state.collect { state ->
        when (state) {
          is InlineVoiceSearchState.Recording -> {
            showPulsingMic()
            if (state.partialText.isNotEmpty()) {
              val sv = searchItem?.actionView as? SearchView
              if (searchItem?.isActionViewExpanded == true && sv != null) {
                sv.setQuery(state.partialText, false)
              }
            }
          }
          is InlineVoiceSearchState.FinalResult -> {
            stopPulsingMic()
            if (state.text.isNotEmpty()) {
              val searchIntent = Intent(this@QuranActivity, SearchActivity::class.java).apply {
                action = Intent.ACTION_SEARCH
                putExtra(SearchManager.QUERY, state.text)
              }
              startActivity(searchIntent)
            }
            controller.reset()
          }
          is InlineVoiceSearchState.ModelNotReady -> {
            stopPulsingMic()
            showModelNotReadyDialog()
            controller.reset()
          }
          is InlineVoiceSearchState.Error -> {
            stopPulsingMic()
            Toast.makeText(this@QuranActivity, state.message, Toast.LENGTH_SHORT).show()
            controller.reset()
          }
          is InlineVoiceSearchState.Idle -> {
            stopPulsingMic()
          }
        }
      }
    }
  }

  private fun showModelNotReadyDialog() {
    AlertDialog.Builder(this)
      .setTitle(R.string.voice_search)
      .setMessage(R.string.voice_search_model_not_ready)
      .setPositiveButton(R.string.menu_settings) { dialog, _ ->
        dialog.dismiss()
        startActivity(Intent(this, QuranPreferenceActivity::class.java))
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun showPulsingMic() {
    val item = voiceSearchItem ?: return
    if (micAnimator != null) return

    val micImage = ImageView(this).apply {
      setImageResource(R.drawable.ic_mic)
      imageTintList = ContextCompat.getColorStateList(this@QuranActivity, R.color.voice_search_recording)
    }
    item.actionView = micImage
    micImage.setOnClickListener { toggleRecording() }

    val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.3f, 1.0f)
    val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.3f, 1.0f)
    micAnimator = ObjectAnimator.ofPropertyValuesHolder(micImage, scaleX, scaleY).apply {
      duration = 800
      repeatCount = ObjectAnimator.INFINITE
      start()
    }
  }

  private fun stopPulsingMic() {
    micAnimator?.cancel()
    micAnimator = null
    voiceSearchItem?.actionView = null
  }

  private fun toggleRecording() {
    val controller = inlineController ?: return
    when (controller.state.value) {
      is InlineVoiceSearchState.Recording -> controller.stopRecording()
      is InlineVoiceSearchState.Idle -> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
          == PackageManager.PERMISSION_GRANTED
        ) {
          startInlineRecording()
        } else {
          recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
      }
      else -> { /* ignore during transitional states */ }
    }
  }

  private fun startInlineRecording() {
    inlineController?.startRecording()
  }

  public override fun onResume() {
    compositeDisposable.add(latestPageObservable.subscribe())
    super.onResume()
    val isRtl = isRtl()
    if (isRtl != this.isRtl) {
      val i = intent
      finish()
      startActivity(i)
    } else {
      val pageToJumpTo = jumpToPageOnResume
      if (pageToJumpTo != null) {
        findViewById<ViewPager>(R.id.index_pager).currentItem = pageToJumpTo
        jumpToPageOnResume = null
      }

      compositeDisposable.add(
          Completable.timer(500, MILLISECONDS)
              .observeOn(AndroidSchedulers.mainThread())
              .subscribe {
                try {
                  startService(
                    audioUtils.getAudioIntent(this@QuranActivity, AudioService.ACTION_STOP)
                  )
                } catch (_: IllegalStateException) {
                  // do nothing, we might be in the background
                  // onPause should have stopped us from needing this, but it sometimes happens
                }
              }
      )
    }
    isPaused = false
  }

  override fun onPause() {
    compositeDisposable.clear()
    isPaused = true
    super.onPause()
  }

  override fun onDestroy() {
    val controller = inlineController
    if (controller != null && controller.state.value is InlineVoiceSearchState.Recording) {
      controller.stopRecording()
    }
    // only set to handle Android Q memory leaks
    backStackListener?.let {
      supportFragmentManager.removeOnBackStackChangedListener(it)
    }
    super.onDestroy()
  }

  // on back pressed, these are run in reverse order of registration
  private fun registerBackPressedCallbacks() {
    // this block works around a memory leak in Android Q
    // https://issuetracker.google.com/issues/139738913
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && isTaskRoot) {
      val enabled = (supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.backStackEntryCount ?: 0) == 0 &&
          supportFragmentManager.backStackEntryCount == 0
      val callback = object : OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
          finishAfterTransition()
        }
      }
      onBackPressedDispatcher.addCallback(this, callback)

      val listener = FragmentManager.OnBackStackChangedListener {
        callback.isEnabled =
          (supportFragmentManager.primaryNavigationFragment?.childFragmentManager?.backStackEntryCount
            ?: 0) == 0 &&
              supportFragmentManager.backStackEntryCount == 0
      }
      backStackListener = listener
      supportFragmentManager.addOnBackStackChangedListener(listener)
    }

    // collapse the search view if it's expanded on back press
    val searchItemExpanded = searchItem?.isActionViewExpanded ?: false
    searchItemCollapserCallback = object : OnBackPressedCallback(searchItemExpanded) {
      override fun handleOnBackPressed() {
        val searchItem = searchItem
        if (searchItem != null && searchItem.isActionViewExpanded) {
          searchItem.collapseActionView()
        }
        // once it's collapsed, disable it
        isEnabled = false
      }
    }

    // clear the action mode if it's active on back press
    val supportActionModeEnabled = supportActionMode != null
    supportActionModeClearingCallback = object : OnBackPressedCallback(supportActionModeEnabled) {
      override fun handleOnBackPressed() {
        supportActionMode?.finish()
      }
    }
  }

  private fun isRtl(): Boolean {
    return QuranUtils.isRtl()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    val inflater = menuInflater
    inflater.inflate(R.menu.home_menu, menu)
    searchItem = menu.findItem(R.id.search)
    voiceSearchItem = menu.findItem(R.id.voice_search)
    val voiceSearchEnabled = inlineController?.isEnabled == true

    searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
      override fun onMenuItemActionExpand(item: MenuItem): Boolean {
        searchItemCollapserCallback.isEnabled = true
        voiceSearchItem?.isVisible = voiceSearchEnabled
        return true
      }

      override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
        searchItemCollapserCallback.isEnabled = false
        voiceSearchItem?.isVisible = false
        return true
      }
    })
    val searchView = searchItem?.actionView as SearchView
    val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
    searchView.queryHint = getString(R.string.search_hint)
    searchView.setSearchableInfo(
        searchManager.getSearchableInfo(
            ComponentName(this, SearchActivity::class.java)
        )
    )

    // Add additional injected screens (if any)
    extraScreens
      .sortedBy { it.order }
      .forEach { menu.add(Menu.NONE, it.id, Menu.NONE, it.titleResId) }

    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (val itemId = item.itemId) {
      R.id.settings -> {
        startActivity(Intent(this, QuranPreferenceActivity::class.java))
      }
      R.id.last_page -> {
        jumpToLastPage()
      }
      R.id.help -> {
        startActivity(Intent(this, HelpActivity::class.java))
      }
      R.id.about -> {
        startActivity(Intent(this, AboutUsActivity::class.java))
      }
      R.id.jump -> {
        gotoPageDialog()
      }
      R.id.voice_search -> {
        toggleRecording()
      }
      R.id.other_apps -> {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = "market://search?q=pub:quran.com".toUri()
        if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
          intent.data = "https://play.google.com/store/search?q=pub:quran.com".toUri()
        }
        startActivity(intent)
      }
      else -> {
        val handled = extraScreens.firstOrNull { it.id == itemId }?.onClick(this) ?: false
        return handled || super.onOptionsItemSelected(item)
      }
    }
    return true
  }

  override fun onSupportActionModeFinished(mode: ActionMode) {
    supportActionMode = null
    supportActionModeClearingCallback.isEnabled = false
    super.onSupportActionModeFinished(mode)
  }

  override fun onSupportActionModeStarted(mode: ActionMode) {
    supportActionMode = mode
    supportActionModeClearingCallback.isEnabled = true
    super.onSupportActionModeStarted(mode)

    /**
     * hack to fix the status bar color when action mode starts.
     * unfortunately, despite being edge to edge, switching to contextual action mode causes
     * [androidx.appcompat.app.AppCompatDelegate] and its implementation to set a status guard
     * under the status bar (white in light mode, black in dark mode). this breaks the edge to
     * edge look and feel, so we manually set the status guard's background color.
     */
    val abRoot = findViewById<ViewGroup>(androidx.appcompat.R.id.action_bar_root)
    // has to be .post otherwise the background is set to the default color overriding this
    abRoot.post {
      val statusGuard = abRoot.getChildAt(abRoot.childCount - 1)
      statusGuard?.let {
        // not using `is` here because i literally want a View, not a subclass of View.
        // checking top to be 0 is just a second just in case check.
        if (statusGuard::class == View::class && statusGuard.top == 0) {
          statusGuard.setBackgroundColor(ContextCompat.getColor(this, R.color.toolbar))
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean(
        SI_SHOWED_UPGRADE_DIALOG,
        showedTranslationUpgradeDialog
    )
    super.onSaveInstanceState(outState)
  }

  private fun jumpToLastPage() {
    compositeDisposable.add(
        latestPageObservable
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { recentPage: Int ->
              jumpTo(
                  if (recentPage == Constants.NO_PAGE) 1 else recentPage
              )
            }
    )
  }

  private fun updateTranslationsListAsNeeded() {
    if (!updatedTranslations) {
      translationManagerPresenter.checkForUpdates()
      updatedTranslations = true
    }
  }

  private fun showTranslationsUpgradeDialog() {
    showedTranslationUpgradeDialog = true

    val builder = Builder(this)
    builder.setMessage(R.string.translation_updates_available)
    builder.setCancelable(false)
    builder.setPositiveButton(R.string.translation_dialog_yes) { dialog: DialogInterface, _: Int ->
      dialog.dismiss()
      upgradeDialog = null
      launchTranslationActivity()
    }

    builder.setNegativeButton(R.string.translation_dialog_later) { dialog: DialogInterface, _: Int ->
      dialog.dismiss()
      upgradeDialog = null
      // pretend we don't have updated translations.  we'll
      // check again after 10 days.
      settings.setHaveUpdatedTranslations(false)
    }

    val dialog = builder.create()
    dialog.show()
    upgradeDialog = dialog
  }

  private fun launchTranslationActivity() {
    val i = Intent(this, TranslationManagerActivity::class.java)
    startActivity(i)
  }

  override fun jumpTo(page: Int) {
    val i = Intent(this, PagerActivity::class.java)
    i.putExtra("page", page)
    i.putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, settings.wasShowingTranslation)
    startActivity(i)
  }

  override fun jumpToAndHighlight(page: Int, sura: Int, ayah: Int) {
    val i = Intent(this, PagerActivity::class.java)
    i.putExtra("page", page)
    i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura)
    i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah)
    i.putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, settings.wasShowingTranslation)
    startActivity(i)
  }

  private fun gotoPageDialog() {
    if (!isPaused) {
      val fm = supportFragmentManager
      val jumpDialog = JumpFragment()
      jumpDialog.show(fm, JumpFragment.TAG)
    }
  }

  fun addTag() {
    if (!isPaused) {
      val fm = supportFragmentManager
      val addTagDialog = AddTagDialog()
      addTagDialog.show(fm, AddTagDialog.TAG)
    }
  }

  fun editTag(id: Long, name: String?) {
    if (!isPaused) {
      val fm = supportFragmentManager
      val addTagDialog = newInstance(id, name!!)
      addTagDialog.show(fm, AddTagDialog.TAG)
    }
  }

  fun tagBookmarks(ids: LongArray?) {
    if (ids != null && ids.size == 1) {
      tagBookmark(ids[0])
      return
    }

    if (!isPaused) {
      val fm = supportFragmentManager
      val tagBookmarkDialog = TagBookmarkDialog.newInstance(ids)
      tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG)
    }
  }

  private fun tagBookmark(id: Long) {
    if (!isPaused) {
      val fm = supportFragmentManager
      val tagBookmarkDialog = TagBookmarkDialog.newInstance(id)
      tagBookmarkDialog.show(fm, TagBookmarkDialog.TAG)
    }
  }

  override fun onAddTagSelected() {
    val fm = supportFragmentManager
    val dialog = AddTagDialog()
    dialog.show(fm, AddTagDialog.TAG)
  }

  private inner class PagerAdapter(fm: FragmentManager) :
      FragmentPagerAdapter(fm) {

    override fun getCount() = 3

    override fun getItem(position: Int): Fragment {
      var pos = position
      if (isRtl) {
        pos = abs(position - 2)
      }
      return when (pos) {
        SURA_LIST -> SuraListFragment.newInstance()
        JUZ2_LIST -> JuzListFragment.newInstance()
        BOOKMARKS_LIST -> BookmarksFragment.newInstance()
        else -> BookmarksFragment.newInstance()
      }
    }

    override fun getItemId(position: Int): Long {
      val pos = if (isRtl) abs(position - 2) else position
      return when (pos) {
        SURA_LIST -> SURA_LIST.toLong()
        JUZ2_LIST -> JUZ2_LIST.toLong()
        BOOKMARKS_LIST -> BOOKMARKS_LIST.toLong()
        else -> BOOKMARKS_LIST.toLong()
      }
    }

    override fun getPageTitle(position: Int): CharSequence {
      val resId = if (isRtl) ARABIC_TITLES[position] else TITLES[position]
      return getString(resId)
    }
  }

  companion object {
    private val TITLES = intArrayOf(
        R.string.quran_sura,
        R.string.quran_juz2,
        R.string.menu_bookmarks
    )
    private val ARABIC_TITLES = intArrayOf(
        R.string.menu_bookmarks,
        R.string.quran_juz2,
        R.string.quran_sura
    )
    const val EXTRA_SHOW_TRANSLATION_UPGRADE = "transUp"
    private const val SI_SHOWED_UPGRADE_DIALOG = "si_showed_dialog"
    private const val SURA_LIST = 0
    private const val JUZ2_LIST = 1
    private const val BOOKMARKS_LIST = 2
    private var updatedTranslations = false
  }
}
