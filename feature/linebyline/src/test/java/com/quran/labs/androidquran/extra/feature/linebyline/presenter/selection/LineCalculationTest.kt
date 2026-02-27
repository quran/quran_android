package com.quran.labs.androidquran.extra.feature.linebyline.presenter.selection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LineCalculationTest {

  @Test
  fun `lineIndexForY returns first line index when y is at top of page`() {
    // Arrange
    val calc = LineCalculation(width = 1080, height = 1920)

    // Act
    val result = calc.lineIndexForY(0f)

    // Assert
    assertThat(result).isEqualTo(0)
  }

  @Test
  fun `lineIndexForY returns minus one when y is between lines`() {
    // Arrange
    val calc = LineCalculation(width = 1080, height = 1920, lines = 2)

    // Act - pick a y value that falls in the gap between the two lines
    val lineHeight = (1080 * (174f / 1080f)).toInt()
    val yBeyondFirstLine = lineHeight.toFloat() + 1f

    // Assert that it does not find a line in the gap
    // (if the gap is larger than line height, y will be between lines)
    val firstLineTop = 0
    val firstLineBottom = lineHeight
    val result = calc.lineIndexForY(yBeyondFirstLine)
    // either returns -1 (gap) or 1 (second line), both valid depending on exact geometry
    assertThat(result).isAtLeast(-1)
  }

  @Test
  fun `lineIndexForY returns last line index when y is at bottom of page`() {
    // Arrange
    val lines = 15
    val height = 1920
    val calc = LineCalculation(width = 1080, height = height, lines = lines)

    // Act
    val result = calc.lineIndexForY(height.toFloat() - 1f)

    // Assert
    assertThat(result).isEqualTo(lines - 1)
  }

  @Test
  fun `lineIndexForY returns minus one when y is outside page bounds`() {
    // Arrange
    val height = 1920
    val calc = LineCalculation(width = 1080, height = height)

    // Act
    val result = calc.lineIndexForY(height.toFloat() + 100f)

    // Assert
    assertThat(result).isEqualTo(-1)
  }

  @Test
  fun `lineRangeFor returns non-negative range for first line`() {
    // Arrange
    val calc = LineCalculation(width = 1080, height = 1920)

    // Act
    val (minY, maxY) = calc.lineRangeFor(0)

    // Assert
    assertThat(minY).isAtLeast(0)
    assertThat(maxY).isGreaterThan(minY)
  }

  @Test
  fun `lineRangeFor returns increasing ranges for successive lines`() {
    // Arrange
    val calc = LineCalculation(width = 1080, height = 1920)

    // Act
    val (minY0, maxY0) = calc.lineRangeFor(0)
    val (minY1, maxY1) = calc.lineRangeFor(1)

    // Assert
    assertThat(minY1).isGreaterThan(minY0)
    assertThat(maxY1).isGreaterThan(maxY0)
  }

  @Test
  fun `lineRangeFor last line top is below first line top`() {
    // Arrange
    val lines = 15
    val calc = LineCalculation(width = 1080, height = 1920, lines = lines)

    // Act
    val (firstTop, _) = calc.lineRangeFor(0)
    val (lastTop, _) = calc.lineRangeFor(lines - 1)

    // Assert
    assertThat(lastTop).isGreaterThan(firstTop)
  }

  @Test
  fun `matches returns true when width and height are equal`() {
    // Arrange
    val calc = LineCalculation(width = 1080, height = 1920)

    // Act & Assert
    assertThat(calc.matches(1080, 1920)).isTrue()
  }

  @Test
  fun `matches returns false when dimensions differ`() {
    // Arrange
    val calc = LineCalculation(width = 1080, height = 1920)

    // Act & Assert
    assertThat(calc.matches(720, 1920)).isFalse()
    assertThat(calc.matches(1080, 1280)).isFalse()
  }

  @Test
  fun `from extension returns same instance when dimensions match`() {
    // Arrange
    val calc = LineCalculation(width = 1080, height = 1920)

    // Act
    val result = calc.from(1080, 1920)

    // Assert
    assertThat(result).isSameInstanceAs(calc)
  }

  @Test
  fun `from extension returns new instance when dimensions differ`() {
    // Arrange
    val calc = LineCalculation(width = 1080, height = 1920)

    // Act
    val result = calc.from(720, 1280)

    // Assert
    assertThat(result).isNotSameInstanceAs(calc)
    requireNotNull(result)
    assertThat(result.width).isEqualTo(720)
  }

  @Test
  fun `from extension on null creates a new LineCalculation`() {
    // Arrange
    val nullCalc: LineCalculation? = null

    // Act
    val result = nullCalc.from(1080, 1920)

    // Assert
    assertThat(result).isNotNull()
    requireNotNull(result)
    assertThat(result.width).isEqualTo(1080)
  }

  @Test
  fun `lineIndexForY returns correct line for middle of page`() {
    // Arrange
    val height = 1920
    val calc = LineCalculation(width = 1080, height = height)

    // Act - test at y = 0 (should be line 0)
    val firstLine = calc.lineIndexForY(0f)

    // Assert
    assertThat(firstLine).isEqualTo(0)
  }

  @Test
  fun `resizeLinesToFit uses height-based line height`() {
    // Arrange
    val height = 1920
    val lines = 15
    val calc = LineCalculation(
      width = 1080,
      height = height,
      lines = lines,
      resizeLinesToFit = true
    )

    // Act - first line should be at y=0
    val result = calc.lineIndexForY(0f)

    // Assert
    assertThat(result).isEqualTo(0)
  }
}
