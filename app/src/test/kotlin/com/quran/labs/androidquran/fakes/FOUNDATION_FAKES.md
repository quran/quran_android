# Foundation Fakes - Phase 1

## Overview

This document describes the foundational fake implementations created for Phase 1 of the testing infrastructure. These fakes are designed to be reused across multiple test files.

## Created Fakes

### 1. FakeBookmarksDBAdapter

**File:** `FakeBookmarksDBAdapter.kt`

**Type:** Standalone fake (does not extend BookmarksDBAdapter)

**Why Standalone?** The real `BookmarksDBAdapter` is a final class and cannot be extended. This fake mimics its interface with the same method signatures.

**Key Methods:**
- `getRecentPages()`, `addRecentPage()`, `replaceRecentRangeWithPage()`
- `updateTag(id, newName)`, `getTags()`, `addTag(name)`
- `tagBookmarks(bookmarkIds, tagIds, deleteNonTagged)`
- `getBookmarks(sortOrder)`, `getBookmarkTagIds(bookmarkId)`
- `bulkDelete(tagIds, bookmarkIds, untag)`

**Features:**
- In-memory storage (tags, bookmarks, recent pages, bookmark-tag associations)
- Configurable behavior (`setUpdateTagResult()`, `setTagBookmarksResult()`)
- Call tracking with assertions
- Sorting support (by date, by location)

**Used By:**
- TagBookmarkPresenterTest (via updateTag)
- BookmarkPresenterTest (via getRecentPages, tagBookmarks)
- QuranPagePresenterTest (indirectly via RecentPageModel)

### 2. FakeRecentPageModel

**File:** `FakeRecentPageModel.kt`

**Type:** Extends `RecentPageModel` (which is marked as `open`)

**Overridden Methods:**
- `getRecentPagesObservable(): Single<List<RecentPage>>`

**Features:**
- Overrides only the key method needed for testing
- Returns configured test data instead of database queries
- Call tracking
- No database dependency

**Used By:**
- BookmarkPresenterTest (via getRecentPagesObservable)
- QuranPagePresenterTest (via RecentPageModel reference)

**Design Note:** The real `RecentPageModel` has complex initialization logic with subjects and subscriptions. This fake simplifies it by just overriding the read method.

### 3. FakeBookmarkModel

**File:** `FakeBookmarkModel.kt`

**Type:** Extends `BookmarkModel` (which is marked as `open`)

**Overridden Methods:**
- `tagsObservable: Single<List<Tag>>`
- `getBookmarkDataObservable(sortOrder): Single<BookmarkData>`
- `updateBookmarkTags(bookmarkIds, tagIds, deleteNonTagged): Observable<Boolean>`
- `safeAddBookmark(sura, ayah, page): Observable<Long>`
- `getBookmarkTagIds(bookmarkIdSingle): Maybe<List<Long>>`

**Features:**
- Overrides key methods used in presenter tests
- In-memory state for tags, bookmarks, recent pages
- Configurable success/failure behavior
- RxJava observables match real implementation
- Call tracking with assertions

**Used By:**
- BookmarkPresenterTest (via getBookmarkDataObservable)
- TagBookmarkPresenterTest (via tagsObservable, updateBookmarkTags, getBookmarkTagIds, safeAddBookmark)

## Architecture Decisions

### Why Not Use Mockito?

**Before (Mockito approach):**
```kotlin
val mock = mock(BookmarksDBAdapter::class.java)
whenever(mock.getTags()).thenReturn(listOf(Tag(1, "Test")))
whenever(mock.updateTag(1, "Updated")).thenReturn(true)
whenever(mock.getRecentPages()).thenReturn(emptyList())
// ... configure every method
```

**After (Fake approach):**
```kotlin
val fake = FakeBookmarksDBAdapter()
fake.setTags(listOf(Tag(1, "Test")))
fake.setUpdateTagResult(true)
// Only configure what changes from defaults
```

**Benefits:**
1. **Stateful:** Fakes maintain state like real objects (add a tag, retrieve it later)
2. **Less brittle:** No need to configure every method call
3. **More readable:** Configuration methods are explicit
4. **Type-safe assertions:** `fake.assertTagUpdated(1, "Updated")`
5. **Reusable:** Same fake instance works across multiple test scenarios

### Extension vs Composition

| Fake | Strategy | Reason |
|------|----------|--------|
| FakeBookmarksDBAdapter | Standalone | Real class is final, cannot extend |
| FakeRecentPageModel | Extension | Real class is open, simple to override key method |
| FakeBookmarkModel | Extension | Real class is open, need to override multiple methods |

### Inheritance Hierarchy

```
RecentPageModel (open)
    └── FakeRecentPageModel (overrides getRecentPagesObservable)

BookmarkModel (open)
    └── FakeBookmarkModel (overrides 5 key methods)

BookmarksDBAdapter (final)
    ... FakeBookmarksDBAdapter (standalone, mimics interface)
```

## Usage Patterns

### Basic Setup

