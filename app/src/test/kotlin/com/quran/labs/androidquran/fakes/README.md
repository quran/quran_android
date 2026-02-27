# Test Fakes

This directory contains fake implementations of core application components for testing purposes.

## Important Note: Android Framework Classes

**Do NOT create fakes for Android framework classes** (Context, Resources, DatabaseHandler, etc.).

For Android framework classes:
1. Use **Mockito mocks** (recommended for unit tests)
2. Use **Robolectric** (for integration tests)
3. Use **test helpers** (see `../test/helpers/` directory)

See `ANDROID_FRAMEWORK_FAKES.md` for detailed explanation and `../test/helpers/README.md` for helper utilities.

## Overview

These fakes provide in-memory, stateful implementations that can be configured for different test scenarios. They track method calls for assertions and maintain state without requiring database setup.

## Available Fakes

### FakeBookmarksDBAdapter

A standalone fake (not extending the real class) that mimics the `BookmarksDBAdapter` interface.

**Key Features:**
- In-memory storage for bookmarks, tags, and recent pages
- Configurable behavior (success/failure scenarios)
- Call tracking for assertions
- No database dependency

**Usage Example:**
```kotlin
val fake = FakeBookmarksDBAdapter()

// Setup test data
fake.setTags(listOf(Tag(1, "Important"), Tag(2, "Review")))
fake.setBookmarks(listOf(
  Bookmark(1, 2, 4, 10, System.currentTimeMillis())
))
fake.setRecentPages(listOf(RecentPage(42, System.currentTimeMillis())))

// Configure behavior
fake.setUpdateTagResult(false) // Force updateTag to fail

// Use in tests
val result = fake.updateTag(1, "Updated Name")

// Assertions
fake.assertTagUpdated(tagId = 1, newName = "Updated Name")
assertThat(fake.getUpdateTagCallCount()).isEqualTo(1)
```

**Supported Methods:**
- `getRecentPages()`
- `addRecentPage(page: Int)`
- `replaceRecentRangeWithPage(start: Int, end: Int, page: Int)`
- `updateTag(id: Long, newName: String): Boolean`
- `tagBookmarks(bookmarkIds: LongArray, tagIds: Set<Long>, deleteNonTagged: Boolean): Boolean`
- `bulkDelete(tagIds: List<Long>, bookmarkIds: List<Long>, untag: List<Pair<Long, Long>>)`
- `getTags()`, `getBookmarks(sortOrder: Int)`, `getBookmarkedAyahsOnPage(page: Int)`
- `getBookmarkTagIds(bookmarkId: Long)`
- `getBookmarkId(sura: Int?, ayah: Int?, page: Int): Long`
- `addBookmark()`, `addBookmarkIfNotExists()`, `removeBookmark()`
- `addTag(name: String): Long`
- `importBookmarks(data: BookmarkData): Boolean`

### FakeRecentPageModel

Extends `RecentPageModel` and overrides open methods.

**Key Features:**
- Overrides `getRecentPagesObservable()` to return configured test data
- Tracks method calls
- No database interaction

**Usage Example:**
```kotlin
val fake = FakeRecentPageModel()

// Setup test data
fake.setRecentPages(listOf(
  RecentPage(42, System.currentTimeMillis()),
  RecentPage(100, System.currentTimeMillis() - 1000)
))

// Use in tests
fake.getRecentPagesObservable()
  .subscribe { pages ->
    assertThat(pages).hasSize(2)
    assertThat(pages[0].page).isEqualTo(42)
  }

// Assertions
fake.assertGetRecentPagesObservableCalled()
assertThat(fake.getGetRecentPagesObservableCallCount()).isEqualTo(1)
```

### FakeBookmarkModel

Extends `BookmarkModel` and overrides open methods.

**Key Features:**
- Overrides key bookmark operations
- Configurable success/failure behavior
- Tracks all method calls
- In-memory state management

