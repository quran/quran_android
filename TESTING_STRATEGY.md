# Comprehensive Testing Strategy for Quran Android

## Executive Summary

This document outlines a comprehensive testing strategy for the Quran Android project based on extensive codebase analysis. The project currently has **~1.5% test coverage** (24 test files for 540+ source files). This strategy aims to establish testing as a cornerstone of development with clear patterns, tooling, and coverage targets.

---

## Table of Contents

1. [Current State Analysis](#1-current-state-analysis)
2. [Testing Philosophy & Goals](#2-testing-philosophy--goals)
3. [Test Pyramid Strategy](#3-test-pyramid-strategy)
4. [Testing Frameworks & Tooling](#4-testing-frameworks--tooling)
5. [Architecture-Specific Testing Patterns](#5-architecture-specific-testing-patterns)
6. [Module Testing Priorities](#6-module-testing-priorities)
7. [Test Infrastructure Setup](#7-test-infrastructure-setup)
8. [Coverage Targets & Metrics](#8-coverage-targets--metrics)
9. [Implementation Roadmap](#9-implementation-roadmap)
10. [Conventions & Standards](#10-conventions--standards)

---

## 1. Current State Analysis

### 1.1 Existing Test Coverage

| Module | Test Files | Coverage | Notes |
|--------|------------|----------|-------|
| `app` | 19 | ~3% | Presenters, models, utilities |
| `common:audio` | 2 | ~5% | Cache commands only |
| `common:mapper` | 1 | ~30% | Mapper tests |
| `feature:audio` | 2 | ~10% | AudioUpdater, MD5 |
| Other modules | 0 | 0% | No tests |

### 1.2 Existing Test Infrastructure

```
app/src/test/
├── java/com/quran/labs/
│   ├── BaseTestExtension.kt          # RxJava test utilities
│   └── androidquran/
│       ├── base/TestApplication.kt   # Robolectric test app
│       ├── core/QuranTestRunner.kt   # Custom test runner
│       └── di/quran/TestQuranActivityBindings.kt
```

### 1.3 Test Frameworks Already in Use

- **JUnit 4.13.2** - Test framework
- **Truth 1.4.5** - Assertions
- **Mockito 5.21.0** - Mocking
- **Robolectric 4.16.1** - Android unit tests
- **Espresso 3.7.0** - UI testing (minimal usage)
- **Turbine 1.2.1** - Flow testing
- **kotlinx-coroutines-test** - Coroutine testing

### 1.4 Identified Gaps

1. **No coverage reporting** - Cannot measure progress
2. **No integration tests** - Only unit tests exist
3. **No E2E tests** - No automated user journeys
4. **Inconsistent patterns** - Mix of Java/Kotlin, various assertion styles
5. **No test documentation** - No guidelines for contributors
6. **Critical modules untested** - BookmarksDao, QuranInfo, Search, Recitation

---

## 2. Testing Philosophy & Goals

### 2.1 Core Principles

1. **Test Behavior, Not Implementation**
   - Focus on inputs/outputs, not internal state
   - Tests should survive refactoring

2. **Fast Feedback Loop**
   - Unit tests must run in <10 seconds
   - Integration tests <2 minutes
   - CI must complete <10 minutes

3. **Deterministic & Isolated**
   - No flaky tests
   - No test interdependencies
   - Controlled concurrency

4. **Documentation Through Tests**
   - Tests serve as living documentation
   - Clear naming: `should_expectedBehavior_when_condition()`

### 2.2 Goals

| Goal | Target | Timeline |
|------|--------|----------|
| Unit test coverage | 70% | 6 months |
| Integration test coverage | 40% | 9 months |
| Critical path E2E coverage | 100% | 12 months |
| Zero flaky tests | 0 | Immediate |
| CI test time | <10 min | 3 months |

---

## 3. Test Pyramid Strategy

```
                    ╱╲
                   ╱  ╲
                  ╱ E2E╲         5% of tests
                 ╱──────╲        (10-15 tests)
                ╱        ╲
               ╱Integration╲     20% of tests
              ╱────────────╲     (50-100 tests)
             ╱              ╲
            ╱   Unit Tests   ╲   75% of tests
           ╱──────────────────╲  (300-500 tests)
```

### 3.1 Unit Tests (75%)

**Scope:** Single class/function in isolation

**Target Areas:**
- Presenters (business logic)
- Models (data transformations)
- Utilities (pure functions)
- ViewModels (state management)
- Mappers (data conversion)
- Validators (input validation)

**Characteristics:**
- Run in JVM (no Android framework)
- Mock all dependencies
- Fast execution (<100ms per test)
- High coverage target (80%+)

### 3.2 Integration Tests (20%)

**Scope:** Multiple components working together

**Target Areas:**
- Database operations (SQLDelight DAOs)
- Network layer (Retrofit + OkHttp)
- Repository patterns
- DI graph validation
- Presenter + Model integration

**Characteristics:**
- May use Robolectric for Android APIs
- Real implementations where practical
- MockWebServer for network
- In-memory databases

### 3.3 E2E Tests (5%)

**Scope:** Complete user journeys

**Target Flows:**
1. App launch → Navigate to Sura → Read page
2. Search for verse → Navigate to result
3. Play audio → Pause → Resume → Stop
4. Add bookmark → View bookmarks → Delete
5. Download translation → View translation
6. Download audio → Play offline

**Characteristics:**
- Run on emulator/device
- Compose testing rules
- Espresso for legacy views
- Screenshot testing for UI regression

---

## 4. Testing Frameworks & Tooling

### 4.1 Recommended Stack

```kotlin
// gradle/libs.versions.toml additions

[versions]
kotestVersion = "5.8.0"
mockkVersion = "1.13.9"
koverVersion = "0.7.5"

[libraries]
# Enhanced assertions
kotest-assertions = { module = "io.kotest:kotest-assertions-core", version.ref = "kotestVersion" }

# Kotlin-first mocking
mockk = { module = "io.mockk:mockk", version.ref = "mockkVersion" }
mockk-android = { module = "io.mockk:mockk-android", version.ref = "mockkVersion" }

# Already present - keep using
turbine = { module = "app.cash.turbine:turbine-jvm", version.ref = "turbineVersion" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesVersion" }

[plugins]
# Coverage reporting
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "koverVersion" }
```

### 4.2 Framework Selection Rationale

| Framework | Purpose | Why |
|-----------|---------|-----|
| **JUnit 4** | Test runner | Already in use, stable |
| **Truth** | Assertions | Already in use, readable |
| **Kotest Assertions** | Enhanced assertions | Better collection/flow assertions |
| **MockK** | Mocking | Kotlin-first, coroutine support |
| **Mockito** | Legacy mocking | Keep for existing tests |
| **Turbine** | Flow testing | Already in use, essential |
| **Robolectric** | Android unit tests | Already in use |
| **Compose Test** | Compose UI tests | Official, well-supported |
| **Kover** | Coverage | Kotlin-native, accurate |

### 4.3 Test Dependencies by Module Type

```kotlin
// Pure Kotlin module (common:data, common:audio, etc.)
testImplementation(libs.junit)
testImplementation(libs.truth)
testImplementation(libs.kotest.assertions)
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)

// Android Library module
testImplementation(libs.junit)
testImplementation(libs.truth)
testImplementation(libs.mockk.android)
testImplementation(libs.robolectric)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)

// Compose module (feature:audiobar, feature:qarilist, etc.)
testImplementation(libs.junit)
testImplementation(libs.truth)
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)
// Compose testing
testImplementation(libs.compose.ui.test.junit4)
debugImplementation(libs.compose.ui.test.manifest)
```

---

## 5. Architecture-Specific Testing Patterns

### 5.1 MVP Presenter Testing

The app uses MVP pattern extensively. Presenters are the primary business logic containers.

```kotlin
class BookmarkPresenterTest {
    // 1. Mock dependencies
    private val bookmarkModel: BookmarkModel = mockk(relaxed = true)
    private val settings: QuranSettings = mockk(relaxed = true)
    private val view: BookmarkScreen = mockk(relaxed = true)

    // 2. Use test dispatchers
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var presenter: BookmarkPresenter

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        presenter = BookmarkPresenter(bookmarkModel, settings)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should load bookmarks when bound`() {
        // Given
        val bookmarks = listOf(testBookmark())
        coEvery { bookmarkModel.getBookmarks() } returns flowOf(bookmarks)

        // When
        presenter.bind(view)

        // Then
        verify { view.showBookmarks(bookmarks) }
    }

    @Test
    fun `should add bookmark and notify observers`() = runTest {
        // Given
        val sura = 2
        val ayah = 255

        // When
        presenter.addBookmark(sura, ayah)

        // Then
        coVerify { bookmarkModel.addBookmark(sura, ayah) }
    }
}
```

### 5.2 RxJava Observable Testing

Many models use RxJava3. Use TestObserver pattern.

```kotlin
class BookmarkModelTest {
    @get:Rule
    val rxSchedulerRule = RxImmediateSchedulerRule()

    @Test
    fun `should emit bookmarks on subscription`() {
        // Given
        val dao: BookmarksDBAdapter = mockk()
        every { dao.getBookmarks() } returns listOf(testBookmark())
        val model = BookmarkModel(dao)

        // When
        val testObserver = model.bookmarksObservable().test()

        // Then
        testObserver.awaitTerminalEvent()
        testObserver.assertNoErrors()
        testObserver.assertValueCount(1)
        testObserver.assertValue { it.size == 1 }
    }
}

// Rule for synchronous RxJava execution
class RxImmediateSchedulerRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
                RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
                RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
                try {
                    base.evaluate()
                } finally {
                    RxJavaPlugins.reset()
                    RxAndroidPlugins.reset()
                }
            }
        }
    }
}
```

### 5.3 Coroutines & Flow Testing

Use `runTest` and Turbine for Flow testing.

```kotlin
class AudioStatusRepositoryTest {
    private val repository = AudioStatusRepository()

    @Test
    fun `should emit stopped state initially`() = runTest {
        repository.audioPlaybackFlow.test {
            val initial = awaitItem()
            assertThat(initial).isInstanceOf(AudioStatus.Stopped::class.java)
        }
    }

    @Test
    fun `should emit playing state when audio starts`() = runTest {
        repository.audioPlaybackFlow.test {
            skipItems(1) // Skip initial stopped state

            repository.updatePlaybackStatus(AudioStatus.Playback(/* ... */))

            val playing = awaitItem()
            assertThat(playing).isInstanceOf(AudioStatus.Playback::class.java)
        }
    }
}
```

### 5.4 SQLDelight DAO Testing

Use in-memory SQLite driver for database tests.

```kotlin
class BookmarksDaoTest {
    private lateinit var database: BookmarksDatabase
    private lateinit var dao: BookmarksDaoImpl

    @Before
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BookmarksDatabase.Schema.create(driver)
        database = BookmarksDatabase(driver)
        dao = BookmarksDaoImpl(database, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `should insert and retrieve bookmark`() = runTest {
        // Given
        val bookmark = Bookmark(sura = 2, ayah = 255, page = 42)

        // When
        dao.addBookmark(bookmark)
        val result = dao.getBookmarks().first()

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].sura).isEqualTo(2)
        assertThat(result[0].ayah).isEqualTo(255)
    }
}
```

### 5.5 Compose UI Testing

```kotlin
class AudioBarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should show play button when stopped`() {
        // Given
        val state = AudioBarState.Stopped(qariName = "Test Qari")

        // When
        composeTestRule.setContent {
            QuranTheme {
                AudioBar(state = state, onEvent = {})
            }
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Play")
            .assertIsDisplayed()
    }

    @Test
    fun `should emit play event on button click`() {
        // Given
        var emittedEvent: AudioBarEvent? = null
        val state = AudioBarState.Stopped(qariName = "Test Qari")

        composeTestRule.setContent {
            QuranTheme {
                AudioBar(state = state, onEvent = { emittedEvent = it })
            }
        }

        // When
        composeTestRule.onNodeWithContentDescription("Play").performClick()

        // Then
        assertThat(emittedEvent).isInstanceOf(AudioBarEvent.Play::class.java)
    }
}
```

### 5.6 Network Testing with MockWebServer

```kotlin
class AudioUpdateServiceTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: AudioUpdateService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

        service = retrofit.create(AudioUpdateService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should parse audio updates response`() = runTest {
        // Given
        val json = """
            {
                "revision": 5,
                "updates": [
                    {"path": "sheikh1/", "files": [{"name": "001.mp3", "md5": "abc123"}]}
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json))

        // When
        val result = service.getUpdates(revision = 1)

        // Then
        assertThat(result.revision).isEqualTo(5)
        assertThat(result.updates).hasSize(1)
    }
}
```

---

## 6. Module Testing Priorities

### 6.1 Priority Matrix

| Priority | Module | Rationale | Target Coverage |
|----------|--------|-----------|-----------------|
| **P0** | `common:data` | Core domain logic (QuranInfo) | 90% |
| **P0** | `common:bookmark` | User data persistence | 90% |
| **P1** | `app/presenter/*` | Business logic | 80% |
| **P1** | `common:audio` | Audio playback state | 80% |
| **P1** | `feature:audio` | Audio updates | 80% |
| **P2** | `common:search` | Search functionality | 70% |
| **P2** | `feature:downloadmanager` | Complex state management | 70% |
| **P2** | `feature:audiobar` | UI state logic | 70% |
| **P3** | `common:translation` | Translation DAOs | 60% |
| **P3** | `feature:qarilist` | Simple UI logic | 60% |
| **P3** | `common:recitation` | Presenter logic | 60% |
| **P4** | `common:networking` | DI configuration | 40% |
| **P4** | `feature:linebyline` | Complex rendering | 50% |
| **P4** | `feature:autoquran` | Media3 integration | 40% |

### 6.2 Detailed Module Testing Plans

#### common:data (P0 - Critical)

```
Target: 90% coverage
Test Focus:
├── QuranInfo
│   ├── getPageFromSuraAyah() - 20+ test cases
│   ├── getSuraFromPage() - edge cases
│   ├── getJuzFromPage() - boundary conditions
│   └── getNumberOfAyahs() - all suras
├── SuraAyah
│   ├── Comparable implementation
│   ├── Serialization/Parcelable
│   └── Factory methods
└── VerseRange
    ├── contains() logic
    └── iteration
```

#### common:bookmark (P0 - Critical)

```
Target: 90% coverage
Test Focus:
├── BookmarksDaoImpl
│   ├── CRUD operations
│   ├── Tag operations
│   ├── Recent pages
│   └── Transaction safety
├── Mappers
│   └── SQLDelight → Domain model
└── Flow emissions
    └── Real-time updates
```

#### app/presenter/* (P1 - High)

```
Target: 80% coverage
Test Focus:
├── BookmarkPresenter
│   ├── Loading states
│   ├── Sorting/grouping
│   ├── Tag filtering
│   └── Error handling
├── AudioPresenter
│   ├── Playback commands
│   ├── State transitions
│   └── Error recovery
├── TranslationPresenter
│   ├── Data combination
│   ├── Missing translations
│   └── Arabic text loading
└── QuranPagePresenter
    ├── Page loading
    ├── Coordinate loading
    └── Error states
```

---

## 7. Test Infrastructure Setup

### 7.1 Enable Kover Coverage

```kotlin
// build.gradle.kts (root)
plugins {
    alias(libs.plugins.kover) apply false
}

// app/build.gradle.kts
plugins {
    id("org.jetbrains.kotlinx.kover")
}

koverReport {
    filters {
        excludes {
            classes(
                "*_Factory",
                "*_Factory\$*",
                "*_MembersInjector",
                "*Module",
                "*Module\$*",
                "*Binding*",
                "*.BuildConfig",
                "*.databinding.*",
                "*.R",
                "*.R\$*"
            )
            packages(
                "*.di.*",
                "*.generated.*"
            )
        }
    }

    defaults {
        html { onCheck = true }
        xml { onCheck = true }
    }
}
```

### 7.2 Test Utilities Module

Create a shared test utilities module:

```
common/
└── test-utils/
    ├── build.gradle.kts
    └── src/main/kotlin/com/quran/labs/test/
        ├── TestDispatcherRule.kt
        ├── RxSchedulerRule.kt
        ├── FlowTestExtensions.kt
        ├── TestDataFactory.kt
        └── FakeImplementations.kt
```

```kotlin
// TestDispatcherRule.kt
class TestDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

// TestDataFactory.kt
object TestDataFactory {
    fun bookmark(
        id: Long = 1,
        sura: Int = 1,
        ayah: Int = 1,
        page: Int = 1,
        timestamp: Long = System.currentTimeMillis()
    ) = Bookmark(id, sura, ayah, page, timestamp)

    fun qariItem(
        id: Int = 1,
        name: String = "Test Qari",
        url: String = "https://example.com/",
        path: String = "test",
        isGapless: Boolean = false
    ) = QariItem(id, name, url, path, isGapless, null)

    fun suraAyah(sura: Int = 1, ayah: Int = 1) = SuraAyah(sura, ayah)
}

// FakeImplementations.kt
class FakeBookmarksDao : BookmarksDao {
    private val bookmarks = mutableListOf<Bookmark>()
    private val _bookmarksFlow = MutableStateFlow<List<Bookmark>>(emptyList())

    override fun bookmarks(): Flow<List<Bookmark>> = _bookmarksFlow

    override suspend fun addBookmark(bookmark: Bookmark) {
        bookmarks.add(bookmark)
        _bookmarksFlow.value = bookmarks.toList()
    }

    override suspend fun removeBookmark(id: Long) {
        bookmarks.removeAll { it.id == id }
        _bookmarksFlow.value = bookmarks.toList()
    }
}
```

### 7.3 CI Configuration Updates

```yaml
# .github/workflows/build.yml additions

      - name: Run tests with coverage
        run: ./gradlew testMadaniDebug koverXmlReportMadaniDebug -PdisableFirebase

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          files: app/build/reports/kover/reportMadaniDebug.xml
          fail_ci_if_error: true

      - name: Check coverage threshold
        run: |
          ./gradlew koverVerifyMadaniDebug -PdisableFirebase
```

---

## 8. Coverage Targets & Metrics

### 8.1 Module Coverage Targets

| Phase | Timeline | Target | Modules |
|-------|----------|--------|---------|
| Phase 1 | Month 1-2 | 50% | common:data, common:bookmark |
| Phase 2 | Month 3-4 | 70% | app/presenter/*, common:audio |
| Phase 3 | Month 5-6 | 60% | feature:audio, common:search |
| Phase 4 | Month 7-9 | 50% | feature:*, remaining common |
| Phase 5 | Month 10-12 | 40% | UI/E2E tests |

### 8.2 Coverage Enforcement

```kotlin
// build.gradle.kts
koverReport {
    verify {
        rule {
            bound {
                minValue = 70
                metric = MetricType.LINE
                aggregation = AggregationType.COVERED_PERCENTAGE
            }
        }

        // Per-package rules
        rule("presenters") {
            filters {
                includes { packages("*.presenter.*") }
            }
            bound {
                minValue = 80
            }
        }

        rule("models") {
            filters {
                includes { packages("*.model.*") }
            }
            bound {
                minValue = 85
            }
        }
    }
}
```

### 8.3 Metrics Dashboard

Track these metrics:
- Line coverage %
- Branch coverage %
- Test count by type (unit/integration/E2E)
- Test execution time
- Flaky test count (should be 0)
- Coverage trend over time

---

## 9. Implementation Roadmap

### Phase 1: Foundation (Weeks 1-4)

- [ ] Add Kover plugin and configure coverage
- [ ] Create common:test-utils module
- [ ] Set up CI coverage reporting
- [ ] Write QuranInfo tests (20+ tests)
- [ ] Write BookmarksDaoImpl tests (15+ tests)
- [ ] Document testing patterns in this file

### Phase 2: Core Business Logic (Weeks 5-8)

- [ ] BookmarkPresenter tests (10+ tests)
- [ ] AudioPresenter tests (10+ tests)
- [ ] TranslationPresenter tests (10+ tests)
- [ ] BookmarkModel tests (10+ tests)
- [ ] AudioStatusRepository tests (8+ tests)

### Phase 3: Feature Modules (Weeks 9-12)

- [ ] AudioUpdater tests expansion
- [ ] DownloadManager presenter tests
- [ ] QariList presenter tests
- [ ] Search tests (ArabicSearcher, etc.)

### Phase 4: Integration Tests (Weeks 13-16)

- [ ] Database integration tests
- [ ] Network integration tests (MockWebServer)
- [ ] Presenter + Model integration
- [ ] DI graph validation tests

### Phase 5: UI & E2E Tests (Weeks 17-24)

- [ ] Compose component tests
- [ ] Critical user journey E2E tests
- [ ] Screenshot tests for regression
- [ ] Performance benchmarks

---

## 10. Conventions & Standards

### 10.1 Test Naming

```kotlin
// Pattern: should_expectedBehavior_when_condition
@Test
fun `should return empty list when no bookmarks exist`()

@Test
fun `should emit error state when network fails`()

@Test
fun `should navigate to page when sura selected`()
```

### 10.2 Test Structure (AAA Pattern)

```kotlin
@Test
fun `should add bookmark successfully`() {
    // Arrange (Given)
    val sura = 2
    val ayah = 255
    coEvery { dao.addBookmark(any()) } returns Unit

    // Act (When)
    presenter.addBookmark(sura, ayah)

    // Assert (Then)
    coVerify { dao.addBookmark(match { it.sura == sura && it.ayah == ayah }) }
}
```

### 10.3 Test File Organization

```
module/src/test/kotlin/com/quran/labs/
├── package/
│   ├── ClassNameTest.kt           # Unit tests for ClassName
│   └── integration/
│       └── ClassNameIntegrationTest.kt
└── fixtures/
    └── TestData.kt                # Shared test data
```

### 10.4 What to Test vs What Not to Test

**DO Test:**
- Business logic in presenters/models
- Data transformations
- State machines and transitions
- Error handling paths
- Edge cases and boundaries
- Flow/Observable emissions

**DON'T Test:**
- Private methods directly
- Simple data classes (getters/setters)
- Framework code (Android APIs)
- Generated code (DI, databinding)
- Third-party libraries

### 10.5 Mocking Guidelines

```kotlin
// Prefer fakes for complex dependencies
class FakeBookmarkRepository : BookmarkRepository {
    private val bookmarks = mutableListOf<Bookmark>()
    // ... implementation
}

// Use mocks for simple dependencies or verification
val analytics: AnalyticsProvider = mockk(relaxed = true)

// Avoid over-mocking - if you need 5+ mocks, consider integration test
```

### 10.6 Flaky Test Prevention

1. Never use `Thread.sleep()` - use `advanceTimeBy()` or `runCurrent()`
2. Always use test dispatchers for coroutines
3. Use `RxSchedulerRule` for RxJava
4. Isolate file system tests with temp directories
5. Use MockWebServer for network tests
6. Reset singletons in `@After`

---

## Appendix A: Test File Templates

### Unit Test Template

```kotlin
package com.quran.labs.androidquran.presenter

import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.*
import org.junit.*

class ExamplePresenterTest {

    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private val dependency: Dependency = mockk(relaxed = true)
    private lateinit var presenter: ExamplePresenter

    @Before
    fun setup() {
        presenter = ExamplePresenter(dependency)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `should do something when condition`() = runTest {
        // Arrange

        // Act

        // Assert
    }
}
```

### Flow Test Template

```kotlin
@Test
fun `should emit states in order`() = runTest {
    repository.stateFlow.test {
        // Initial state
        assertThat(awaitItem()).isEqualTo(State.Initial)

        // Trigger action
        repository.doSomething()

        // Loading state
        assertThat(awaitItem()).isEqualTo(State.Loading)

        // Success state
        assertThat(awaitItem()).isInstanceOf(State.Success::class.java)

        cancelAndIgnoreRemainingEvents()
    }
}
```

---

## Appendix B: References

- [Android Testing Documentation](https://developer.android.com/training/testing)
- [Testing Kotlin Coroutines](https://developer.android.com/kotlin/coroutines/test)
- [Compose Testing](https://developer.android.com/jetpack/compose/testing)
- [Truth Assertions](https://truth.dev/)
- [MockK](https://mockk.io/)
- [Turbine](https://github.com/cashapp/turbine)
- [Kover](https://github.com/Kotlin/kotlinx-kover)

---

*Document Version: 1.0*
*Last Updated: 2026-02-05*
*Author: Testing Strategy Analysis*
