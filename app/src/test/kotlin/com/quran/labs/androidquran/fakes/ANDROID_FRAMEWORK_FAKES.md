# Android Framework Fakes - Analysis and Recommendations

## Summary

Creating fake implementations of Android framework classes (Context, Resources, DatabaseHandler) is **not recommended** for this codebase due to technical limitations and existing test infrastructure.

## Why Fakes Are Problematic

### 1. Context Cannot Be Extended

**Problem**: `android.content.Context` is an abstract class with:
- Final methods (`getString()`, `getResources()`, etc.) that cannot be overridden
- Missing abstract method implementations in newer Android versions
- Package-private constructors in helper classes (AssetManager)

**Evidence**:
```kotlin
// This FAILS to compile:
class FakeContext : Context() {
  override fun getString(id: Int): String  // ERROR: getString() is final!
}
```

**Compilation errors**:
```
e: FakeContext.kt:67:3 'getString' in 'Context' is final and cannot be overridden.
e: FakeContext.kt:23:1 Class 'FakeContext' is not abstract and does not implement abstract base class members
```

### 2. Resources Cannot Be Constructed

**Problem**: `android.content.res.Resources` requires:
- `AssetManager` which has a package-private constructor
- `DisplayMetrics` and `Configuration` objects
- Complex internal Android framework state

**Compilation error**:
```
e: FakeResources.kt:22:3 Cannot access 'constructor(): AssetManager': it is package-private
```

### 3. DatabaseHandler Uses Factory Pattern

**Problem**: `DatabaseHandler` is:
- A class (not an interface), making it hard to fake
- Has a private constructor
- Uses a factory method `getDatabaseHandler()` with singleton caching
- Tests already mock it successfully

## Current Test Infrastructure

### What Tests Actually Use

**From test file analysis**:

1. **BookmarkPresenterTest.kt**:
   ```kotlin
   @Mock lateinit var appContext: Context
   @Mock lateinit var resources: Resources

   whenever(appContext.getString(anyInt())).thenReturn("Test")
   whenever(appContext.resources).thenReturn(resources)
   whenever(resources.getStringArray(anyInt())).thenReturn(RESOURCE_ARRAY)
   ```

2. **ArabicDatabaseUtilsTest.kt**:
   ```kotlin
   @Mock lateinit var context: Context
   @Mock lateinit var arabicHandler: DatabaseHandler

   // Test overrides methods in anonymous class
   ```

3. **QuranImportPresenterTest.kt**:
   ```kotlin
   appContext = mock(Context::class.java)
   whenever(appContext.contentResolver).thenReturn(resolver)
   ```

**Conclusion**: All tests use **Mockito mocks**, not fakes.

## Recommended Approaches

### Approach 1: Continue Using Mockito (Recommended)

**Why**:
- Already works in all existing tests
- No compilation issues
- Flexible and well-understood

**Example**:
```kotlin
@Mock lateinit var context: Context
@Mock lateinit var resources: Resources

@Before
fun setup() {
  MockitoAnnotations.openMocks(this)
  whenever(context.getString(R.string.app_name)).thenReturn("Quran Android")
  whenever(context.resources).thenReturn(resources)
}
```

### Approach 2: Use Robolectric for Integration Tests

**Why**:
- Provides real Android framework implementation
- Useful for Activity/UI tests

**Example**:
```kotlin
@RunWith(RobolectricTestRunner::class)
class MyIntegrationTest {

  @Test
  fun testWithRealContext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val result = context.getString(R.string.app_name)
    assertThat(result).isEqualTo("Quran Android")
  }
}
```

**Current usage**: Already used in `QuranActivityTest.kt` and `ShortcutsActivityTest.kt`

### Approach 3: Test Helpers (For Common Patterns)

Instead of faking Android classes, create **helper functions** for common test setup:

```kotlin
// File: app/src/test/kotlin/com/quran/labs/androidquran/test/ContextTestHelpers.kt

fun Context.mockGetString(id: Int, value: String) {
  whenever(this.getString(id)).thenReturn(value)
}

fun Context.mockGetStringWithFormat(id: Int, template: String) {
  whenever(this.getString(eq(id), any())).thenAnswer { invocation ->
    String.format(template, *invocation.arguments.drop(1).toTypedArray())
  }
}

// Usage in tests:
context.mockGetString(R.string.app_name, "Quran Android")
```

## DatabaseHandler Special Case

### Why Not Fake It?

**DatabaseHandler** is already well-tested through:
1. **Unit tests**: Mock it with Mockito
2. **Override pattern**: Tests extend classes and override `getArabicDatabaseHandler()`

**Example from ArabicDatabaseUtilsTest**:
```kotlin
private fun getArabicDatabaseUtils(): ArabicDatabaseUtils {
  return object : ArabicDatabaseUtils(context, QuranInfo(...), ...) {
    override fun getArabicDatabaseHandler(): DatabaseHandler {
      return arabicHandler  // Mocked
    }

    override fun getAyahTextForAyat(ayat: List<Int>): Map<Int, String> {
      return ayat.map { it to "verse $it" }.toMap()  // Test data
    }
  }
}
```

This pattern is **more flexible** than a fake because:
- Each test can customize behavior
- No need to maintain a complex fake
- Follows existing codebase patterns

## Decision Matrix

| Approach | Pros | Cons | Use When |
|----------|------|------|----------|
| Mockito | ✅ Works now<br>✅ No setup<br>✅ Flexible | ❌ Verbose setup | Unit tests (most cases) |
| Robolectric | ✅ Real Android<br>✅ Less mocking | ❌ Slower<br>❌ More dependencies | Integration/UI tests |
| Test Helpers | ✅ Reusable<br>✅ Clean tests | ❌ Initial effort | Repeated patterns |
| Fakes | ❌ Can't compile<br>❌ High maintenance | None | ❌ Don't use |

## Conclusion

**Do NOT create fake implementations** of Android framework classes.

**Instead**:
1. ✅ Use Mockito for unit tests (current approach)
2. ✅ Use Robolectric for integration tests
3. ✅ Create test helper functions for common setup patterns
4. ✅ Override methods in anonymous classes when needed (DatabaseHandler pattern)

## Phase 2 Recommendation

For Phase 2 testing infrastructure:
- Focus on creating **test utilities** and **helper functions**
- Improve **test data factories** (already exist in `TestDataFactory`)
- Add **custom matchers** for domain objects
- Create **builder patterns** for complex test setup

These provide more value than attempting to fake Android framework classes.