**Usage Example:**
```kotlin
val fake = FakeBookmarkModel()

// Setup test data
fake.setTags(listOf(Tag(1, "Important")))
fake.setBookmarks(listOf(bookmark1, bookmark2))
fake.setBookmarkTagIds(bookmarkId = 1, tagIds = listOf(1L, 2L))

// Configure behavior
fake.setUpdateBookmarkTagsResult(false) // Force update to fail

// Use in tests
fake.updateBookmarkTags(
  bookmarkIds = longArrayOf(1, 2),
  tagIds = setOf(1L),
  deleteNonTagged = false
).subscribe { success ->
  assertThat(success).isFalse()
}

// Assertions
fake.assertUpdateBookmarkTagsCalled(
  bookmarkIds = longArrayOf(1, 2),
  tagIds = setOf(1L),
  deleteNonTagged = false
)
```

**Supported Override Methods:**
- `tagsObservable: Single<List<Tag>>`
- `getBookmarkDataObservable(sortOrder: Int): Single<BookmarkData>`
- `updateBookmarkTags(bookmarkIds, tagIds, deleteNonTagged): Observable<Boolean>`
- `safeAddBookmark(sura, ayah, page): Observable<Long>`
- `getBookmarkTagIds(bookmarkIdSingle): Maybe<List<Long>>`

## Common Patterns

### Setup and Teardown

```kotlin
class MyPresenterTest {
  private lateinit var fakeAdapter: FakeBookmarksDBAdapter
  private lateinit var fakeModel: FakeBookmarkModel

  @Before
  fun setup() {
    fakeAdapter = FakeBookmarksDBAdapter()
    fakeModel = FakeBookmarkModel()

    // Setup default test data
    fakeAdapter.setTags(listOf(Tag(1, "Default")))
  }

  @After
  fun teardown() {
    // Clear state between tests
    fakeAdapter.reset()
    fakeModel.reset()
  }

  @Test
  fun testSomething() {
    // Test-specific setup
    fakeAdapter.setUpdateTagResult(true)

    // Test code
    // ...

    // Clear call history if testing multiple operations
    fakeAdapter.clearCallHistory()
  }
}
```

### Configuring Behavior

All fakes support configurable behavior for testing edge cases:

```kotlin
// Success scenario
fake.setUpdateTagResult(true)

// Failure scenario
fake.setUpdateTagResult(false)

// Custom return value
fake.setSafeAddBookmarkResult(42L)
```

### Assertions

All fakes provide assertion methods:

```kotlin
// Assert a method was called with specific arguments
fake.assertTagUpdated(tagId = 1, newName = "Updated")

// Assert call count
assertThat(fake.getUpdateTagCallCount()).isEqualTo(2)

// Check current state
assertThat(fake.getCurrentTags()).hasSize(3)
```

## Testing Strategy

### When to Use These Fakes

1. **Unit tests for presenters** - Test business logic without database
2. **Integration tests with isolated components** - Test component interactions
3. **Fast tests** - No database setup/teardown overhead

### When NOT to Use These Fakes

1. **Database query tests** - Use real in-memory SQLite database
2. **SQL syntax validation** - Use actual database
3. **Transaction behavior tests** - Use real database

## Migration Guide

### From Mockito Mocks to Fakes

**Before (Mockito):**
```kotlin
val mockAdapter = mock(BookmarksDBAdapter::class.java)
whenever(mockAdapter.getTags()).thenReturn(listOf(Tag(1, "Test")))
whenever(mockAdapter.updateTag(1, "Updated")).thenReturn(true)
```

**After (Fakes):**
```kotlin
val fakeAdapter = FakeBookmarksDBAdapter()
fakeAdapter.setTags(listOf(Tag(1, "Test")))
fakeAdapter.setUpdateTagResult(true)
```

**Benefits:**
- More explicit and readable
- Type-safe assertions
- Stateful behavior matches real implementation
- No need to configure every method call

## Design Principles

1. **Stateful** - Fakes maintain in-memory state like real implementations
2. **Configurable** - Behavior can be configured for different test scenarios
3. **Verifiable** - All method calls are tracked for assertions
4. **Simple** - No complex setup required
5. **Isolated** - No external dependencies (database, network)

## Contributing

When adding new fakes:

1. Follow the existing pattern (configuration, state, assertions)
2. Document all public methods
3. Include usage examples
4. Add assertion helpers for common checks
5. Support reset() for test isolation