```kotlin
class MyPresenterTest {
  private lateinit var fakeAdapter: FakeBookmarksDBAdapter
  private lateinit var fakeRecentPageModel: FakeRecentPageModel
  private lateinit var fakeBookmarkModel: FakeBookmarkModel

  @Before
  fun setup() {
    fakeAdapter = FakeBookmarksDBAdapter()
    fakeRecentPageModel = FakeRecentPageModel()
    fakeBookmarkModel = FakeBookmarkModel()

    // Default test data
    fakeAdapter.setTags(listOf(Tag(1, "Important")))
    fakeRecentPageModel.setRecentPages(listOf(RecentPage(42, System.currentTimeMillis())))
  }

  @After
  fun teardown() {
    fakeAdapter.reset()
    fakeRecentPageModel.reset()
    fakeBookmarkModel.reset()
  }
}
```

### Configuring Behavior

```kotlin
@Test
fun `should handle update tag failure`() {
  // Configure fake to fail
  fakeAdapter.setUpdateTagResult(false)

  val result = presenter.updateTag(1, "New Name")

  assertThat(result).isFalse()
  fakeAdapter.assertTagUpdated(1, "New Name")
}
```

### Testing with RxJava

```kotlin
@Test
fun `should load bookmark data`() {
  fakeBookmarkModel.setTags(listOf(Tag(1, "Test")))
  fakeBookmarkModel.setBookmarks(listOf(bookmark1))

  fakeBookmarkModel.getBookmarkDataObservable(sortOrder)
    .test()
    .assertValue { data ->
      data.tags.size == 1 && data.bookmarks.size == 1
    }
}
```

### Call Tracking

```kotlin
@Test
fun `should track method calls`() {
  presenter.updateTag(1, "Name1")
  presenter.updateTag(2, "Name2")

  assertThat(fakeAdapter.getUpdateTagCallCount()).isEqualTo(2)
  fakeAdapter.assertTagUpdated(1, "Name1")
  fakeAdapter.assertTagUpdated(2, "Name2")
}
```

## Test Coverage

These fakes enable testing for:

1. **BookmarkPresenterTest** (3 dependencies)
   - FakeBookmarkModel → getBookmarkDataObservable
   - FakeRecentPageModel → getRecentPagesObservable
   - Settings mocks (existing)

2. **TagBookmarkPresenterTest** (2 dependencies)
   - FakeBookmarkModel → tagsObservable, updateBookmarkTags, getBookmarkTagIds, safeAddBookmark
   - Mock dialog (existing)

3. **QuranPagePresenterTest** (1 dependency, indirect)
   - FakeRecentPageModel → used internally by presenters

## Migration Path

### Existing Tests → Phase 1 Fakes

1. **Replace Mockito mocks:**
   ```kotlin
   // Before
   @Mock private lateinit var bookmarksAdapter: BookmarksDBAdapter

   // After
   private lateinit var fakeAdapter: FakeBookmarksDBAdapter
   ```

2. **Replace `whenever()` calls with configuration:**
   ```kotlin
   // Before
   whenever(bookmarksAdapter.getTags()).thenReturn(tags)

   // After
   fakeAdapter.setTags(tags)
   ```

3. **Add assertions:**
   ```kotlin
   // Before
   verify(bookmarksAdapter).updateTag(1, "Name")

   // After
   fakeAdapter.assertTagUpdated(1, "Name")
   ```

## Known Limitations

1. **FakeBookmarksDBAdapter is not a drop-in replacement**
   - Cannot pass it where `BookmarksDBAdapter` type is required
   - Tests must be refactored to inject it differently
   - Solution: Use dependency injection or constructor parameters

2. **Some methods not implemented**
   - Only methods used by current tests are implemented
   - Add more methods as needed for new tests

3. **Simplified behavior**
   - Fakes may not capture all edge cases of real implementation
   - Good for unit tests, use real database for integration tests

## Next Steps

1. **Phase 2:** Use these fakes in actual presenter tests
2. **Phase 3:** Add more assertion helpers as needed
3. **Phase 4:** Document patterns that emerge from usage

## Files Created

```
app/src/test/kotlin/com/quran/labs/androidquran/fakes/
├── FakeBookmarksDBAdapter.kt          (9.6 KB, 360 lines)
├── FakeRecentPageModel.kt             (2.0 KB, 80 lines)
├── FakeBookmarkModel.kt               (6.5 KB, 180 lines)
├── FakeBookmarksDBAdapterTest.kt      (4.2 KB, verification test)
├── README.md                          (6.7 KB, usage documentation)
└── FOUNDATION_FAKES.md                (this file)
```

**Total:** 3 production fakes, 1 test file, 2 documentation files

## Success Criteria

✅ All three fakes compile successfully
✅ Fakes are stateful and maintain in-memory state
✅ Configuration methods provided for test scenarios
✅ Call tracking with assertion helpers
✅ Documentation with usage examples
✅ Test file verifying fake behavior

## References

- Real implementation: `app/src/main/java/com/quran/labs/androidquran/database/BookmarksDBAdapter.kt`
- Real implementation: `app/src/main/java/com/quran/labs/androidquran/model/bookmark/RecentPageModel.kt`
- Real implementation: `app/src/main/java/com/quran/labs/androidquran/model/bookmark/BookmarkModel.kt`
- Existing test patterns: `app/src/test/java/com/quran/labs/androidquran/presenter/bookmark/TagBookmarkPresenterTest.kt`
