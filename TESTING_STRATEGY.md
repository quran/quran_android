# Testing Strategy — Quran Android

## Overview

This document covers testing philosophy, tooling, patterns, conventions, and the full history of the testing initiative (Phases 1–6). Use it as the single reference for how tests are written and where the project stands.

**Baseline** (before Phase 1): ~1.5% coverage — 24 test files across 540+ source files.
**Current** (after Phase 6): 413+ tests across 11 modules, zero Mockito usage in `app/src/test`.

---

## Philosophy

### Core Principles

1. **Test behavior, not implementation** — focus on inputs and outputs, not internal state
2. **Fakes over mocks** — prefer simple stateful fakes over mock verification
3. **Fast and deterministic** — unit tests < 100ms each; no flaky tests, no test interdependencies
4. **Real infrastructure where possible** — in-memory SQLite over mocked DAOs; real dispatchers via `TestDispatcherRule`

### Fakes Philosophy

This project **prefers fakes over mocks**. Mocks tie tests to implementation details and break silently on refactors. Fakes are readable, reusable, and survive changes.

```kotlin
// Preferred: stateful fake
class FakeBookmarkDao : BookmarkDao {
    private val bookmarks = mutableListOf<Bookmark>()
    override suspend fun getBookmarks(): List<Bookmark> = bookmarks.toList()
    override suspend fun addBookmark(bookmark: Bookmark) { bookmarks.add(bookmark) }
}

// Test
class BookmarkRepositoryTest {
    private val fakeDao = FakeBookmarkDao()
    private val repository = BookmarkRepository(fakeDao)

    @Test
    fun `adding bookmark persists it`() = runTest {
        repository.addBookmark(testBookmark)
        assertThat(repository.getBookmarks()).contains(testBookmark)
    }
}
```

---

## Test Pyramid

```
          ╱╲
         ╱E2E╲          5%  (10–15 tests)
        ╱──────╲
       ╱Integration╲    20% (50–100 tests)
      ╱──────────────╲
     ╱   Unit Tests   ╲  75% (300–500 tests)
```

| Type | Scope | Tools |
|------|-------|-------|
| Unit | Single class/function | JVM only, fakes, < 100ms |
| Integration | Multiple components | Robolectric, in-memory SQLite, MockWebServer |
| E2E | Full user journeys | Emulator, Compose testing, Espresso |

---

## Testing Stack

| Framework | Purpose |
|-----------|---------|
| JUnit 4 | Test runner |
| Truth | Assertions |
| Turbine | Flow / StateFlow testing |
| Robolectric | Android APIs in JVM tests |
| Kover | Coverage reporting |
| SQLDelight JDBC driver | In-memory SQLite for DAO tests |
| MockWebServer | Network layer testing |

### Standard dependencies by module type

```kotlin
// Pure Kotlin module
testImplementation(libs.junit)
testImplementation(libs.truth)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)

// Android module
testImplementation(libs.robolectric)

// Module with SQLDelight DAOs
testImplementation(libs.sqldelight.sqlite.driver)
```

---

## Infrastructure — `common:test-utils`

Located at `common/test-utils/`. Add as `testImplementation(project(":common:test-utils"))`.

| Class | Purpose |
|-------|---------|
| `TestDispatcherRule` | JUnit rule — injects `UnconfinedTestDispatcher` for coroutine tests |
| `RxSchedulerRule` | JUnit rule — makes all RxJava schedulers synchronous |
| `FlowTestExtensions` | Turbine-based helpers: `collectN`, `awaitSingle`, etc. |
| `TestDataFactory` | Real domain model fixtures: `SuraAyah`, `Bookmark`, `Tag`, `RecentPage` |
| `BaseCoroutineTest` | Abstract base: sets up `TestDispatcherRule` |
| `BaseRxTest` | Abstract base: sets up `RxSchedulerRule` |
| `BaseCombinedTest` | Abstract base: both rules |

```kotlin
// TestDataFactory usage
val bookmark = TestDataFactory.createBookmark(sura = 2, ayah = 255)
val position = TestDataFactory.ayatAlKursi()  // SuraAyah(2, 255)

// TestDispatcherRule usage (composition over inheritance)
class MyPresenterTest {
    @get:Rule val dispatcher = TestDispatcherRule()
    @get:Rule val rx = RxSchedulerRule()
}
```

---

## Patterns

### Coroutine tests

Always use `runTest`. Never use `runBlocking` — it does not advance virtual time and is inherently racy with `Dispatchers.IO`.

