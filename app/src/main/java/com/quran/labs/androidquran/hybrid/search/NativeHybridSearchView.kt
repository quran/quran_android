package com.quran.labs.androidquran.hybrid.search

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.ui.core.AppThemeStyle
import com.quran.labs.androidquran.hybrid.contract.AdaptiveMotionTokens
import com.quran.labs.androidquran.hybrid.contract.HybridSearchActionHandler
import com.quran.labs.androidquran.hybrid.ui.HybridArchitecturalIcons
import com.quran.labs.androidquran.hybrid.ui.HybridViewPalette
import com.quran.labs.androidquran.hybrid.ui.HybridViewTokens
import com.quran.labs.androidquran.hybrid.ui.NativeUthmaniTypefaceCache
import com.quran.labs.androidquran.hybrid.ui.VectorPathDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Pure Native Android View hosting instant Quran / Surah / Tafsir search (`SearchActivity`
 * and Main Hub `بحث` tab). Replaces Compose `LazyColumn` + `BasicTextField` to eliminate
 * UI-thread MultiParagraph shaping jank and Compose runtime overhead.
 */
class NativeHybridSearchView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

  private val searchBarContainer = LinearLayout(context)
  private val searchIconDrawable: VectorPathDrawable
  val searchInput: EditText
  private val queryTextWatcher: TextWatcher
  private val editorActionListener: TextView.OnEditorActionListener
  private val hitCountBadge = TextView(context)
  val clearButton: ImageView
  private val clearIconDrawable: VectorPathDrawable

  private val recentsScroll = HorizontalScrollView(context)
  private val recentsRow = LinearLayout(context)

  private val scopeRow = LinearLayout(context)
  private val scopeButtons = LinkedHashMap<HybridSearchScope, TextView>()

  val resultsRecyclerView: RecyclerView
  private val layoutManager = LinearLayoutManager(context)
  private val adapter: HybridSearchAdapter

  private var palette = HybridViewPalette.from(AppThemeStyle.BEIGE, false)
  private var engine: HybridSearchEngine? = null
  private var actionHandler: HybridSearchActionHandler? = null
  private var stateCollectionJob: Job? = null
  private var inputListenersAttached = true
  private var isSyncingQueryText = false
  private var latestState: HybridSearchUiState = HybridSearchUiState()
  private var hasSubmittedRows = false
  private var lastAyahHitsRef: List<AyahSearchHit>? = null
  private var lastBookmarkedKeysRef: Set<Long>? = null
  private var cachedAyahRows: List<HybridSearchRow.AyahCard> = emptyList()
  private var lastRenderedScope: HybridSearchScope? = null
  private var topExtraPaddingPx: Int = 0
  private var bottomInsetPaddingPx: Int = 0

  var onDownloadArabicSearchDb: (() -> Unit)? = null
  var onOpenTranslationManager: (() -> Unit)? = null

  init {
    id = R.id.hybrid_search_root
    orientation = VERTICAL
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    clipToPadding = false

    val hPad = HybridViewTokens.dp(context, 14f)
    val tPad = HybridViewTokens.dp(context, 8f)
    setPadding(hPad, tPad, hPad, 0)

    searchIconDrawable = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.SEARCH,
      palette.accent,
      HybridViewTokens.dp(context, 18f)
    )
    clearIconDrawable = HybridArchitecturalIcons.drawable(
      HybridArchitecturalIcons.CLOSE,
      palette.textSecondary,
      HybridViewTokens.dp(context, 15f)
    )

    queryTextWatcher = object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) {
        if (!isSyncingQueryText) {
          engine?.onQueryTextChanged(s?.toString().orEmpty())
        }
      }
    }
    editorActionListener = TextView.OnEditorActionListener { _, actionId, event ->
      val isSearchKey = actionId == EditorInfo.IME_ACTION_SEARCH ||
        (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
      if (isSearchKey) {
        engine?.submitQuery(searchInput.text?.toString().orEmpty())
        hideKeyboard()
        true
      } else {
        false
      }
    }

    searchInput = EditText(context).apply {
      id = R.id.hybrid_search_input
      hint = context.getString(R.string.hybrid_search_placeholder)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
      setSingleLine(true)
      maxLines = 1
      imeOptions = EditorInfo.IME_ACTION_SEARCH
      background = null
      gravity = Gravity.CENTER_VERTICAL or Gravity.START
      textAlignment = View.TEXT_ALIGNMENT_VIEW_START
      setPadding(0, 0, 0, 0)
      includeFontPadding = false
      addTextChangedListener(queryTextWatcher)
      setOnEditorActionListener(editorActionListener)
    }

    hitCountBadge.apply {
      tag = SearchTestTags.HIT_COUNT_BADGE
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      maxLines = 1
      val ph = HybridViewTokens.dp(context, 8f)
      val pv = HybridViewTokens.dp(context, 3f)
      setPadding(ph, pv, ph, pv)
      visibility = View.GONE
    }

    clearButton = ImageView(context).apply {
      id = R.id.hybrid_search_clear
      tag = SearchTestTags.CLEAR_BUTTON
      setImageDrawable(clearIconDrawable)
      scaleType = ImageView.ScaleType.CENTER
      contentDescription = context.getString(R.string.hybrid_search_clear_cd)
      isClickable = true
      isFocusable = true
      visibility = View.GONE
      setOnClickListener {
        isSyncingQueryText = true
        searchInput.setText("")
        isSyncingQueryText = false
        engine?.clearQuery()
      }
      HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.ICON_PRESS_SCALE)
    }

    searchBarContainer.apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      minimumHeight = HybridViewTokens.dp(context, 46f)
      val ph = HybridViewTokens.dp(context, 12f)
      val pv = HybridViewTokens.dp(context, 4f)
      setPadding(ph, pv, ph, pv)
      alpha = 1f
      isClickable = true
      setOnClickListener {
        restoreInputIfNeeded()
        searchInput.requestFocus()
        searchInput.setSelection(searchInput.text?.length ?: 0)
        showKeyboard()
      }

      addView(
        ImageView(context).apply { setImageDrawable(searchIconDrawable) },
        LinearLayout.LayoutParams(
          HybridViewTokens.dp(context, 18f),
          HybridViewTokens.dp(context, 18f)
        ).apply { marginEnd = HybridViewTokens.dp(context, 8f) }
      )
      addView(
        searchInput,
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      )
      addView(
        hitCountBadge,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = HybridViewTokens.dp(context, 6f) }
      )
      val clearSize = HybridViewTokens.dp(context, 32f)
      addView(
        clearButton,
        LinearLayout.LayoutParams(clearSize, clearSize).apply {
          marginStart = HybridViewTokens.dp(context, 4f)
        }
      )
    }

    addView(
      searchBarContainer,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    )

    recentsRow.apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      clipChildren = false
      clipToPadding = false
      val pv = HybridViewTokens.dp(context, 2f)
      setPadding(0, pv, 0, pv)
    }
    recentsScroll.apply {
      isHorizontalScrollBarEnabled = false
      clipChildren = false
      clipToPadding = false
      visibility = View.GONE
      addView(
        recentsRow,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        )
      )
    }
    addView(
      recentsScroll,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = HybridViewTokens.dp(context, 8f)
      }
    )

    scopeRow.apply {
      orientation = HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutDirection = View.LAYOUT_DIRECTION_RTL
      clipChildren = false
      clipToPadding = false
      val pv = HybridViewTokens.dp(context, 2f)
      setPadding(0, pv, 0, pv)
    }
    val scopes = listOf(
      HybridSearchScope.AYAHS to R.string.hybrid_search_scope_ayahs,
      HybridSearchScope.SURAS to R.string.hybrid_search_scope_suras,
      HybridSearchScope.TAFSIR to R.string.hybrid_search_scope_tafsir
    )
    scopes.forEach { (scope, labelRes) ->
      val chip = TextView(context).apply {
        tag = when (scope) {
          HybridSearchScope.AYAHS -> SearchTestTags.SCOPE_AYAHS
          HybridSearchScope.SURAS -> SearchTestTags.SCOPE_SURAS
          HybridSearchScope.TAFSIR -> SearchTestTags.SCOPE_TAFSIR
        }
        text = context.getString(labelRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 1
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = HybridViewTokens.dp(context, 32f)
        val ph = HybridViewTokens.dp(context, 14f)
        val pv = HybridViewTokens.dp(context, 5f)
        setPadding(ph, pv, ph, pv)
        setOnClickListener {
          engine?.onScopeSelected(scope)
        }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      scopeButtons[scope] = chip
      scopeRow.addView(
        chip,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = HybridViewTokens.dp(context, 8f) }
      )
    }

    addView(
      scopeRow,
      LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
        topMargin = HybridViewTokens.dp(context, 8f)
        bottomMargin = HybridViewTokens.dp(context, 8f)
      }
    )

    adapter = HybridSearchAdapter(
      onOpenPage = { page, sura, ayah ->
        val q = latestState.query.trim()
        if (q.isNotBlank()) engine?.recordRecentQuery(q)
        actionHandler?.openPage(page, sura, ayah)
      },
      onPlayAyah = { sura, ayah ->
        val q = latestState.query.trim()
        if (q.isNotBlank()) engine?.recordRecentQuery(q)
        actionHandler?.playAyah(sura, ayah, page = 1)
      },
      onToggleBookmark = { sura, ayah, page ->
        actionHandler?.toggleBookmark(sura = sura, ayah = ayah, page = page)
      },
      onDownloadArabicDb = { onDownloadArabicSearchDb?.invoke() },
      onOpenTranslationManager = { onOpenTranslationManager?.invoke() },
      staticLayoutProvider = { sura, ayah -> engine?.getCachedStaticLayout(sura, ayah) },
      refLayoutProvider = { sura, ayah -> engine?.getCachedRefStaticLayout(sura, ayah) },
      pagePillLayoutProvider = { page, textColorArgb ->
        engine?.getCachedPagePillStaticLayout(page, textColorArgb)
      }
    )

    layoutManager.initialPrefetchItemCount = 6
    resultsRecyclerView = object : RecyclerView(context) {
      override fun hasOverlappingRendering(): Boolean = false
    }.apply {
      id = R.id.hybrid_search_list
      layoutManager = this@NativeHybridSearchView.layoutManager
      adapter = this@NativeHybridSearchView.adapter
      setHasFixedSize(true)
      clipToPadding = false
      itemAnimator = null
      setItemViewCacheSize(6)
      recycledViewPool.setMaxRecycledViews(HybridSearchAdapter.TYPE_AYAH, 12)
      addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
          if (dy <= 0) return
          val state = latestState
          val wantsMoreAyahs = state.scope == HybridSearchScope.AYAHS && state.hasMoreAyahHits
          val wantsMoreTafsir = state.scope == HybridSearchScope.TAFSIR && state.hasMoreTafsirHits
          if (!wantsMoreAyahs && !wantsMoreTafsir) return
          val total = this@NativeHybridSearchView.adapter.itemCount
          val lastVisible = this@NativeHybridSearchView.layoutManager.findLastVisibleItemPosition()
          if (lastVisible >= total - HybridSearchEngine.LOAD_MORE_PREFETCH_THRESHOLD) {
            if (wantsMoreAyahs) {
              engine?.loadMoreAyahHits()
            } else {
              engine?.loadMoreTafsirHits()
            }
          }
        }
      })
    }

    addView(
      resultsRecyclerView,
      LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
    )

    applyPalette(palette)
  }

  fun setStatusBarTopInset(topInsetPx: Int) {
    if (topExtraPaddingPx != topInsetPx) {
      topExtraPaddingPx = topInsetPx
      val hPad = HybridViewTokens.dp(context, 14f)
      val baseTop = HybridViewTokens.dp(context, 8f)
      setPadding(hPad, topInsetPx + baseTop, hPad, 0)
    }
  }

  fun setBottomNavOverlapPadding(bottomPx: Int) {
    if (bottomInsetPaddingPx != bottomPx) {
      bottomInsetPaddingPx = bottomPx
      resultsRecyclerView.setPadding(0, 0, 0, bottomPx + HybridViewTokens.dp(context, 12f))
    }
  }

  fun bind(
    searchEngine: HybridSearchEngine,
    handler: HybridSearchActionHandler,
    scope: CoroutineScope,
    lifecycleOwner: LifecycleOwner? = null
  ) {
    actionHandler = handler
    restoreInputIfNeeded()
    if (engine === searchEngine && stateCollectionJob?.isActive == true) return
    engine = searchEngine
    scope.launch(Dispatchers.IO) {
      NativeUthmaniTypefaceCache.get(context.applicationContext)
    }
    stateCollectionJob?.cancel()
    stateCollectionJob = if (lifecycleOwner != null) {
      lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
          searchEngine.uiState.collectLatest { state ->
            render(state)
          }
        }
      }
    } else {
      scope.launch {
        searchEngine.uiState.collectLatest { state ->
          render(state)
        }
      }
    }
  }

  fun restoreInputIfNeeded() {
    if (!searchInput.isEnabled) {
      searchInput.isEnabled = true
    }
    if (!searchInput.isFocusable) {
      searchInput.isFocusable = true
    }
    if (!searchInput.isFocusableInTouchMode) {
      searchInput.isFocusableInTouchMode = true
    }
    if (!inputListenersAttached) {
      inputListenersAttached = true
      searchInput.addTextChangedListener(queryTextWatcher)
      searchInput.setOnEditorActionListener(editorActionListener)
    }
  }

  fun clearInputFocusAndKeyboard() {
    hideKeyboard()
    if (searchInput.hasFocus()) {
      searchInput.clearFocus()
    }
  }

  fun releaseFocusAndInput() {
    hideKeyboard()
    searchInput.isFocusable = false
    searchInput.isFocusableInTouchMode = false
    searchInput.clearFocus()
    if (inputListenersAttached) {
      inputListenersAttached = false
      searchInput.setOnEditorActionListener(null)
      searchInput.removeTextChangedListener(queryTextWatcher)
    }
  }

  fun clearRecentsViews() {
    recentsRow.removeAllViews()
  }

  fun setStaticSearchBarLayerEnabled(enabled: Boolean) {
    val targetLayer = if (enabled) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
    if (searchBarContainer.layerType != targetLayer) {
      searchBarContainer.setLayerType(targetLayer, null)
    }
  }

  fun unbind() {
    releaseFocusAndInput()
    setStaticSearchBarLayerEnabled(false)
    stateCollectionJob?.cancel()
    stateCollectionJob = null
    actionHandler = null
    engine = null
    onDownloadArabicSearchDb = null
    onOpenTranslationManager = null
    clearButton.setOnClickListener(null)
    scopeButtons.values.forEach { it.setOnClickListener(null) }
    resultsRecyclerView.clearOnScrollListeners()
    resultsRecyclerView.adapter = null
    resultsRecyclerView.recycledViewPool.clear()
    recentsRow.removeAllViews()
    hasSubmittedRows = false
    lastAyahHitsRef = null
    lastBookmarkedKeysRef = null
    cachedAyahRows = emptyList()
  }

  override fun hasOverlappingRendering(): Boolean = false

  fun setQueryProgrammatically(query: String) {
    if (searchInput.text?.toString() != query) {
      isSyncingQueryText = true
      searchInput.setText(query)
      searchInput.setSelection(query.length)
      isSyncingQueryText = false
    }
  }

  fun applyPalette(newPalette: HybridViewPalette) {
    palette = newPalette
    background = HybridViewTokens.canvasAmbientGlowDrawable(
      surface = newPalette.surface,
      surfaceTopGlow = newPalette.surfaceTopGlow
    )

    searchIconDrawable.setTintColor(newPalette.accent)
    clearIconDrawable.setTintColor(newPalette.textSecondary)
    searchInput.setTextColor(newPalette.textPrimary)
    searchInput.setHintTextColor(newPalette.textSecondary)

    val strokePx = HybridViewTokens.dp(context, 1f)
    val searchPillRadius = HybridViewTokens.dpF(context, 28f)
    searchBarContainer.background = HybridViewTokens.floatingSurfaceDrawable(
      fillColor = newPalette.surfaceElevated,
      borderColor = newPalette.cardHairlineBorder,
      specularTopColor = newPalette.glassRimHighlight,
      strokeWidthPx = strokePx,
      radiusPx = searchPillRadius
    )
    HybridViewTokens.applyZLayerFloatation(
      view = searchBarContainer,
      zLevel = 2,
      isDark = newPalette.isDark,
      shadowTintColor = newPalette.shadowTint,
      radiusPx = searchPillRadius
    )

    hitCountBadge.setTextColor(newPalette.editorialAccent)
    hitCountBadge.background = HybridViewTokens.roundedDrawable(
      fillColor = newPalette.editorialSoftBg,
      radiusPx = HybridViewTokens.dpF(context, 999f)
    )

    lastRenderedScope = latestState.scope
    updateScopeChips(latestState.scope)
    updateRecentChips(latestState)
    adapter.updatePalette(newPalette)
  }

  fun render(state: HybridSearchUiState) {
    val prevState = latestState
    val prevRecents = prevState.recentQueries
    latestState = state

    setQueryProgrammatically(state.query)
    val clearVis = if (state.query.isNotBlank()) View.VISIBLE else View.GONE
    if (clearButton.visibility != clearVis) {
      clearButton.visibility = clearVis
    }

    if (lastRenderedScope != state.scope) {
      lastRenderedScope = state.scope
      updateScopeChips(state.scope)
    }

    val statsChanged = prevState.query != state.query ||
      prevState.scope != state.scope ||
      prevState.activeResultCount != state.activeResultCount ||
      prevState.suraHits.size != state.suraHits.size ||
      prevState.tafsirHits.size != state.tafsirHits.size ||
      prevState.totalTafsirHitCount != state.totalTafsirHitCount ||
      prevState.hasMoreTafsirHits != state.hasMoreTafsirHits ||
      prevState.noTafsirInstalled != state.noTafsirInstalled

    if (statsChanged) {
      val hideTafsirStats = state.scope == HybridSearchScope.TAFSIR && state.noTafsirInstalled
      if (state.query.isNotBlank() && !hideTafsirStats) {
        val (countText, hasCount) = when (state.scope) {
          HybridSearchScope.AYAHS -> {
            val n = state.activeResultCount
            if (n > 0) {
              context.getString(
                R.string.hybrid_search_count_ayahs,
                HybridSearchAdapter.formatCountLocalized(n)
              ) to true
            } else {
              "" to false
            }
          }
          HybridSearchScope.SURAS -> {
            val n = state.activeResultCount
            if (n > 0) {
              context.getString(
                R.string.hybrid_search_count_suras,
                HybridSearchAdapter.formatCountLocalized(n)
              ) to true
            } else {
              "" to false
            }
          }
          HybridSearchScope.TAFSIR -> {
            val n = state.activeResultCount
            if (n > 0) {
              context.getString(
                R.string.hybrid_search_count_tafsir,
                HybridSearchAdapter.formatCountLocalized(n)
              ) to true
            } else {
              "" to false
            }
          }
        }
        hitCountBadge.text = countText
        hitCountBadge.visibility = if (hasCount) View.VISIBLE else View.GONE
      } else {
        hitCountBadge.visibility = View.GONE
      }
    }

    if (prevRecents != state.recentQueries) {
      updateRecentChips(state)
    }

    val rowsAffected = !hasSubmittedRows ||
      prevState.scope != state.scope ||
      prevState.query != state.query ||
      prevState.ayahHits !== state.ayahHits ||
      prevState.suraHits !== state.suraHits ||
      prevState.tafsirHits !== state.tafsirHits ||
      prevState.bookmarkedKeys != state.bookmarkedKeys ||
      prevState.isLoading != state.isLoading ||
      prevState.hasSearchedAyahs != state.hasSearchedAyahs ||
      prevState.hasSearchedTafsir != state.hasSearchedTafsir ||
      prevState.needsArabicDbWarning != state.needsArabicDbWarning ||
      prevState.noTranslationsWarning != state.noTranslationsWarning ||
      prevState.noTafsirInstalled != state.noTafsirInstalled

    if (rowsAffected) {
      hasSubmittedRows = true
      adapter.submitList(buildRows(state))
    }
  }

  private fun updateScopeChips(activeScope: HybridSearchScope) {
    val pillRadius = HybridViewTokens.dpF(context, 999f)
    val strokePx = HybridViewTokens.dp(context, 1f)
    scopeButtons.forEach { (scope, chip) ->
      val selected = scope == activeScope
      chip.isSelected = selected
      if (selected) {
        chip.setTextColor(palette.onCta)
        chip.background = HybridViewTokens.lightweightPressRoundedDrawable(
          fillColor = palette.ctaBackground,
          radiusPx = pillRadius,
          pressColor = palette.ripple
        )
        HybridViewTokens.applyZLayerFloatation(
          view = chip,
          zLevel = 1,
          isDark = palette.isDark,
          shadowTintColor = palette.ctaBackground,
          radiusPx = pillRadius
        )
      } else {
        chip.setTextColor(palette.textSecondary)
        chip.background = HybridViewTokens.lightweightPressRoundedDrawable(
          fillColor = palette.cardSurface,
          strokeColor = palette.cardHairlineBorder,
          strokeWidthPx = strokePx,
          radiusPx = pillRadius,
          pressColor = palette.ripple
        )
        HybridViewTokens.applyZLayerFloatation(
          view = chip,
          zLevel = 0,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = pillRadius
        )
      }
    }
  }

  private fun updateRecentChips(state: HybridSearchUiState) {
    val showRecents = state.recentQueries.isNotEmpty()
    recentsScroll.visibility = if (showRecents) View.VISIBLE else View.GONE
    recentsRow.removeAllViews()
    if (!showRecents) return

    val chipRadius = HybridViewTokens.dpF(context, 999f)
    val strokePx = HybridViewTokens.dp(context, 1f)
    val ph = HybridViewTokens.dp(context, 10f)
    val pv = HybridViewTokens.dp(context, 5f)
    val iconPx = HybridViewTokens.dp(context, 13f)
    val maxChipWidth = HybridViewTokens.dp(context, 220f)

    state.recentQueries.take(8).forEach { q ->
      val chip = LinearLayout(context).apply {
        id = R.id.recent_chip
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        contentDescription = "بحث سابق: $q"
        setPadding(ph, pv, ph, pv)
        background = HybridViewTokens.lightweightPressRoundedDrawable(
          fillColor = palette.cardSurface,
          strokeColor = palette.cardHairlineBorder,
          strokeWidthPx = strokePx,
          radiusPx = chipRadius,
          pressColor = palette.ripple
        )
        HybridViewTokens.applyZLayerFloatation(
          view = this,
          zLevel = 1,
          isDark = palette.isDark,
          shadowTintColor = palette.shadowTint,
          radiusPx = chipRadius
        )
        addView(
          ImageView(context).apply {
            setImageDrawable(
              HybridArchitecturalIcons.drawable(
                HybridArchitecturalIcons.CLOCK,
                palette.textSecondary,
                iconPx
              )
            )
          },
          LinearLayout.LayoutParams(iconPx, iconPx).apply { marginEnd = HybridViewTokens.dp(context, 5f) }
        )
        addView(
          TextView(context).apply {
            text = q
            setTextColor(palette.textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = maxChipWidth
          }
        )
        setOnClickListener {
          engine?.submitQuery(q)
        }
        HybridViewTokens.attachApplePressFeedback(this, AdaptiveMotionTokens.PRESS_SCALE)
      }
      recentsRow.addView(
        chip,
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = HybridViewTokens.dp(context, 6f) }
      )
    }

    val clearRecentsBtn = TextView(context).apply {
      text = context.getString(R.string.hybrid_hub_saved_clear_recents)
      setTextColor(palette.accent)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      setTypeface(Typeface.DEFAULT, Typeface.BOLD)
      isClickable = true
      isFocusable = true
      setPadding(ph, pv, ph, pv)
      background = HybridViewTokens.lightweightPressRoundedDrawable(
        fillColor = Color.TRANSPARENT,
        radiusPx = chipRadius,
        pressColor = palette.ripple
      )
      setOnClickListener { engine?.clearAllRecentQueries() }
      HybridViewTokens.attachApplePressFeedback(this, 0.965f)
    }
    recentsRow.addView(clearRecentsBtn)
  }

  override fun onDetachedFromWindow() {
    HybridViewTokens.resetApplePressFeedback(clearButton)
    scopeButtons.values.forEach { HybridViewTokens.resetApplePressFeedback(it) }
    super.onDetachedFromWindow()
  }

  private fun buildRows(state: HybridSearchUiState): List<HybridSearchRow> {
    val rows = ArrayList<HybridSearchRow>(64)

    if (state.needsArabicDbWarning && state.scope == HybridSearchScope.AYAHS) {
      rows.add(
        HybridSearchRow.ActionCard(
          key = "arabic_db_missing",
          message = context.getString(R.string.no_arabic_search_available),
          ctaLabel = context.getString(R.string.get_arabic_search_db),
          isTranslationCta = false
        )
      )
      return rows
    }

    if (state.noTranslationsWarning && state.scope == HybridSearchScope.AYAHS) {
      rows.add(
        HybridSearchRow.ActionCard(
          key = "no_translations",
          message = context.getString(R.string.need_translation),
          ctaLabel = context.getString(R.string.get_translations),
          isTranslationCta = true
        )
      )
      return rows
    }

    if (state.scope == HybridSearchScope.TAFSIR && state.noTafsirInstalled) {
      rows.add(HybridSearchRow.TafsirEmptyCard)
      return rows
    }

    if (state.query.isBlank() && state.scope != HybridSearchScope.SURAS) {
      rows.add(
        HybridSearchRow.EmptyMessageCard(
          key = "idle",
          title = context.getString(R.string.hybrid_search_empty_title),
          subtitle = context.getString(R.string.hybrid_search_empty_subtitle)
        )
      )
      return rows
    }

    when (state.scope) {
      HybridSearchScope.AYAHS -> {
        if (state.ayahHits.isEmpty() && !state.isLoading && state.hasSearchedAyahs) {
          lastAyahHitsRef = null
          lastBookmarkedKeysRef = null
          cachedAyahRows = emptyList()
          rows.add(
            HybridSearchRow.EmptyMessageCard(
              key = "no_ayahs",
              title = context.getString(R.string.hybrid_search_no_results, state.query),
              subtitle = null
            )
          )
        } else {
          return resolveCachedAyahRows(state.ayahHits, state.bookmarkedKeys)
        }
      }
      HybridSearchScope.SURAS -> {
        if (state.suraHits.isEmpty()) {
          rows.add(
            HybridSearchRow.EmptyMessageCard(
              key = "no_suras",
              title = context.getString(R.string.hybrid_search_no_results, state.query),
              subtitle = null
            )
          )
        } else {
          state.suraHits.forEach { hit ->
            rows.add(HybridSearchRow.SuraCard(hit))
          }
        }
      }
      HybridSearchScope.TAFSIR -> {
        if (state.tafsirHits.isEmpty() && state.hasSearchedTafsir && !state.isLoading) {
          rows.add(
            HybridSearchRow.EmptyMessageCard(
              key = "no_tafsir",
              title = context.getString(R.string.hybrid_search_no_results, state.query),
              subtitle = null
            )
          )
        } else {
          state.tafsirHits.forEach { hit ->
            val bookmarked = state.bookmarkedKeys.contains(ayahBookmarkKey(hit.sura, hit.ayah))
            rows.add(HybridSearchRow.TafsirCard(hit, bookmarked))
          }
        }
      }
    }

    return rows
  }

  private fun resolveCachedAyahRows(
    hits: List<AyahSearchHit>,
    bookmarkedKeys: Set<Long>
  ): List<HybridSearchRow.AyahCard> {
    if (hits.isEmpty()) {
      lastAyahHitsRef = hits
      lastBookmarkedKeysRef = bookmarkedKeys
      cachedAyahRows = emptyList()
      return emptyList()
    }
    val prevHits = lastAyahHitsRef
    val prevRows = cachedAyahRows
    if (prevHits === hits && lastBookmarkedKeysRef == bookmarkedKeys) {
      return prevRows
    }
    if (prevHits != null &&
      lastBookmarkedKeysRef == bookmarkedKeys &&
      hits.size > prevHits.size &&
      prevRows.size == prevHits.size &&
      prevHits.indices.all { idx -> hits[idx] === prevHits[idx] }
    ) {
      val combined = ArrayList<HybridSearchRow.AyahCard>(hits.size)
      combined.addAll(prevRows)
      for (idx in prevHits.size until hits.size) {
        val hit = hits[idx]
        val bookmarked = bookmarkedKeys.contains(ayahBookmarkKey(hit.sura, hit.ayah))
        combined.add(HybridSearchRow.AyahCard(hit, bookmarked))
      }
      lastAyahHitsRef = hits
      cachedAyahRows = combined
      return combined
    }
    val fresh = ArrayList<HybridSearchRow.AyahCard>(hits.size)
    for (idx in 0 until hits.size) {
      val hit = hits[idx]
      val bookmarked = bookmarkedKeys.contains(ayahBookmarkKey(hit.sura, hit.ayah))
      fresh.add(HybridSearchRow.AyahCard(hit, bookmarked))
    }
    lastAyahHitsRef = hits
    lastBookmarkedKeysRef = bookmarkedKeys
    cachedAyahRows = fresh
    return fresh
  }

  private fun showKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
  }

  private fun hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
  }
}
