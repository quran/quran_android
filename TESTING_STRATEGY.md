# Testing Strategy for Quran Android

## Executive Summary

**Phase 4 Complete**: Feature module tests added. 289 tests total, 0 flaky. 154 new tests across 6 modules in Phase 4.

---

## Testing Philosophy

### Core Principles

1. **Test Behavior, Not Implementation** - Focus on inputs/outputs, not internal state
2. **Fast Feedback Loop** - Unit tests <10s, integration tests <2min, CI <10min
3. **Deterministic & Isolated** - No flaky tests, no test interdependencies
4. **Fakes Over Mocks** - Prefer simple fake implementations over mock verification

### Fakes Philosophy

This project **prefers fakes over mocks**:

```kotlin
// PREFERRED: Fake implementation
class FakeBookmarkDao : BookmarkDao {
    private val bookmarks = mutableListOf<Bookmark>()
    override suspend fun getBookmarks(): List<Bookmark> = bookmarks.toList()
    override suspend fun addBookmark(bookmark: Bookmark) { bookmarks.add(bookmark) }
}

// Use in tests
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

Fakes are simpler, more readable, and survive refactoring better than mock-based tests.

---

## Test Pyramid

```
              ╱╲
             ╱E2E╲        5% (10-15 tests)
            ╱──────╲
           ╱Integration╲  20% (50-100 tests)
          ╱──────────────╲
         ╱  Unit Tests    ╲ 75% (300-500 tests)
```

| Type | Scope | Characteristics |
|------|-------|-----------------|
| **Unit** | Single class/function | JVM-only, fakes, <100ms/test |
| **Integration** | Multiple components | Robolectric, real DBs, MockWebServer |
| **E2E** | User journeys | Emulator, Compose testing, Espresso |

---

## Testing Stack

| Framework | Purpose |
|-----------|---------|
| JUnit 4 | Test runner |
| Truth | Assertions |
| Fakes | Test doubles (preferred) |
| Turbine | Flow testing |
| Robolectric | Android unit tests |
| Kover | Coverage reporting |
| MockWebServer | Network testing |

### Test Dependencies

```kotlin
// Pure Kotlin module
testImplementation(libs.junit)
testImplementation(libs.truth)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)

// Android module
testImplementation(libs.robolectric)
```

---

## Test Infrastructure

### test-utils Module

Located at `common/test-utils/`, provides:

- **TestDataFactory** - Creates real domain models for tests
- **RxSchedulerRule** - Synchronous RxJava execution
- **DatabaseTestHelpers** - Shared in-memory database factory (`inMemoryBookmarksAdapter()`) used by bookmark test classes

```kotlin
// TestDataFactory usage
val bookmark = TestDataFactory.createBookmark(sura = 2, ayah = 255)
val suraAyah = TestDataFactory.ayatAlKursi()  // 2:255
```

### Phase 4 Fakes

New fakes introduced in Phase 4:

- **FakeSettings** (`feature:linebyline/src/test`) — implements `com.quran.data.dao.Settings` backed by a MutableMap; used by `QuranLineByLineSettingsPresenter` tests
- **FakeQariDownloadInfoSource** (`feature:qarilist/src/test`) — implements `QariDownloadInfoSource` via `MutableStateFlow`; used by `QariListPresenter` tests
- **FakeHashCalculator** (`feature:audio/src/test`) — implements `HashCalculator` with deterministic results; used by `AudioFileCheckerImpl` tests

### File System Testing

Use `TemporaryFolder` (JUnit rule) for classes that use `java.io.File` directly:

```kotlin
class AudioFileCheckerImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `file exists when present on disk`() {
        val file = tempFolder.newFile("audio.mp3")
        // Use real file, not fake filesystem
    }
}
```

### JUnit Rules (Composition over Inheritance)

```kotlin
class MyPresenterTest {
    @get:Rule
    val rxRule = RxSchedulerRule()  // For RxJava tests

    @Test
    fun `test rx behavior`() {
        // RxJava schedulers now execute synchronously
    }
}
```

---

## Testing Patterns

### MVP Presenter Testing

```kotlin
class BookmarkPresenterTest {
    private val fakeModel = FakeBookmarkModel()
    private val fakeView = FakeBookmarkScreen()
    private lateinit var presenter: BookmarkPresenter

    @Before
    fun setup() {
        presenter = BookmarkPresenter(fakeModel)
    }

    @Test
    fun `should load bookmarks when bound`() = runTest {
        fakeModel.setBookmarks(listOf(testBookmark()))
        presenter.bind(fakeView)
        assertThat(fakeView.displayedBookmarks).hasSize(1)
    }
}
```

### Flow Testing with Turbine

```kotlin
@Test
fun `should emit states in order`() = runTest {
    repository.stateFlow.test {
        assertThat(awaitItem()).isEqualTo(State.Initial)
        repository.doSomething()
        assertThat(awaitItem()).isEqualTo(State.Loading)
        assertThat(awaitItem()).isInstanceOf(State.Success::class.java)
        cancelAndIgnoreRemainingEvents()
    }
}
```

### RxJava Testing

```kotlin
class BookmarkModelTest {
    @get:Rule
    val rxRule = RxSchedulerRule()