```kotlin
@Test
fun `emits updated state after action`() = runTest {
    val presenter = MyPresenter(fakeRepo, this)  // pass TestScope as CoroutineScope
    presenter.doSomething()
    assertThat(presenter.state.value).isEqualTo(State.Done)
}
```

For presenters that own a `CoroutineScope`, use the **injectable scope pattern**:

```kotlin
class MyPresenter private constructor(
    private val repo: Repo,
    private val scope: CoroutineScope
) {
    @Inject constructor(repo: Repo) : this(repo, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    companion object {
        internal fun forTest(repo: Repo, scope: CoroutineScope) = MyPresenter(repo, scope)
    }
}

// In tests:
val presenter = MyPresenter.forTest(fakeRepo, this)  // 'this' is the TestScope
```

### Flow testing with Turbine

```kotlin
@Test
fun `emits states in order`() = runTest {
    repository.stateFlow.test {
        assertThat(awaitItem()).isEqualTo(State.Initial)
        repository.trigger()
        assertThat(awaitItem()).isEqualTo(State.Loading)
        assertThat(awaitItem()).isInstanceOf(State.Success::class.java)
        cancelAndIgnoreRemainingEvents()
    }
}
```

For **multiple concurrent collectors**, use `launch` — not nested `test {}` blocks (which are sequential):

```kotlin
@Test
fun `multiple collectors each receive events`() = runTest {
    val received1 = mutableListOf<Event>()
    val received2 = mutableListOf<Event>()
    val job1 = launch { flow.collect { received1 += it } }
    val job2 = launch { flow.collect { received2 += it } }
    yield()
    repository.emit(Event.Action)
    advanceUntilIdle()
    assertThat(received1).containsExactly(Event.Action)
    assertThat(received2).containsExactly(Event.Action)
    job1.cancel(); job2.cancel()
}
```

`MutableSharedFlow` in fakes should use **no replay** (`replay = 0`, the default) to match production semantics. `replay = 1` masks bugs where collectors miss emissions.

`stateIn(scope, SharingStarted.Eagerly, default)` resolves synchronously inside `runTest` — no `yield()` or `advanceUntilIdle()` needed for the initial value.

For single-emission flows, prefer `flow.first()` over `flow.toList().flatten()` — it is explicit about the expectation and cancels the flow immediately after the first item.

### RxJava testing

```kotlin
class BookmarkModelTest {
    @get:Rule val rxRule = RxSchedulerRule()

    @Test
    fun `emits bookmarks`() {
        val fakeDao = FakeBookmarksDao()
        fakeDao.setBookmarks(listOf(testBookmark()))
        val model = BookmarkModel(fakeDao)

        val observer = model.bookmarksObservable().test()
        observer.assertNoErrors()
        observer.assertValue { it.size == 1 }
    }
}
```

### MVP presenter testing

```kotlin
class BookmarkPresenterTest {
    private val fakeModel = FakeBookmarkModel()
    private val fakeView = FakeBookmarkScreen()
    private lateinit var presenter: BookmarkPresenter

    @Before fun setup() {
        presenter = BookmarkPresenter(fakeModel)
    }

    @Test
    fun `loads bookmarks on bind`() = runTest {
        fakeModel.setBookmarks(listOf(testBookmark()))
        presenter.bind(fakeView)
        assertThat(fakeView.displayedBookmarks).hasSize(1)
    }
}
```

### In-memory SQLite (DAO tests)

```kotlin
private lateinit var db: BookmarksDatabase
private lateinit var dao: BookmarksDaoImpl

@Before fun setup() {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BookmarksDatabase.Schema.create(driver)
    db = BookmarksDatabase(driver, /* column adapters */)
    dao = BookmarksDaoImpl(db)
}
```

### Robolectric for Android-dependent classes

