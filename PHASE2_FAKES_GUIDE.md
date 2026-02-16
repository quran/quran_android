# Phase 2: Fakes Over Mocks - Implementation Guide

## Philosophy

**Fakes over Mocks**: Create simple, working test implementations instead of using mocking frameworks.

- **Fake**: A working implementation with hardcoded/simplified behavior
- **Mock**: A framework-generated test double (Mockito, MockK)

## Why Not MockK?

Per maintainer @ahmedre's feedback:
- Mocks make tests brittle (coupled to implementation details)
- Fakes are easier to understand and maintain
- Fakes naturally document expected behavior

## Fake Placement Strategy

### ❌ Don't: Shared Fakes in test-utils

**Problem**: Circular dependencies

```
app → test-utils (for testing)
test-utils → app (for QuranSettings)  ❌ CIRCULAR!
```

**Example that failed**:
```kotlin
// common/test-utils/src/main/kotlin/.../FakeQuranSettings.kt
class FakeQuranSettings : QuranSettings(null) {  // ❌ Can't find QuranSettings!
  var lastPage: Int = 1
  override fun getLastPage(): Int = lastPage
}
```

### ✅ Do: Local Fakes in Test Source Sets

Create fakes where they're needed, in the test source set:

```
app/src/test/kotlin/com/quran/labs/androidquran/fakes/
├── FakeQuranSettings.kt
├── FakeAudioUtils.kt
└── FakeQuranFileUtils.kt
```

## Fake Implementation Pattern

### Example: FakeQuranSettings

```kotlin
// app/src/test/kotlin/.../fakes/FakeQuranSettings.kt
package com.quran.labs.androidquran.fakes

import android.content.SharedPreferences
import com.quran.labs.androidquran.util.QuranSettings

/**
 * Fake QuranSettings for testing presenters.
 *
 * Provides controllable, predictable behavior without Android framework dependencies.
 */
class FakeQuranSettings : QuranSettings(null) {

  // Configurable test properties
  var lastPage: Int = 1
  var isNightModeEnabled: Boolean = false
  var shouldStreamAudio: Boolean = false
  var shouldOverlayPageInfoEnabled: Boolean = true

  // Override methods used by code under test
  override fun getLastPage(): Int = lastPage

  override fun setLastPage(page: Int) {
    lastPage = page
  }

  override fun isNightMode(): Boolean = isNightModeEnabled

  override fun shouldStream(): Boolean = shouldStreamAudio

  override fun shouldOverlayPageInfo(): Boolean = shouldOverlayPageInfoEnabled

  // No-op for listener methods
  override fun registerPreferencesListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
  override fun unregisterPreferencesListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

  companion object {
    /** Create with defaults */
    fun create(): FakeQuranSettings = FakeQuranSettings()

    /** Create configured for specific page */
    fun withLastPage(page: Int): FakeQuranSettings {
      return FakeQuranSettings().apply {
        lastPage = page
      }
    }

    /** Create configured for night mode */
    fun withNightMode(): FakeQuranSettings {
      return FakeQuranSettings().apply {
        isNightModeEnabled = true
      }
    }
  }
}
```

### Usage in Tests

```kotlin
class QuranPagePresenterTest {

  private lateinit var presenter: QuranPagePresenter
  private lateinit var fakeSettings: FakeQuranSettings
  private lateinit var mockScreen: QuranPageScreen

  @Before
  fun setup() {
    fakeSettings = FakeQuranSettings.create()

    // Create presenter with fake
    presenter = QuranPagePresenter(
      coordinatesModel = mockCoordinatesModel,
      quranSettings = fakeSettings,  // ✅ Use fake
      quranPageLoader = mockPageLoader,
      quranInfo = mockQuranInfo,
      pages = intArrayOf(1, 2, 3)
    )
  }

  @Test
  fun `should overlay page info when setting enabled`() {
    // Arrange
    fakeSettings.shouldOverlayPageInfoEnabled = true

    // Act
    presenter.bind(mockScreen)

    // Assert
    verify(mockCoordinatesModel).getPageCoordinates(shouldOverlay = true, ...)
  }

  @Test
  fun `should not overlay page info when setting disabled`() {
    // Arrange
    fakeSettings.shouldOverlayPageInfoEnabled = false

    // Act
    presenter.bind(mockScreen)

    // Assert
    verify(mockCoordinatesModel).getPageCoordinates(shouldOverlay = false, ...)
  }
}
```

## When to Create Fakes

### ✅ Good Candidates for Fakes

1. **Settings/Configuration classes**
   - QuranSettings
   - SharedPreferences wrappers

2. **Simple data providers**
   - QuranInfo (page/sura/ayah lookups)
   - QuranDisplayData

3. **Utilities with side effects**
   - AudioUtils (file checks, URL generation)
   - QuranFileUtils (file system operations)

### ❌ Bad Candidates for Fakes

1. **Complex business logic** - might need real implementation
2. **Database layers** - use in-memory database (like BookmarksDaoImplTest)
3. **Android framework classes** - use Robolectric or real instrumented tests

## Migration Strategy

### Phase 2 Approach

**Week 1-2**: Foundation ✅
- ✅ Created BookmarksDaoImpl tests (16 tests, in-memory SQLite)
- ✅ Established fake pattern documentation

**Week 3-4**: Presenter Tests (NEXT)
- Create local fakes as needed:
  - FakeQuranSettings
  - FakeAudioUtils
  - FakeQuranFileUtils
- Write tests for:
  - QuranPagePresenter
  - AudioPresenter

**Week 5-6**: Mockito Migration
- Identify existing tests using Mockito
- Replace with fakes where appropriate
- Remove Mockito dependency

## Key Principles

1. **Minimal Implementation**: Only implement methods actually used by tests
2. **Configurability**: Expose properties for test control
3. **Factory Methods**: Provide convenient builders for common scenarios
4. **Documentation**: Explain what the fake does and why
5. **Colocate**: Keep fakes near the tests that use them

## Real-World Example

From our BookmarksDaoImpl tests - we used **real in-memory SQLite** instead of a fake:

```kotlin
@Before
fun setup() {
  // Real database, in-memory driver
  val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  BookmarksDatabase.Schema.create(driver)
  database = BookmarksDatabase(driver, ...)
  dao = BookmarksDaoImpl(database)
}
```

**Why?** SQLite driver is designed for testing. Creating a fake would duplicate complex database behavior.

**Rule**: Use real implementations when they're designed for testing (in-memory databases, test doubles from libraries).

## References

- Original issue: https://github.com/quran/quran_android/issues/3513
- Phase 1 PR: https://github.com/quran/quran_android/pull/3520
- Martin Fowler: [Mocks Aren't Stubs](https://martinfowler.com/articles/mocksArentStubs.html)
