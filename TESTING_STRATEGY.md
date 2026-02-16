# Testing Strategy for Quran Android

## Executive Summary

**Phase 2 Complete**: Core testing infrastructure established with 33 new tests. Current focus: Presenter layer testing with pragmatic fake/mock strategy.

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

```kotlin
// TestDataFactory usage
val bookmark = TestDataFactory.createBookmark(sura = 2, ayah = 255)
val suraAyah = TestDataFactory.ayatAlKursi()  // 2:255
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

### Phase 3: Mockito Migration (Next)
- [ ] Analyze existing Mockito usage (~7 test files, ~100 calls)
- [ ] Migrate tests to fakes where beneficial
- [ ] Create fakes for commonly mocked dependencies
- [ ] Reduce Mockito dependency footprint

### Phase 4: Feature Modules
- [ ] Audio, Search, Download module tests

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

## Current Status (Phase 2 Complete)

**Test Count**: 33 new tests added
- QuranInfo: 21 tests (domain logic)
- BookmarksDaoImpl: 16 tests (DAO layer, in-memory SQLite)
- QuranPagePresenter: 9 tests (presenter logic, RxJava)
- AudioPresenter: 8 tests (audio logic, streaming)

**Patterns Established**:
- Fakes over mocks philosophy documented
- TestDataFactory for consistent fixtures
- RxSchedulerRule for synchronous RxJava
- Pragmatic mocking for framework classes

**Next**: Phase 3 - Mockito migration analysis

---

*Last Updated: 2026-02-15*