Use Robolectric when the class under test has Android dependencies that cannot be injected (e.g. `QuranSettings`, which has a private constructor and uses `SharedPreferences`):

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class MyPresenterTest {
    @Before fun setup() {
        QuranSettings.setInstance(null)  // reset singleton before each test
    }

    @After fun teardown() {
        QuranSettings.setInstance(null)
    }

    @Test fun `reads setting correctly`() {
        val settings = QuranSettings.getInstance(ApplicationProvider.getApplicationContext())
        settings.setLastUpdatedTranslationDate(0L)
        // ...
    }
}
```

---

## Conventions

### Test naming

Use backtick names that read as a sentence describing the behavior:

```kotlin
@Test fun `returns empty list when no bookmarks exist`()
@Test fun `emits error state when network fails`()
@Test fun `does not include ayah bookmark in page-only query`()
```

### Test structure — AAA

```kotlin
@Test
fun `adds bookmark successfully`() = runTest {
    // Arrange
    val bookmark = TestDataFactory.createBookmark(sura = 2, ayah = 255)

    // Act
    repository.addBookmark(bookmark)

    // Assert
    assertThat(repository.getBookmarks()).contains(bookmark)
}
```

### What to test

**Do test:**
- Business logic in presenters and models
- Data transformations and state machines
- Error handling paths
- Edge cases and boundary values
- Flow emission ordering and change notifications

**Don't test:**
- Private methods directly
- Simple data classes
- Generated code (DI factories, databinding)
- Third-party library internals

### Flaky test prevention

- Never use `Thread.sleep()` — use `advanceTimeBy()` or `runCurrent()`
- Always use `TestDispatcherRule` or pass `TestScope` for coroutines
- Always use `RxSchedulerRule` for RxJava
- Reset state in `@After` or use `@Before` to re-initialize
- Use `JdbcSqliteDriver.IN_MEMORY` for database tests — never share state between tests

---

## Coverage Configuration

Kover is configured in `app/build.gradle.kts`:

```kotlin
kover {
    reports {
        filters {
            excludes {
                classes("*_Factory", "*_MembersInjector", "*_Module", "*.BuildConfig", "*.R")
                packages("*.di.*", "*.generated.*")
            }
        }
    }
}
```

Generate a coverage report:

```bash
./gradlew koverHtmlReportMadaniDebug -PdisableFirebase
```

---

## Module Coverage Targets

| Priority | Module | Target |
|----------|--------|--------|
| P0 | `common:data` | 90% |
| P0 | `common:bookmark` | 90% |
| P1 | `app/presenter/*` | 80% |
| P1 | `common:audio` | 80% |
| P2 | `common:search` | 70% |
| P2 | `feature:downloadmanager` | 70% |
| P3 | `feature:qarilist` | 60% |
| P3 | `feature:linebyline` | 60% |
| P4 | `feature:audiobar` | 60% |

---

## Implementation History

### Phase 1 — Infrastructure
**PR #3520** · merged 2026-02-22

Established the foundation before writing any domain tests.

- **Kover plugin** — coverage reporting with exclusions for generated code
- **`common:test-utils` module** — `TestDispatcherRule`, `RxSchedulerRule`, `FlowTestExtensions`, `TestDataFactory`, base test classes
- **MockK removed** — fakes-over-mocks policy adopted from day one per maintainer guidance
- **Pre-existing broken audio tests fixed** — clean baseline

Result: 128 tests passing.

---

### Phase 2 — Bookmark Database
**PR #3538** · merged 2026-03-25

Tested the data layer with real in-memory SQLite — zero mocks.

- **19 new tests** in `common:bookmark` (`BookmarksDaoImpl`)
  - Bookmark CRUD, toggle, queries, transactional operations
  - Flow emission and change notifications
  - Recent pages: add, retrieve, limit, remove
- **`BookmarkModel` and `RecentPageModel` migrated** from Java + Mockito to Kotlin + `JdbcSqliteDriver.IN_MEMORY`
- `@BeforeClass` scheduler pollution eliminated via `RxSchedulerRule`

---

### Phase 3 — Presenter Tests and Mockito Migration
**PR #3539** · merged 2026-03-27

Presenter tests with fakes; Mockito eliminated from the `app` module.

**New tests:**

| Class | Tests | Notes |
|-------|-------|-------|
| `QuranPagePresenter` | 8 | Page loading, coordinates, image download, lifecycle |
| `AudioPresenter` | 13 | Playback, streaming, downloads, permissions, gapless/gapped |
| `QuranImportPresenter` | — | Robolectric + `ShadowContentResolver` |
| `BaseTranslationPresenter` | — | `FakeTranslationModel`, coroutine exception paths |
| `AudioUtils`, `ArabicDatabaseUtils`, `BookmarkImportExportModel` | — | Robolectric |

**Fakes built:**
`FakeBookmarksDBAdapter`, `FakeBookmarkModel`, `FakeRecentPageModel`, `FakeQariUtil`, `FakeQuranFileUtils`, `FakeTranslationModel`, `FakeTranslationsDBAdapter`, `FakeTranslationListPresenter`, `FakeContentResolver`, `FakeQuranPageScreen`, `FakePagerActivity`, `FakeTagBookmarkDialog`, `FakeCoordinatesModel`, `FakeQuranPageLoader`, `FakeAudioExtensionDecider`, `FakeQuranDisplayData`

**Production changes:**
- `TranslationModel`, `TranslationsDBAdapter`, `TranslationListPresenter` — interface extracted from concrete class (for testability)
- `BookmarksDao.changes`: `Flow<Long>` → `Flow<Unit>` — timestamps were never consumed; also fixed a pre-existing double-emit that was masked by `StateFlow` conflation

---

### Phase 4 — Feature Module Tests
**PR #3542** · merged 2026-03-29

Broad coverage across feature modules that previously had zero tests.

**New tests (154 total):**

| Module | Tests | Classes |
|--------|-------|---------|
| `common:search` | 55 | `SearchTextUtil`, `ArabicCharacterHelper`, `DefaultSearcher`, `ArabicSearcher` |
| `feature:linebyline` | 44 | `LineCalculation`, `AyahSelectionExtension`, `SelectionHelper`, `QuranLineByLineSettingsPresenter` |
| `common:audio` | 19 | `PartiallyDownloadedSuraExtension`, `AudioStatusRepository` |
| `feature:audio` | 17 | `AudioFileCheckerImpl` |
| `feature:audiobar` | 12 | `AudioBarEventRepository` |
| `feature:qarilist` | 7 | `QariListPresenter` |

**Production changes:**
- `QariDownloadInfoSource` interface extracted; `QariListPresenter` depends on interface; DI binding added in `ApplicationModule`
- `QuranLineByLineSettingsPresenter` — injectable `CoroutineScope` via private primary constructor + `@Inject` secondary + `internal forTest()` companion factory

**New fakes:** `FakeSettings`, `FakeQariDownloadInfoSource`, `FakeHashCalculator`

---

### Phase 5 — Mockito Elimination in `app` Presenters
**PR #3603** · merged 2026-04-02

Eliminated all remaining Mockito usage from `BookmarkPresenterTest` and `TranslationManagerPresenterTest` in the `app` module.

**Changes:**

| File | Change |
|------|--------|
| `BookmarkPresenterTest` | Replaced 5 `@Mock` fields with Robolectric `QuranSettings` + `FakeBookmarkModel`; all 6 anonymous `BookmarkModel` subclasses replaced by `fakeModel.setTags/setBookmarks/setRecentPages`; 8 new tests added |
| `TranslationManagerPresenterTest` | Replaced `mock(QuranSettings)`, `mock(Context)`, `mock(TranslationsDBAdapter)`, `mock(QuranFileUtils)` with Robolectric equivalents + fakes; `mergeWithServerTranslations` override and its TODO removed; 5 new tests added |
| `FakeQuranFileUtils` (new) | Factory object constructing a real `QuranFileUtils` backed by Robolectric; uses `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY)` (non-deprecated, API 17+) |

**Key decisions:**
- `QuranSettings` has a private constructor — Robolectric provides an in-memory `SharedPreferences` context; `@VisibleForTesting setInstance(null)` used in `@Before`/`@After` for isolation
- `TranslationItem.exists()` returns `localVersion > 0` — tests use `localVersion = 1` to signal "installed"
- `mergeWithServerTranslations` now runs the real implementation: Robolectric's path has no `.db` files, so `translationExists = false` and no DB updates are triggered

---

### Phase 6 — Zero Mockito in `app/src/test`
**branch: feature/testing-phase6** · 2026-04-03

Achieved zero `@Mock` annotations and zero Mockito imports across all of `app/src/test`. Migrated five test files and extracted six new production interfaces.

**Production changes (testability surface):**

| File | Change |
|------|--------|
| `CoordinatesModelInterface` (new) | Interface for `CoordinatesModel`; bound via `@ContributesBinding(ActivityScope::class)` |
| `QuranPageLoaderInterface` (new) | Interface for `QuranPageLoader`; same binding pattern |
| `AudioPresenterScreen` (new) | Interface replacing `PagerActivity` in `AudioPresenter<T>` |
| `AudioUtilsInterface` (new) | Interface for `AudioUtils` covering 5 audio-path methods |
| `AudioFileUtils` (new) | Interface for aya-position and gapless database file utilities |
| `QuranDisplayInterface` (new) | Interface for `QuranDisplayData.getNotificationTitle` |
| `AudioPresenter` | Constructor gains `@ApplicationContext appContext: Context`; `Presenter<PagerActivity>` becomes `Presenter<AudioPresenterScreen>` |
| `TagBookmarkDialog.showAddTagDialog()` | Added `open` modifier for subclassing in tests |
| `ApplicationModule` | Added `@Provides` bindings for the three new interfaces |

**New fakes (all in `app/src/test/.../fakes/`):**

| Fake | Purpose |
|------|---------|
| `FakeCoordinatesModel` | Configurable page/ayah coordinate observables |
| `FakeQuranPageLoader` | Records loaded pages; configurable result observable |
| `FakeQuranPageScreen` | Records all `QuranPageScreen` callbacks |
| `FakeAudioPresenterScreen` | Records `handlePlayback`, `handleRequiredDownload`, `proceedWithDownload` calls |
| `FakeAudioUtils` | Map-based stubs for all `AudioUtilsInterface` methods |
| `FakeAudioFileUtils` | Configurable boolean/string/File fields for file-check methods |
| `FakeQuranDisplayData` | Returns configurable `notificationTitleResult` |
| `FakeAudioExtensionDecider` | Map-based stubs for extension lookup |
| `FakeContentResolver` | Extends `ContentResolver(null)`; configurable stream/fd/exception |
| `FakeTagBookmarkDialog` | Extends `TagBookmarkDialog`; counts `showAddTagDialog` calls |

**Migrated test files:**

| Test | What changed |
|------|-------------|
| `QuranPagePresenterTest` | Full Robolectric rewrite; all 9 tests use fakes; no Mockito |
| `AudioPresenterTest` | 12 tests; `@Mock` fields replaced with fakes; `ArgumentCaptor` removed; `verify()` replaced with list-size assertions on `fakeScreen` |
| `QuranImportPresenterTest` | `BookmarkImportExportModel` constructed directly (internal constructor accessible from same module); `mock(ContentResolver)` + `mock(Context)` replaced with `FakeContentResolver` + `ContextWrapper` |
| `TagBookmarkPresenterTest` | `mock(TagBookmarkDialog)` + `verify(bookmarkDialog).showAddTagDialog()` replaced with `FakeTagBookmarkDialog` + count assertion |
| `ArabicDatabaseUtilsTest` | `@Mock DatabaseHandler` removed; anonymous subclass overrides `getArabicDatabaseHandler()` to return `null`; deprecated `WindowManager.defaultDisplay` replaced |

**Key decisions:**
- `ServiceIntentHelper.getAudioDownloadIntent` requires a real Context — injecting `@ApplicationContext` into `AudioPresenter` avoids a Context leak from the screen to the service layer
- `BookmarkImportExportModel` uses `internal constructor` — accessible from test code in the same Gradle module without reflection
- `FakeContentResolver` throws from `openFileDescriptor` instead of returning a mock `ParcelFileDescriptor` — same NPE observable error, cleaner mechanism
- MadaniDataSource has 604 pages — page 605 is naturally invalid, eliminating the need to mock `QuranInfo.isValidPage` in the filter test

---

## Cumulative Impact

| Metric | Value |
|--------|-------|
| PRs merged | 5 (#3520, #3538, #3539, #3542, #3603) + Phase 6 branch |
| Net new tests | 413+ |
| Modules with coverage added | 11 |
| Mockito files eliminated or migrated | ~17 |
| Fakes created | 32+ |
| Production interface extractions | 12 |
| `@Mock` annotations remaining in `app/src/test` | **0** |

---

## Roadmap

| Phase | Focus | Details |
|-------|-------|---------|
| 7 | `feature:downloadmanager` | 27 source files, 0 tests — highest-value untested module |
| 8 | `QariDownloadInfoManager` depth | Core audio state machine; interface already extracted in Phase 4 |
| 9 | Kover CI enforcement | Wire coverage gates into CI to prevent regression |
| 10 | `app` presenter depth | `JuzListPresenter`, `CoordinatesModel`, `AyahTrackerPresenter` |

---

## References

- [Android Testing Documentation](https://developer.android.com/training/testing)
- [Testing Kotlin Coroutines](https://developer.android.com/kotlin/coroutines/test)
- [Turbine](https://github.com/cashapp/turbine)
- [Kover](https://github.com/Kotlin/kotlinx-kover)
- [Test Doubles (Fakes vs Mocks)](https://testing.googleblog.com/2013/07/testing-on-toilet-know-your-test-doubles.html)

---

*Last updated: 2026-04-02*