    @Test
    fun `should emit bookmarks`() {
        val fakeDao = FakeBookmarksDao()
        fakeDao.setBookmarks(listOf(testBookmark()))
        val model = BookmarkModel(fakeDao)

        val testObserver = model.bookmarksObservable().test()
        testObserver.assertNoErrors()
        testObserver.assertValue { it.size == 1 }
    }
}
```

---

## Module Priorities

| Priority | Module | Target Coverage |
|----------|--------|-----------------|
| **P0** | common:data | 90% |
| **P0** | common:bookmark | 90% |
| **P1** | app/presenter/* | 80% |
| **P1** | common:audio | 80% |
| **P2** | common:search | 70% |
| **P2** | feature:downloadmanager | 70% |
| **P3** | feature:qarilist | 60% |
| **P4** | feature:autoquran | 40% |

---

## Coverage Configuration

Kover is configured in `app/build.gradle.kts` with exclusions for generated code:

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

Run coverage: `./gradlew koverHtmlReportMadaniDebug -PdisableFirebase`

---

## Conventions

### Test Naming

```kotlin
@Test
fun `should return empty list when no bookmarks exist`()

@Test
fun `should emit error state when network fails`()
```

### Test Structure (AAA Pattern)

```kotlin
@Test
fun `should add bookmark successfully`() = runTest {
    // Arrange
    val bookmark = TestDataFactory.createBookmark(sura = 2, ayah = 255)

    // Act
    repository.addBookmark(bookmark)

    // Assert
    assertThat(repository.getBookmarks()).contains(bookmark)
}
```

### What to Test

**DO Test:**
- Business logic in presenters/models
- Data transformations and state machines
- Error handling paths
- Edge cases and boundaries

**DON'T Test:**
- Private methods directly
- Simple data classes
- Generated code (DI, databinding)
- Third-party libraries

### Flaky Test Prevention

1. **Timing**: Prefer `advanceTimeBy()` or `runCurrent()` over `Thread.sleep()`
   - Exception: RxJava timers with trampoline scheduler may need small sleeps
2. **Coroutines**: Always use test dispatchers (`runTest`, `TestScope`)
3. **RxJava**: Use `RxSchedulerRule` or `trampoline()` for synchronous execution
4. **Network**: Use MockWebServer for deterministic responses
5. **State**: Reset mocks and state in `@After` hooks

---

## Implementation Roadmap

### Phase 1: Foundation ✅ **COMPLETE**
- [x] Add Kover plugin and configure coverage
- [x] Create common:test-utils module with TestDataFactory
- [x] Add RxSchedulerRule for synchronous RxJava testing
- [x] Write QuranInfo tests (21 tests)
- [x] Remove MockK dependency, establish fakes-over-mocks pattern
- [x] Write BookmarksDaoImpl tests (16 tests)

**PR**: [#3520](https://github.com/quran/quran_android/pull/3520)

### Phase 2: Core Business Logic ✅ **COMPLETE**
- [x] Document fake patterns and circular dependency solutions
- [x] Create presenter tests (17 tests total):
  - [x] QuranPagePresenter (9 tests) - RxJava, async timers, lifecycle
  - [x] AudioPresenter (8 tests) - Audio logic, streaming, permissions
- [x] Establish pragmatic mocking strategy for framework classes
- [x] Add test-utils dependency to app module

**Branch**: `feature/testing-phase2`

**Key Decisions**:
- Framework-dependent classes (QuranSettings) → Mockito
- Intent creation tests → Deferred (requires Robolectric)
- Test data → TestDataFactory for consistency

### Phase 3: Mockito Migration ✅ **COMPLETE**
- [x] Analyze existing Mockito usage (~7 test files, ~100 calls)
- [x] Migrate tests to fakes where beneficial
- [x] Create fakes for commonly mocked dependencies
- [x] Reduce Mockito dependency footprint

**Branch**: `feature/testing-infrastructure-phase1`

**Key Decisions**:
- Fakes created: FakeBookmarkModel, FakeRecentPageModel, FakeTranslationModel,
  FakeTranslationsDBAdapter, FakeTranslationListPresenter, FakePageProvider, FakeBookmarksDBAdapter
- Interface extraction (DIP): TranslationModel, TranslationsDBAdapter, TranslationListPresenter
- Robolectric adopted for SQLite-backed tests (fixes JDBC classloader flakiness)
- Remaining Mockito (10 files): all justified — `verify()` interaction tests and
  unavoidable mocks for non-open classes (DatabaseHandler, ShadowContentResolver gaps)

### Phase 4: Feature Modules ✅ **COMPLETE**
- [x] common:search tests (55 tests): SearchTextUtil, ArabicCharacterHelper, DefaultSearcher, ArabicSearcher
- [x] feature:linebyline tests (44 tests): LineCalculation, AyahSelectionExtension, SelectionHelper, QuranLineByLineSettingsPresenter
- [x] feature:qarilist tests (7 tests): QariListPresenter + QariDownloadInfoSource interface extraction
- [x] feature:audiobar tests (12 tests): AudioBarEventRepository
- [x] common:audio tests (19 tests): PartiallyDownloadedSuraExtension, AudioStatusRepository, GaplessAudioInfoCommand, GappedAudioInfoCommand
- [x] feature:audio tests (17 tests): AudioFileCheckerImpl, MD5Calculator, AudioUpdater

**Branch**: `feature/testing-phase4`

**Key Decisions**:
- `QariDownloadInfoSource` interface extracted to `common:audio` (not `feature:qarilist`) to avoid circular dependency
- `QariDownloadInfoManager` implements `QariDownloadInfoSource`; `QariListPresenter` depends on interface (DIP)
- `TemporaryFolder` (JUnit rule) used for classes that use `java.io.File` directly; `FakeFileSystem` not suitable here
- Fakes created: `FakeSettings`, `FakeQariDownloadInfoSource`, `FakeHashCalculator`

### Phase 5: Integration & E2E
- [ ] Database integration tests
- [ ] Critical user journey E2E tests

---

## References

- [Android Testing Documentation](https://developer.android.com/training/testing)
- [Testing Kotlin Coroutines](https://developer.android.com/kotlin/coroutines/test)
- [Turbine](https://github.com/cashapp/turbine)
- [Kover](https://github.com/Kotlin/kotlinx-kover)
- [Fakes vs Mocks](https://testing.googleblog.com/2013/07/testing-on-toilet-know-your-test-doubles.html)

---

## Current Status (Phase 4 Complete)

**Test Count**: 289 tests total, 0 failures, 0 flaky

**Phase 1-3 (146 tests)**:
- QuranInfo: 21 tests (domain logic)
- BookmarksDaoImpl: 16 tests (DAO layer, in-memory SQLite)
- QuranPagePresenter: 9 tests (presenter logic, RxJava)
- AudioPresenter: 13 tests (audio logic, streaming, all download paths)
- BookmarkModel: 2 tests (in-memory SQLite)
- RecentPageModel: 6 tests (in-memory SQLite)
- TagBookmarkPresenter: 5 tests
- QuranImportPresenter: 4 tests (Robolectric + ShadowContentResolver)
- BaseTranslationPresenter: 4 tests (fakes, Robolectric context, covers getVerses$2 lambda)
- ArabicDatabaseUtils: tests migrated to Robolectric real context
- BookmarkImportExportModel: 4 tests (Robolectric, import + export paths covered)
- AudioUtils: tests migrated to Robolectric real context

**Phase 4 (143+ tests)**:
- SearchTextUtil: 19 tests (common:search)
- ArabicCharacterHelper: 15 tests (common:search)
- DefaultSearcher: 11 tests (common:search)
- ArabicSearcher: 10 tests (common:search)
- LineCalculation: 14 tests (feature:linebyline)
- SelectionHelper: 12 tests (feature:linebyline)
- QuranLineByLineSettingsPresenter: 10 tests (feature:linebyline)
- AyahSelectionExtension: 8 tests (feature:linebyline)
- QariListPresenter: 7 tests (feature:qarilist)
- AudioBarEventRepository: 12 tests (feature:audiobar)
- PartiallyDownloadedSuraExtension: 8 tests (common:audio)
- AudioStatusRepository: 5 tests (common:audio)
- GaplessAudioInfoCommand: 3 tests (common:audio)
- GappedAudioInfoCommand: 3 tests (common:audio)
- AudioFileCheckerImpl: 12 tests (feature:audio)
- AudioUpdater: 4 tests (feature:audio)
- MD5Calculator: 1 test (feature:audio)

**Patterns Established**:
- Fakes over mocks philosophy documented and implemented
- TestDataFactory for consistent fixtures
- RxSchedulerRule for synchronous RxJava
- Robolectric for Android context + SQLite (fixes JDBC classloader flakiness)
- In-memory SQLite (JdbcSqliteDriver.IN_MEMORY) for database tests
- Interface extraction (DIP) for testability without `open` classes
- DatabaseTestHelpers for shared in-memory adapter factory (DRY across bookmark test classes)
- `FakeSettings` — implements `com.quran.data.dao.Settings` with a MutableMap backend
- `FakeQariDownloadInfoSource` — MutableStateFlow-backed fake for `QariDownloadInfoSource`
- `FakeHashCalculator` — deterministic hash fake for `AudioFileCheckerImpl` tests
- `TemporaryFolder` (JUnit rule) preferred over `FakeFileSystem` for classes using `java.io.File`

**Next**: Phase 5 - Integration & E2E tests

---

*Last Updated: 2026-02-19*
