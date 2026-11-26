package com.quran.labs.androidquran.hybrid.reader

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class HybridReaderGeometryAndFormattingTest {

  private lateinit var originalLocale: Locale

  @Before
  fun setUp() {
    originalLocale = Locale.getDefault()
  }

  @After
  fun tearDown() {
    Locale.setDefault(originalLocale)
  }

  @Test
  fun `middle ayah places floating bar cleanly below lastLineBottom with zero sacred text overlap`() {
    val bounds = AyahOverlayBounds(
      firstLineLeft = 200f,
      firstLineTop = 500f,
      firstLineRight = 540f,
      firstLineBottom = 560f,
      lastLineLeft = 180f,
      lastLineTop = 580f,
      lastLineRight = 440f,
      lastLineBottom = 640f
    )

    val placement = computeFloatingAyahBarPlacement(
      bounds = bounds,
      viewportWidthPx = 720f,
      viewportHeightPx = 1604f,
      topObstacleBottomPx = 150f,
      bottomObstacleTopPx = 1400f,
      pillHorizontalInsetPx = 36f,
      caretHeightPx = 14f,
      pillHeightPx = 104f,
      selectionTagHeightPx = 56f,
      gapFromAyahPx = 12f,
      caretHorizontalPaddingPx = 52f
    )

    assertThat(placement.direction).isEqualTo(AyahBarPlacementDirection.BELOW_AYAH)
    assertThat(placement.topPx).isEqualTo(652f)
    assertWithMessage("Bar top must remain strictly below ayah last line bottom")
      .that(placement.topPx)
      .isGreaterThan(bounds.lastLineBottom)
    assertThat(placement.caretCenterXPx).isWithin(0.1f).of(310f)
  }

  @Test
  fun `bottom ayah near audio capsule flips floating bar cleanly above firstLineTop`() {
    val bounds = AyahOverlayBounds(
      firstLineLeft = 180f,
      firstLineTop = 1200f,
      firstLineRight = 560f,
      firstLineBottom = 1260f,
      lastLineLeft = 220f,
      lastLineTop = 1270f,
      lastLineRight = 500f,
      lastLineBottom = 1330f
    )

    val totalHeight = 14f + 104f + 56f
    val placement = computeFloatingAyahBarPlacement(
      bounds = bounds,
      viewportWidthPx = 720f,
      viewportHeightPx = 1604f,
      topObstacleBottomPx = 150f,
      bottomObstacleTopPx = 1400f,
      pillHorizontalInsetPx = 36f,
      caretHeightPx = 14f,
      pillHeightPx = 104f,
      selectionTagHeightPx = 56f,
      gapFromAyahPx = 12f,
      caretHorizontalPaddingPx = 52f
    )

    assertThat(placement.direction).isEqualTo(AyahBarPlacementDirection.ABOVE_AYAH)
    val barBottom = placement.topPx + totalHeight
    assertWithMessage("Flipped bar bottom must sit strictly above ayah first line top")
      .that(barBottom)
      .isLessThan(bounds.firstLineTop)
    assertThat(placement.topPx).isAtLeast(150f)
  }

  @Test
  fun `top ayah near top pills places bar below ayah and clamps caret inside pill bounds`() {
    val bounds = AyahOverlayBounds(
      firstLineLeft = 10f,
      firstLineTop = 160f,
      firstLineRight = 40f,
      firstLineBottom = 210f,
      lastLineLeft = 10f,
      lastLineTop = 210f,
      lastLineRight = 40f,
      lastLineBottom = 260f
    )

    val placement = computeFloatingAyahBarPlacement(
      bounds = bounds,
      viewportWidthPx = 720f,
      viewportHeightPx = 1604f,
      topObstacleBottomPx = 150f,
      bottomObstacleTopPx = 1400f,
      pillHorizontalInsetPx = 36f,
      caretHeightPx = 14f,
      pillHeightPx = 104f,
      selectionTagHeightPx = 56f,
      gapFromAyahPx = 12f,
      caretHorizontalPaddingPx = 52f
    )

    assertThat(placement.direction).isEqualTo(AyahBarPlacementDirection.BELOW_AYAH)
    assertThat(placement.topPx).isEqualTo(272f)
    // Anchor centerX = 25f < minCaretX (36 + 52 = 88) -> clamped cleanly inside pill corner
    assertThat(placement.caretCenterXPx).isEqualTo(88f)

    val farRightBounds = bounds.copy(lastLineLeft = 690f, lastLineRight = 715f)
    val rightPlacement = computeFloatingAyahBarPlacement(
      bounds = farRightBounds,
      viewportWidthPx = 720f,
      viewportHeightPx = 1604f,
      topObstacleBottomPx = 150f,
      bottomObstacleTopPx = 1400f,
      pillHorizontalInsetPx = 36f,
      caretHeightPx = 14f,
      pillHeightPx = 104f,
      selectionTagHeightPx = 56f,
      gapFromAyahPx = 12f,
      caretHorizontalPaddingPx = 52f
    )
    // maxCaretX = 720 - 36 - 52 = 632
    assertThat(rightPlacement.caretCenterXPx).isEqualTo(632f)
  }

  @Test
  fun `computeStudyNudgeTranslationY shifts Mushaf up cleanly when drawer opens and resets on close`() {
    assertThat(
      computeStudyNudgeTranslationY(
        isStudyDrawerOpen = false,
        drawerTopPx = 800f,
        selectedAyahBottomPx = 700f,
        defaultNudgePx = 96f,
        maxNudgePx = 500f
      )
    ).isEqualTo(0f)

    // Upper ayah needs only default nudge
    assertThat(
      computeStudyNudgeTranslationY(
        isStudyDrawerOpen = true,
        drawerTopPx = 800f,
        selectedAyahBottomPx = 650f,
        defaultNudgePx = 96f,
        maxNudgePx = 500f
      )
    ).isEqualTo(-96f)

    // Lower ayah below drawerTopPx nudges further up so it stays visible above drawer
    val deepNudge = computeStudyNudgeTranslationY(
      isStudyDrawerOpen = true,
      drawerTopPx = 800f,
      selectedAyahBottomPx = 960f,
      defaultNudgePx = 96f,
      maxNudgePx = 500f
    )
    assertThat(deepNudge).isLessThan(-160f)
    assertThat(deepNudge).isAtLeast(-500f)
  }

  @Test
  fun `ReaderFormatters localize durations and speeds cleanly in English and Arabic without U+00B7`() {
    Locale.setDefault(Locale.US)
    assertThat(ReaderFormatters.formatDuration(0L)).isEqualTo("0:00")
    assertThat(ReaderFormatters.formatDuration(24_000L)).isEqualTo("0:24")
    assertThat(ReaderFormatters.formatDuration(75_000L)).isEqualTo("1:15")
    assertThat(ReaderFormatters.formatSpeed(1.0f)).isEqualTo("1.0")
    assertThat(ReaderFormatters.formatSpeed(1.25f)).isEqualTo("1.25")

    Locale.setDefault(Locale.forLanguageTag("ar-EG"))
    val arDuration = ReaderFormatters.formatDuration(24_000L)
    val arSpeed = ReaderFormatters.formatSpeed(1.0f)
    assertThat(arDuration).isEqualTo("٠:٢٤")
    assertThat(arSpeed).isEqualTo("١٫٠")
    assertThat(arDuration).doesNotContain("\u00B7")
    assertThat(arSpeed).doesNotContain("\u00B7")
  }

  @Test
  fun `computeFloatingAyahBarPlacement has zero sacred text overlap and zero offscreen clipping across 600+ viewport and span geometries`() {
    data class ViewportProfile(
      val name: String,
      val widthPx: Float,
      val heightPx: Float,
      val density: Float
    )

    val profiles = listOf(
      ViewportProfile("Moto_g05_720x1604_2x", 720f, 1604f, 2.0f),
      ViewportProfile("FHD_1080x2400_2.75x", 1080f, 2400f, 2.75f),
      ViewportProfile("Small_720x1280_2x", 720f, 1280f, 2.0f),
      ViewportProfile("Landscape_1604x720_2x", 1604f, 720f, 2.0f)
    )

    var totalTested = 0
    for (profile in profiles) {
      val d = profile.density
      val topObs = 74f * d
      val botObs = profile.heightPx - 102f * d
      val pillInset = 18f * d
      val caretH = 7f * d
      val pillH = 52f * d
      val tagH = 28f * d
      val gap = 6f * d
      val caretPad = 30f * d
      val compactH = caretH + pillH
      val totalH = compactH + tagH

      val ftStep = 10f * d
      val spans = listOf(25f * d, 60f * d, 120f * d, 250f * d, 400f * d, 550f * d, 700f * d)
      var ft = topObs
      while (ft <= profile.heightPx - compactH - 40f * d) {
        for (span in spans) {
          val lb = ft + span
          if (lb > profile.heightPx - 10f * d) continue
          // Only include geometries where at least one side of the ayah leaves compactH space
          if (ft < compactH && (profile.heightPx - lb) < compactH) continue

          totalTested++
          val bounds = AyahOverlayBounds(
            firstLineLeft = 80f,
            firstLineTop = ft,
            firstLineRight = profile.widthPx - 80f,
            firstLineBottom = minOf(lb, ft + 30f * d),
            lastLineLeft = 100f,
            lastLineTop = maxOf(ft, lb - 30f * d),
            lastLineRight = profile.widthPx - 100f,
            lastLineBottom = lb
          )
          val p = computeFloatingAyahBarPlacement(
            bounds = bounds,
            viewportWidthPx = profile.widthPx,
            viewportHeightPx = profile.heightPx,
            topObstacleBottomPx = topObs,
            bottomObstacleTopPx = botObs,
            pillHorizontalInsetPx = pillInset,
            caretHeightPx = caretH,
            pillHeightPx = pillH,
            selectionTagHeightPx = tagH,
            gapFromAyahPx = gap,
            caretHorizontalPaddingPx = caretPad
          )
          val renderedH = if (p.showSelectionTag) totalH else compactH
          val barTop = p.topPx
          val barBottom = p.topPx + renderedH

          assertWithMessage("${profile.name} ft=$ft lb=$lb clipped top ($barTop)")
            .that(barTop).isAtLeast(0f)
          assertWithMessage("${profile.name} ft=$ft lb=$lb clipped bottom ($barBottom > ${profile.heightPx})")
            .that(barBottom).isAtMost(profile.heightPx)

          val overlapsAyah = barTop < lb && barBottom > ft
          assertWithMessage("${profile.name} ft=$ft lb=$lb overlaps ayah: [$barTop, $barBottom] vs [$ft, $lb]")
            .that(overlapsAyah).isFalse()

          if (!p.suppressTopPills) {
            assertWithMessage("${profile.name} ft=$ft lb=$lb overlaps top pills ($barTop < $topObs)")
              .that(barTop).isAtLeast(topObs)
          }
          if (!p.suppressBottomCapsule) {
            assertWithMessage("${profile.name} ft=$ft lb=$lb overlaps bottom capsule ($barBottom > $botObs)")
              .that(barBottom).isAtMost(botObs)
          }
        }
        ft += ftStep
      }
    }

    assertWithMessage("Expected sweeping 600+ geometry combinations")
      .that(totalTested).isAtLeast(600)
  }

  @Test
  fun `Al-Baqarah 2_282 full-page ayah placements have zero overlap and zero offscreen clipping`() {
    val d = 2.0f
    val vh = 1604f
    val topObs = 148f
    val botObs = 1400f
    val caretH = 14f
    val pillH = 104f
    val tagH = 56f
    val totalH = caretH + pillH + tagH
    val compactH = caretH + pillH

    val cases = listOf(
      250f to 1450f,
      250f to 1480f
    )
    for ((ft, lb) in cases) {
      val bounds = AyahOverlayBounds(
        firstLineLeft = 60f,
        firstLineTop = ft,
        firstLineRight = 660f,
        firstLineBottom = ft + 60f,
        lastLineLeft = 80f,
        lastLineTop = lb - 60f,
        lastLineRight = 640f,
        lastLineBottom = lb
      )
      val p = computeFloatingAyahBarPlacement(
        bounds = bounds,
        viewportWidthPx = 720f,
        viewportHeightPx = vh,
        topObstacleBottomPx = topObs,
        bottomObstacleTopPx = botObs,
        pillHorizontalInsetPx = 18f * d,
        caretHeightPx = caretH,
        pillHeightPx = pillH,
        selectionTagHeightPx = tagH,
        gapFromAyahPx = 6f * d,
        caretHorizontalPaddingPx = 30f * d
      )
      val renderedH = if (p.showSelectionTag) totalH else compactH
      val barTop = p.topPx
      val barBottom = p.topPx + renderedH

      assertThat(barTop).isAtLeast(0f)
      assertThat(barBottom).isAtMost(vh)
      assertWithMessage("2:282 (ft=$ft, lb=$lb) must have zero overlap with sacred text [$ft, $lb]")
        .that(barTop < lb && barBottom > ft).isFalse()
      assertThat(p.suppressTopPills || p.suppressBottomCapsule).isTrue()
    }
  }
}
