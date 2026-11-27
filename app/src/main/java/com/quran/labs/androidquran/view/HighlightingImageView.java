package com.quran.labs.androidquran.view;

import static com.quran.data.model.highlight.HighlightType.Mode.BACKGROUND;
import static com.quran.data.model.highlight.HighlightType.Mode.COLOR;
import static com.quran.data.model.highlight.HighlightType.Mode.HIDE;
import static com.quran.data.model.highlight.HighlightType.Mode.HIGHLIGHT;
import static com.quran.data.model.highlight.HighlightType.Mode.UNDERLAY;
import static com.quran.data.model.highlight.HighlightType.Mode.UNDERLINE;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;
import androidx.core.view.DisplayCutoutCompat;

import com.quran.data.model.highlight.HighlightType;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.helpers.AyahHighlight;
import com.quran.labs.androidquran.ui.helpers.AyahHighlight.SingleAyahHighlight;
import com.quran.labs.androidquran.ui.helpers.AyahHighlight.TransitionAyahHighlight;
import com.quran.labs.androidquran.ui.helpers.HighlightAnimationConfig;
import com.quran.labs.androidquran.ui.helpers.HighlightTypes;
import com.quran.page.common.data.AyahBounds;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;
import com.quran.page.common.draw.ImageDrawHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import android.animation.AnimatorListenerAdapter;
import android.view.animation.LinearInterpolator;

import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomHighlightsDrawer;
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomManager;
import com.quran.labs.androidquran.kidsclassroom.KidsClassroomUiState;
import com.quran.labs.androidquran.memorization.AyahGlyphSynthesizer;
import com.quran.labs.androidquran.memorization.FadingWord;
import com.quran.labs.androidquran.memorization.MemorizationHighlightsDrawer;
import com.quran.labs.androidquran.memorization.MemorizationUiState;
import com.quran.labs.androidquran.memorization.QuranMemorizationManager;
import com.quran.labs.androidquran.memorization.RevealMode;
import com.quran.labs.androidquran.memorization.RevealTransition;
import com.quran.page.common.data.coordinates.PageGlyphsCoords;

import java.util.Collections;

import dev.chrisbanes.insetter.Insetter;
import kotlin.Unit;

