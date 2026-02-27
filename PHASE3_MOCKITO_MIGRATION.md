# Phase 3: Mockito Migration Analysis

## Executive Summary

**Goal**: Reduce Mockito usage in favor of fakes and real implementations where beneficial.

**Current State**:
- 12 files use Mockito (10 pre-existing + 2 from Phase 2)
- ~220 total Mockito calls across all files
- Heavy database mocking (BookmarksDBAdapter) - good migration candidates

**Target**: Reduce pre-existing Mockito files from 10 to 5-7

---

## Pre-existing Mockito Usage

| File | Mock Calls | Priority | Migration Approach |
|------|-----------|----------|-------------------|
| QuranImportPresenterTest.kt | 22 | Medium | Keep Mockito (complex I/O logic) |
| TranslationManagerPresenterTest.kt | 21 | Low | Keep (already uses MockWebServer well) |
| AudioUtilsTest.kt | 16 | Medium | Evaluate (file system operations) |
| **BookmarkPresenterTest.kt** | **14** | **High** | **Create FakeBookmarkModel** |
| **BookmarkModelTest.java** | **13** | **High** | **Use in-memory SQLite** |
| **RecentPageModelTest.java** | **12** | **High** | **Use in-memory SQLite** |
| TagBookmarkPresenterTest.kt | 10 | Medium | Evaluate after BookmarkModel migration |
| ArabicDatabaseUtilsTest.kt | 6 | Low | Keep (minimal mocking) |
| BaseTranslationPresenterTest.kt | 4 | Low | Keep (base class) |
| BookmarkImportExportModelTest.java | 4 | Low | Keep (I/O heavy) |

---

## High-Priority Migration Candidates

### 1. BookmarkModelTest.java (13 mocks)

**Current State**:
- Mocks: `BookmarksDBAdapter`, `RecentPageModel`
- Tests: Tag operations, bookmark updates
- Pattern: `when(adapter.method()).thenReturn(value)`

**Migration Plan**:
```kotlin
class BookmarkModelTest {
  private lateinit var database: BookmarksDatabase
  private lateinit var adapter: BookmarksDBAdapter
  private lateinit var model: BookmarkModel

  @Before
  fun setup() {
    // Use in-memory SQLite like BookmarksDaoImplTest
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    BookmarksDatabase.Schema.create(driver)
    database = BookmarksDatabase(driver, ...)
    adapter = BookmarksDBAdapter(database)
    model = BookmarkModel(adapter, recentPageModel)
  }
}
```

**Benefits**:
- Tests real database behavior
- Catches SQL bugs
- More confidence in integration

**Effort**: Medium (2-3 hours)

### 2. RecentPageModelTest.java (12 mocks)

**Current State**:
- Mocks: `BookmarksDBAdapter`
- Tests: Recent pages retrieval, updates, observables
- RxJava heavy

**Migration Plan**:
Same as BookmarkModelTest - use real in-memory database.

**Benefits**:
- Tests actual database queries
- Tests RxJava flow with real data

**Effort**: Low (1-2 hours, similar to BookmarksDaoImplTest pattern)

### 3. BookmarkPresenterTest.kt (14 mocks)

**Current State**:
- Mocks: `BookmarkModel`, `BookmarksDBAdapter`, `Context`, `Resources`
- Tests: Presenter logic, sorting, grouping

**Migration Plan**:
```kotlin
// Create fake in app/src/test/.../fakes/
class FakeBookmarkModel : BookmarkModel {
  private val bookmarks = mutableListOf<Bookmark>()
  private val tags = mutableListOf<Tag>()

  override fun getBookmarks(): Single<List<Bookmark>> = Single.just(bookmarks.toList())
  // ... implement other methods
}
```

**Benefits**:
- Simpler than mock setup
- More readable tests
- Can reuse fake in other presenter tests

**Effort**: Medium (3-4 hours for fake + test migration)

---

## Migration Strategy

### Phase 3.1: Database Tests (Week 1)
1. **Day 1-2**: Migrate RecentPageModelTest to in-memory SQLite
2. **Day 3-4**: Migrate BookmarkModelTest to in-memory SQLite
3. **Day 5**: Run full test suite, verify no regressions

**Success Criteria**: 2 fewer files using Mockito (from 10 to 8)

### Phase 3.2: Fake Creation (Week 2)
1. **Day 1-2**: Create FakeBookmarkModel
2. **Day 3-4**: Migrate BookmarkPresenterTest to use fake
3. **Day 5**: Evaluate TagBookmarkPresenterTest for migration

**Success Criteria**: 1-2 fewer files using Mockito (from 8 to 6-7)

### Phase 3.3: Evaluation (Week 3)
- Analyze remaining files
- Document decision for each (migrate vs keep)
- Update TESTING_STRATEGY.md

---

## Keep Mockito For

These scenarios justify keeping Mockito:

1. **Android Framework Classes**
   - `QuranSettings` (private constructor + SharedPreferences)
   - `Context`, `Resources` (Android-specific)
   - Intent creation (requires Robolectric or mocking)

2. **Complex External Dependencies**
   - Network clients (prefer MockWebServer)
   - File system operations (case-by-case)

3. **Verification-Heavy Tests**
   - Tests that need `ArgumentCaptor`
   - Tests that verify call order (`inOrder()`)
   - Tests using `spy()` for partial mocking

4. **Low-Effort, Low-Benefit Tests**
   - Tests with 4-5 mocks or fewer
   - Tests that are already clear and maintainable
   - Tests that would require significant rewrite for minimal gain

---

## Metrics & Goals

### Current (Post Phase 2)
- Test files: 27
- Mockito files: 12 (10 pre-existing + 2 new)
- Tests added in Phase 1-2: 33

### Target (Post Phase 3)
- Mockito files reduced to: 6-8 (50% reduction)
- New fakes created: 1-2 (FakeBookmarkModel + optional others)
- Database tests using real in-memory DB: 3 files

### Success Metrics
- All migrated tests pass
- No test coverage loss
- Improved test clarity (measured by code review)
- Reduced Mockito dependency footprint

---

## Implementation Notes

### In-Memory SQLite Pattern

```kotlin
@Before
fun setup() {
  val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
  BookmarksDatabase.Schema.create(driver)
  database = BookmarksDatabase(
    driver,
    Bookmarks.Adapter(IntColumnAdapter, IntColumnAdapter, IntColumnAdapter),
    Last_pages.Adapter(IntColumnAdapter)
  )
  // Create real adapter/DAO
  adapter = BookmarksDBAdapter(database)
}

@After
fun tearDown() {
  // Driver automatically cleaned up
}
```

### Fake Pattern

```kotlin
class FakeBookmarkModel(
  private val initialBookmarks: List<Bookmark> = emptyList()
) : BookmarkModel {
  private val bookmarks = mutableListOf<Bookmark>().apply { addAll(initialBookmarks) }

  override fun getBookmarks(): Single<List<Bookmark>> {
    return Single.just(bookmarks.toList())
  }

  // Implement other methods as needed
}
```

---

## Next Steps

1. **Review this analysis** with team/maintainer
2. **Start Phase 3.1** (Database test migration)
3. **Document learnings** in TESTING_STRATEGY.md
4. **Update PHASE2_FAKES_GUIDE.md** with new patterns discovered

---

*Created: 2026-02-15*
*Status: Analysis Complete, Ready for Implementation*