public class HighlightingImageView extends AppCompatImageView
    implements QuranMemorizationManager.Listener, KidsClassroomManager.Listener {
  // for debugging / visualizing glyph bounds:
  // when enabled, will draw bounds around each glyph to visualize the glyph bounds
  private static final boolean DEBUG_BOUNDS = false;

  private static int overlayTextColor = -1;
  private static int headerFooterSize;
  private static int headerFooterFontSize;
  private static int scrollableHeaderFooterSize;
  private static int scrollableHeaderFooterFontSize;
  private static int dualPageHeaderFooterSize;
  private static int dualPageHeaderFooterFontSize;

  // Sorted map so we use highest priority highlighting when iterating
  private final SortedMap<HighlightType, Set<AyahHighlight>> currentHighlights = new TreeMap<>();

  private boolean isNightMode;
  private boolean isColorFilterOn;
  private int nightModeTextBrightness = Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
  private int nightModeBackgroundBrightness = Constants.DEFAULT_NIGHT_MODE_BACKGROUND_BRIGHTNESS;

  // Params for drawing text
  private int fontSize;
  private OverlayParams overlayParams = null;
  private RectF pageBounds = null;
  private boolean didDraw = false;
  private PageCoordinates pageCoordinates;
  private AyahCoordinates ayahCoordinates;
  private Map<AyahHighlight, List<AyahBounds>> highlightCoordinates;
  private Set<ImageDrawHelper> imageDrawHelpers;
  private ValueAnimator animator;

  private int topSafeOffset = 0;
  private int bottomSafeOffset = 0;
  private int horizontalSafeOffset = 0;
  private int verticalOffsetForScrolling = 0;

  private final ImageDrawHelper underlayHighlightsDrawer = new HighlightsDrawer(
      () -> ayahCoordinates,
      () -> highlightCoordinates,
      () -> currentHighlights,
      c -> { super.onDraw(c); return Unit.INSTANCE; },
      () -> isNightMode,
      UNDERLAY, UNDERLINE
  );

  // Draws highlights that need to run before the page image is drawn (to apply clippings)
  private final ImageDrawHelper clippingHighlightsDrawer = new HighlightsDrawer(
      () -> ayahCoordinates,
      () -> highlightCoordinates,
      () -> currentHighlights,
      c -> { super.onDraw(c); return Unit.INSTANCE; },
      () -> isNightMode,
      COLOR, HIDE
  );

  // Draws remaining highlights that need to run after the page image is drawn
  private final ImageDrawHelper highlightsDrawer = new HighlightsDrawer(
      () -> ayahCoordinates,
      () -> highlightCoordinates,
      () -> currentHighlights,
      c -> { super.onDraw(c); return null; },
      () -> isNightMode,
      HIGHLIGHT, BACKGROUND
  );

  public static final long WORD_REVEAL_DURATION_MS = 320L;
  public static final long WORD_WINDOW_FADE_OUT_DURATION_MS = 520L;

  private static final class ActiveWordAnim {
    final SuraAyah suraAyah;
    final int wordPos;
    final float startFraction;
    final float targetFraction;
    final long durationMs;

    ActiveWordAnim(
        SuraAyah suraAyah,
        int wordPos,
        float startFraction,
        float targetFraction,
        long durationMs
    ) {
      this.suraAyah = suraAyah;
      this.wordPos = wordPos;
      this.startFraction = startFraction;
      this.targetFraction = targetFraction;
      this.durationMs = durationMs;
    }
  }

  private final Map<String, Float> wordAnimationFractions = new HashMap<>();
  private final Map<String, ActiveWordAnim> activeWordAnimations = new HashMap<>();
  private ValueAnimator revealAnimator;
  private long currentAnimatorDurationMs = 0L;
  private long currentAnimatorElapsedMs = 0L;
  private boolean isRestartingRevealAnimator = false;
  private long lastHandledTransitionId = -1L;

  private final MemorizationHighlightsDrawer memorizationHighlightsDrawer =
      new MemorizationHighlightsDrawer(
          () -> ayahCoordinates,
          c -> { super.onDraw(c); return Unit.INSTANCE; },
          () -> isNightMode,
          () -> nightModeTextBrightness,
          () -> nightModeBackgroundBrightness,
          () -> wordAnimationFractions
      );

  private final KidsClassroomHighlightsDrawer kidsClassroomHighlightsDrawer =
      new KidsClassroomHighlightsDrawer(
          () -> ayahCoordinates,
          () -> isNightMode
      );

  private final ImageDrawHelper glyphBoundsDebuggingDrawer = DEBUG_BOUNDS ?
      new GlyphBoundsDebuggingDrawer(() -> ayahCoordinates) : null;

  public HighlightingImageView(Context context) {
    this(context, null);
  }

  public HighlightingImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    if (overlayTextColor == -1) {
      final Resources res = context.getResources();
      overlayTextColor = ContextCompat.getColor(context, R.color.overlay_text_color);
      headerFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size);
      scrollableHeaderFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size_scrollable);
      dualPageHeaderFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size_dualPage);
      headerFooterFontSize = res.getDimensionPixelSize(R.dimen.page_overlay_font_size);
      scrollableHeaderFooterFontSize =
          res.getDimensionPixelSize(R.dimen.page_overlay_font_size_scrollable);
      dualPageHeaderFooterFontSize =
          res.getDimensionPixelSize(R.dimen.page_overlay_font_size_dualPage);
    }

    Insetter.builder()
        .setOnApplyInsetsListener((view, insets, initialState) -> {
          final DisplayCutoutCompat cutout = insets.getDisplayCutout();
          if (cutout != null) {
            topSafeOffset = cutout.getSafeInsetTop();
            bottomSafeOffset = cutout.getSafeInsetBottom();
            horizontalSafeOffset = Math.max(cutout.getSafeInsetLeft(), cutout.getSafeInsetRight());
            setPadding(horizontalSafeOffset,
                topSafeOffset + verticalOffsetForScrolling,
                horizontalSafeOffset,
                bottomSafeOffset + verticalOffsetForScrolling);
          }
        })
        .applyToView(this);
    QuranMemorizationManager.addListener(this);
    KidsClassroomManager.addListener(this);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    QuranMemorizationManager.addListener(this);
    KidsClassroomManager.addListener(this);
  }

  @Override
  protected void onDetachedFromWindow() {
    QuranMemorizationManager.removeListener(this);
    KidsClassroomManager.removeListener(this);
    clearAllWordAnimations();
    super.onDetachedFromWindow();
  }

  @Override
  public void onKidsClassroomStateChanged(@NonNull KidsClassroomUiState state) {
    postInvalidateOnAnimation();
  }

  private void clearAllWordAnimations() {
    isRestartingRevealAnimator = false;
    if (revealAnimator != null) {
      revealAnimator.cancel();
      revealAnimator = null;
    }
    activeWordAnimations.clear();
    wordAnimationFractions.clear();
    currentAnimatorDurationMs = 0L;
    currentAnimatorElapsedMs = 0L;
  }

  @Override
  public void onMemorizationStateChanged(@NonNull MemorizationUiState state) {
    if (!state.isHiddenModeEnabled() ||
        (state.getLastTransition() == null &&
            (state.getPageProgress().getRevealedWords() == 0 ||
                state.getRevealMode().isSingleActiveWordMode()))) {
      clearAllWordAnimations();
      invalidate();
      return;
    }

    final RevealTransition transition = state.getLastTransition();
    final int currentPageNum = pageCoordinates != null ? pageCoordinates.getPage() :
        (ayahCoordinates != null ? ayahCoordinates.getPage() : -1);
    if (transition != null &&
        transition.getId() != lastHandledTransitionId &&
        transition.getPage() == currentPageNum) {
      lastHandledTransitionId = transition.getId();
      startRevealColorTransition(transition, state.getRevealMode());
    } else {
      invalidate();
    }
  }

  private void startRevealColorTransition(
      @NonNull RevealTransition transition,
      @NonNull RevealMode revealMode
  ) {
    final long previousElapsedMs = currentAnimatorElapsedMs;
    if (revealAnimator != null && revealAnimator.isRunning()) {
      isRestartingRevealAnimator = true;
      revealAnimator.cancel();
      isRestartingRevealAnimator = false;
    }

    final int pageNum = transition.getPage();
    final Set<String> fadingOutKeys = new HashSet<>();
    for (FadingWord fadingWord : transition.getFadingOutWords()) {
      fadingOutKeys.add(fadingWord.key());
    }

    // Carry over still-running animations when appropriate; prune any stale ones
    final Map<String, ActiveWordAnim> carriedAnimations = new HashMap<>();
    final Map<String, Float> carriedStartFractions = new HashMap<>();

    for (Map.Entry<String, ActiveWordAnim> entry : activeWordAnimations.entrySet()) {
      final String key = entry.getKey();
      final ActiveWordAnim prev = entry.getValue();
      final Float currentFractionObj = wordAnimationFractions.get(key);
      final float currentFraction = currentFractionObj != null ? currentFractionObj : prev.targetFraction;
      final long remainingMs = prev.durationMs - previousElapsedMs;

      if (fadingOutKeys.contains(key)) {
        // Was mid-reveal and just got pushed out of the Word Window: remember its current fraction
        carriedStartFractions.put(key, currentFraction);
        continue;
      }

      if (prev.targetFraction <= 0f) {
        // Ongoing Word Window fade-out from an earlier tap: continue smoothly if still visible
        if (revealMode == RevealMode.WORD_WINDOW && remainingMs > 16L && currentFraction > 0.02f) {
          carriedAnimations.put(
              key,
              new ActiveWordAnim(prev.suraAyah, prev.wordPos, currentFraction, 0f, remainingMs)
          );
        }
      } else {
        // Ongoing fade-in from an earlier tap: keep ONLY if the word is still revealed
        final boolean stillRevealed =
            QuranMemorizationManager.INSTANCE.isWordRevealed(pageNum, prev.suraAyah, prev.wordPos);
        if (stillRevealed && remainingMs > 16L && currentFraction < 0.98f) {
          carriedAnimations.put(
              key,
              new ActiveWordAnim(prev.suraAyah, prev.wordPos, currentFraction, 1f, remainingMs)
          );
        }
      }
    }

    activeWordAnimations.clear();
    wordAnimationFractions.clear();
    activeWordAnimations.putAll(carriedAnimations);

    // 1. Register newly fading-out words (Word Window removing words smoothly: 1.0f -> 0.0f)
    for (FadingWord fadingWord : transition.getFadingOutWords()) {
      final String key = fadingWord.key();
      final Float startFromCarried = carriedStartFractions.get(key);
      final float startFraction = startFromCarried != null ?
          Math.max(0.05f, Math.min(1f, startFromCarried)) : 1f;
      activeWordAnimations.put(
          key,
          new ActiveWordAnim(
              fadingWord.getSuraAyah(),
              fadingWord.getWordPosition(),
              startFraction,
              0f,
              WORD_WINDOW_FADE_OUT_DURATION_MS
          )
      );
    }

    // 2. Register newly revealed words (0.0f -> 1.0f)
    final SuraAyah targetAyah = transition.getSuraAyah();
    for (Integer wordPos : transition.getWordPositions()) {
      final String key = targetAyah.sura + ":" + targetAyah.ayah + ":" + wordPos;
      activeWordAnimations.put(
          key,
          new ActiveWordAnim(
              targetAyah,
              wordPos,
              0f,
              1f,
              WORD_REVEAL_DURATION_MS
          )
      );
    }

    long maxDurationMs = 0L;
    for (ActiveWordAnim anim : activeWordAnimations.values()) {
      if (anim.durationMs > maxDurationMs) {
        maxDurationMs = anim.durationMs;
      }
    }

    currentAnimatorDurationMs = maxDurationMs;
    currentAnimatorElapsedMs = 0L;
    applyRevealAnimationAtElapsedMs(0L);
    invalidate();

    if (maxDurationMs <= 0L || activeWordAnimations.isEmpty()) {
      return;
    }

    final long totalDurationMs = maxDurationMs;
    revealAnimator = ValueAnimator.ofFloat(0f, 1f);
    revealAnimator.setDuration(totalDurationMs);
    revealAnimator.setInterpolator(new LinearInterpolator());
    revealAnimator.addUpdateListener(anim -> {
      final float rawFraction = (Float) anim.getAnimatedValue();
      final long elapsedMs = Math.round(rawFraction * totalDurationMs);
      currentAnimatorElapsedMs = elapsedMs;
      applyRevealAnimationAtElapsedMs(elapsedMs);
      invalidate();
    });
    revealAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator animation) {
        if (!isRestartingRevealAnimator) {
          activeWordAnimations.clear();
          wordAnimationFractions.clear();
          currentAnimatorDurationMs = 0L;
          currentAnimatorElapsedMs = 0L;
          invalidate();
        }
      }

      @Override
      public void onAnimationCancel(Animator animation) {
        if (!isRestartingRevealAnimator) {
          activeWordAnimations.clear();
          wordAnimationFractions.clear();
          currentAnimatorDurationMs = 0L;
          currentAnimatorElapsedMs = 0L;
          invalidate();
        }
      }
    });
    revealAnimator.start();
  }

  private void applyRevealAnimationAtElapsedMs(long elapsedMs) {
    final List<String> finishedKeys = new ArrayList<>();
    for (Map.Entry<String, ActiveWordAnim> entry : activeWordAnimations.entrySet()) {
      final String key = entry.getKey();
      final ActiveWordAnim spec = entry.getValue();
      if (spec.durationMs <= 0L || elapsedMs >= spec.durationMs) {
        finishedKeys.add(key);
      } else {
        final float rawT = Math.min(1f, Math.max(0f, (float) elapsedMs / (float) spec.durationMs));
        final float easedT = 1f - (1f - rawT) * (1f - rawT);
        final float current = spec.startFraction + (spec.targetFraction - spec.startFraction) * easedT;
        wordAnimationFractions.put(key, current);
      }
    }
    for (String finishedKey : finishedKeys) {
      activeWordAnimations.remove(finishedKey);
      wordAnimationFractions.remove(finishedKey);
    }
  }

  public void advanceRevealAnimationForTest(long elapsedMs) {
    if (revealAnimator != null && revealAnimator.isRunning()) {
      isRestartingRevealAnimator = true;
      revealAnimator.cancel();
      isRestartingRevealAnimator = false;
      revealAnimator = null;
    }
    currentAnimatorElapsedMs = Math.max(0L, elapsedMs);
    applyRevealAnimationAtElapsedMs(currentAnimatorElapsedMs);
    invalidate();
  }

  public Map<String, Float> getWordAnimationFractions() {
    return Collections.unmodifiableMap(wordAnimationFractions);
  }

  public AyahCoordinates getAyahCoordinatesData() {
    return ayahCoordinates;
  }

  public void setIsScrollable(boolean scrollable, boolean landscape) {
    int topBottom = scrollable ? scrollableHeaderFooterSize :
        landscape ? dualPageHeaderFooterSize : headerFooterSize;
    verticalOffsetForScrolling = topBottom;
    setPadding(horizontalSafeOffset,
        topBottom + topSafeOffset,
        horizontalSafeOffset,
        topBottom + bottomSafeOffset);
    fontSize = scrollable ? scrollableHeaderFooterFontSize :
        landscape ? dualPageHeaderFooterFontSize : headerFooterFontSize;
  }

  public void unHighlight(int surah, int ayah, HighlightType type) {
    unHighlight(surah, ayah, -1, type);
  }

  public void unHighlight(int surah, int ayah, int word, HighlightType type) {
    Set<AyahHighlight> highlights = currentHighlights.get(type);
    if (highlights != null && highlights.remove(new SingleAyahHighlight(surah, ayah, word))) {
      invalidate();
    }
  }

  public void highlightAyat(Set<String> ayahKeys, HighlightType type) {
    Set<AyahHighlight> highlights = currentHighlights.get(type);
    if (highlights == null) {
      highlights = new HashSet<>();
      currentHighlights.put(type, highlights);
    }
    highlights.addAll(SingleAyahHighlight.createSet(ayahKeys));
  }

  public void unHighlight(HighlightType type) {
    if (!currentHighlights.isEmpty()) {
      currentHighlights.remove(type);
      if (type.isTransitionAnimated()) {
        //stop animation here
        if (animator != null) {
          // this check is essential because
          // if playing first time and stopping
          // before animation is setup
          animator.cancel();
        }
      }
      invalidate();
    }
  }

  public void setPageData(PageCoordinates pageCoordinates, Set<ImageDrawHelper> imageDrawHelpers) {
    this.imageDrawHelpers = imageDrawHelpers;
    this.pageCoordinates = pageCoordinates;
    invalidate();
  }

  public void setAyahData(AyahCoordinates ayahCoordinates) {
    QuranMemorizationManager.INSTANCE.ensurePreferencesLoaded(getContext());
    QuranMemorizationManager.INSTANCE.ensureWordAlignmentLoaded(getContext());
    PageGlyphsCoords glyphCoords = ayahCoordinates.getGlyphCoordinates();
    if (glyphCoords == null && !ayahCoordinates.getAyahCoordinates().isEmpty()) {
      glyphCoords = AyahGlyphSynthesizer.synthesize(
          ayahCoordinates.getPage(),
          ayahCoordinates.getAyahCoordinates()
      );
      if (glyphCoords != null) {
        ayahCoordinates = new AyahCoordinates(
            ayahCoordinates.getPage(),
            ayahCoordinates.getAyahCoordinates(),
            glyphCoords,
            ayahCoordinates.getAyahLineCoordinates(),
            ayahCoordinates.getUnderlineRaiseRatio()
        );
      }
    }
    this.ayahCoordinates = ayahCoordinates;
    if (this.pageCoordinates == null) {
      this.pageCoordinates = new PageCoordinates(
          ayahCoordinates.getPage(),
          new RectF(),
          Collections.emptyList(),
          Collections.emptyList()
      );
    }
    QuranMemorizationManager.INSTANCE.registerPageGlyphs(
        ayahCoordinates.getPage(),
        glyphCoords,
        ayahCoordinates.getAyahCoordinates().keySet()
    );
    highlightCoordinates = new HashMap<>();
    for (Map.Entry<String, List<AyahBounds>> entry: ayahCoordinates.getAyahCoordinates().entrySet()) {
      highlightCoordinates.put(new SingleAyahHighlight(entry.getKey()), entry.getValue());
    }
    invalidate();
  }

  public void setNightMode(boolean isNightMode, int textBrightness, int backgroundBrightness) {
    this.isNightMode = isNightMode;
    if (isNightMode) {
      nightModeTextBrightness = textBrightness;
      nightModeBackgroundBrightness = backgroundBrightness;
      // we need a new color filter now
      isColorFilterOn = false;
    }
    // Re-init overlay params color based on the new night mode setting
    initOverlayParamsColor();
    adjustNightMode();
  }

  class AnimationUpdateListener implements ValueAnimator.AnimatorUpdateListener, Animator.AnimatorListener {
    /*
    This is an inner class because it needs access to invalidate()
     */
    Set<AyahHighlight> highlights;
    TransitionAyahHighlight transitionHighlight;

    public AnimationUpdateListener(Set<AyahHighlight> highlights, TransitionAyahHighlight transitionHighlight) {
      this.highlights = highlights;
      this.transitionHighlight = transitionHighlight;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
      List<AyahBounds> value = (List<AyahBounds>) animation.getAnimatedValue();
      highlightCoordinates.put(transitionHighlight, value);
      invalidate();
    }

    @Override
    public void onAnimationStart(Animator animation) {

    }

    @Override
    public void onAnimationEnd(Animator animation) {
      highlightCoordinates.remove(transitionHighlight);
      highlights.remove(transitionHighlight);
      highlights.add(transitionHighlight.getDestination());
    }

    @Override
    public void onAnimationCancel(Animator animation) {
      highlightCoordinates.remove(transitionHighlight);
      highlights.remove(transitionHighlight);
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }
  }

  private void highlightFloatableAyah(Set<AyahHighlight> highlights, AyahHighlight destinationHighlight, HighlightAnimationConfig config) {
    AyahHighlight previousHighlight = highlights.iterator().next();
    AyahHighlight sourceHighlight;

    List<AyahBounds> startingBounds;
    if (previousHighlight.isTransition()) {
      // The ayah changed during animating
      startingBounds = (List<AyahBounds>)animator.getAnimatedValue();
      animator.cancel();
      sourceHighlight = ((TransitionAyahHighlight)previousHighlight).getSource();
    } else {
      sourceHighlight = previousHighlight;
      startingBounds = highlightCoordinates.get(sourceHighlight);
    }

    final TransitionAyahHighlight transitionHighlight = new TransitionAyahHighlight(sourceHighlight, destinationHighlight);

    if (startingBounds == null) {
      startingBounds = new ArrayList<>();
    }

    // yes we make copies, because normalizing the bounds will change them
    List<AyahBounds> sourceBounds = new ArrayList<>(startingBounds);

    List<AyahBounds> destinationBounds = new ArrayList<>();
    final List<AyahBounds> source = highlightCoordinates.get(destinationHighlight);
    if (source != null) {
      destinationBounds.addAll(source);
    }

    highlights.clear();
    highlights.add(transitionHighlight);

    animator = ValueAnimator.ofObject(config.getTypeEvaluator(), sourceBounds, destinationBounds);

    animator.setDuration(config.getDuration());

    AnimationUpdateListener listener = new AnimationUpdateListener(highlights, transitionHighlight);
    animator.addUpdateListener(listener);
    animator.addListener(listener);
    animator.setInterpolator(config.getInterpolator());
    animator.start();
  }

  private boolean shouldFloatHighlight(Set<AyahHighlight> highlights, HighlightType type, AyahHighlight destinationHighlight) {
    // only animating AUDIO highlights, for now
    if (!type.isTransitionAnimated()) {
      return false;
    }

    // can only animate from one ayah to another, for now
    if (highlights.size() != 1 || highlightCoordinates == null) {
      return false;
    }

    AyahHighlight previousAyahHighlight = highlights.iterator().next();

    if (destinationHighlight.equals(previousAyahHighlight)) {
      // can't animate to the same location
      return false;
    }

    List<AyahBounds> startingBounds;
    if (previousAyahHighlight.isTransition()) {
      if (animator == null) {
        return false;
      }
      startingBounds = (List<AyahBounds>) animator.getAnimatedValue();
    } else {
      startingBounds = highlightCoordinates.get(previousAyahHighlight);
    }

    List<AyahBounds> destinationBounds = highlightCoordinates.get(destinationHighlight);
    // normalization needs rectangles on both sides, including when retargeting a transition.
    return startingBounds != null && !startingBounds.isEmpty()
        && destinationBounds != null && !destinationBounds.isEmpty();
  }

  public void highlightAyah(int surah, int ayah, int word, HighlightType type) {
    final Set<AyahHighlight> highlights = currentHighlights.get(type);
    final SingleAyahHighlight singleAyahHighlight = new SingleAyahHighlight(surah, ayah, word);
    if (highlights == null) {
      final Set<AyahHighlight> updatedHighlights = new HashSet<>();
      updatedHighlights.add(singleAyahHighlight);
      currentHighlights.put(type, updatedHighlights);
    } else if (type.isSingle()) {
      // If multiple highlighting not allowed (e.g. audio)
      // clear all others of this type first
      // only if highlight type is floatable
      if (shouldFloatHighlight(highlights, type, singleAyahHighlight)) {
        highlightFloatableAyah(highlights, singleAyahHighlight, HighlightTypes.INSTANCE.getAnimationConfig(type));
      } else {
        // cancel before replacing highlights, since its callbacks modify this set.
        if (type.isTransitionAnimated() && animator != null) {
          animator.cancel();
        }
        highlights.clear();
        highlights.add(singleAyahHighlight);
      }
    } else {
      highlights.add(singleAyahHighlight);
      currentHighlights.put(type, highlights);
    }
  }

  @Override
  public void setImageDrawable(Drawable bitmap) {
    // clear the color filter before setting the image
    clearColorFilter();
    // this allows the filter to be enabled again if needed
    isColorFilterOn = false;

    super.setImageDrawable(bitmap);
    if (bitmap != null) {
      adjustNightMode();
    }
  }

  public void adjustNightMode() {
    if (isNightMode && !isColorFilterOn) {
      float[] matrix =
          NightModeColorMatrix.create(nightModeTextBrightness, nightModeBackgroundBrightness);
      setColorFilter(new ColorMatrixColorFilter(matrix));
      isColorFilterOn = true;
    } else if (!isNightMode) {
      clearColorFilter();
      isColorFilterOn = false;
    }

    invalidate();
  }

  private static class OverlayParams {
    boolean init = false;
    Paint paint = null;
    float offsetX;
    float topBaseline;
    float bottomBaseline;
    String suraText = null;
    String juzText = null;
    String pageText = null;
    String rub3Text = null;
    String manzilText = null;
  }

  public void setOverlayText(String suraText, String juzText, String pageText, String rub3Text, String manzilText) {
    // Calculate page bounding rect from ayahinfo db
    if (pageBounds == null) {
      return;
    }

    overlayParams = new OverlayParams();
    overlayParams.suraText = suraText;
    overlayParams.juzText = juzText;
    overlayParams.pageText = pageText;
    overlayParams.rub3Text = rub3Text;
    overlayParams.manzilText = manzilText;
    overlayParams.paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
    overlayParams.paint.setTextSize(fontSize);
//    if (juzText.contains("ج")) {
//      // change typeface for Arabic
//      overlayParams.paint.setTypeface(TypefaceManager.getHeaderFooterTypeface(context));
//    }
    if (!didDraw) {
      invalidate();
    }
  }

  public void setPageBounds(RectF rect) {
    pageBounds = rect;
  }

  private boolean initOverlayParams(Matrix matrix) {
    if (overlayParams == null || pageBounds == null) {
      return false;
    }

    // Overlay params previously initiated; skip
    if (overlayParams.init) {
      return true;
    }

    initOverlayParamsColor();

    // Use font metrics to calculate the maximum possible height of the text
    FontMetrics fm = overlayParams.paint.getFontMetrics();

    final RectF mappedRect = new RectF();
    matrix.mapRect(mappedRect, pageBounds);

    // Calculate where the text's baseline should be
    // (for top text and bottom text)
    // (p.s. parts of the glyphs will be below the baseline such as a
    // 'y' or 'ي')
    overlayParams.topBaseline = -fm.top;
    overlayParams.bottomBaseline = getHeight() - fm.bottom;

    // Calculate the horizontal margins off the edge of screen
    overlayParams.offsetX = Math.min(
        mappedRect.left, getWidth() - mappedRect.right);

    overlayParams.init = true;
    return true;
  }

  private void initOverlayParamsColor() {
    if (overlayParams == null) {
      return;
    }
    int overlayColor = overlayTextColor;
    if (isNightMode) {
      overlayColor = Color.rgb(nightModeTextBrightness,
          nightModeTextBrightness, nightModeTextBrightness);
    }
    overlayParams.paint.setColor(overlayColor);
  }

  private void overlayText(Canvas canvas, Matrix matrix) {
    if (overlayParams == null || !initOverlayParams(matrix)) {
      return;
    }

    overlayParams.paint.setTextAlign(Align.LEFT);
    canvas.drawText(overlayParams.suraText,
        overlayParams.offsetX + horizontalSafeOffset,
        overlayParams.topBaseline + topSafeOffset,
        overlayParams.paint);
    overlayParams.paint.setTextAlign(Align.CENTER);
    canvas.drawText(overlayParams.pageText,
        getWidth() / 2.0f,
        overlayParams.bottomBaseline - bottomSafeOffset,
        overlayParams.paint);
    // Merge the current rub3 text with the juz' text
    overlayParams.paint.setTextAlign(Align.RIGHT);
    canvas.drawText(overlayParams.juzText + overlayParams.rub3Text + overlayParams.manzilText,
        (getWidth() - overlayParams.offsetX) - horizontalSafeOffset,
        overlayParams.topBaseline + topSafeOffset,
        overlayParams.paint);
    didDraw = true;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (overlayParams != null) {
      overlayParams.init = false;
    }
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    final Drawable d = getDrawable();
    if (d == null) {
      // no image, forget it.
      return;
    }

    // draw the highlights that go under the page image first
    if (pageCoordinates != null) {
      underlayHighlightsDrawer.draw(pageCoordinates, canvas, this);
    }

    // Save the canvas before applying any clippings
    canvas.save();

    // Draw highlights that involve clipping the canvas (HIDE, COLOR)
    // Note: this must be done before the super.onDraw call so that HighlightsDrawer has a chance
    //       to apply canvas clippings (e.g. hiding) before the image is drawn
    if (pageCoordinates != null) {
      memorizationHighlightsDrawer.draw(pageCoordinates, canvas, this);
      clippingHighlightsDrawer.draw(pageCoordinates, canvas, this);
    }

    // Draw the page image (excluding clipped out sections)
    super.onDraw(canvas);

    // Restore the canvas to remove any clippings so the remaining highlights/drawers don't get clipped
    canvas.restore();

    // Draw remaining highlights (other than HIDE, COLOR)
    if (pageCoordinates != null) {
      highlightsDrawer.draw(pageCoordinates, canvas, this);
      kidsClassroomHighlightsDrawer.draw(pageCoordinates, canvas, this);
    }

    // Draw overlay text
    didDraw = false;
    if (overlayParams != null) {
      overlayText(canvas, getImageMatrix());
    }

    // run additional image draw helpers if any
    if (imageDrawHelpers != null && pageCoordinates != null) {
      for (ImageDrawHelper imageDrawHelper : imageDrawHelpers) {
        imageDrawHelper.draw(pageCoordinates, canvas, this);
      }
    }

    // for debugging
    if (DEBUG_BOUNDS && pageCoordinates != null) {
      glyphBoundsDebuggingDrawer.draw(pageCoordinates, canvas, this);
    }
  }
}
