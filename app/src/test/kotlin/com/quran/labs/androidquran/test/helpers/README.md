# Test Helpers

This directory contains helper utilities to reduce boilerplate in unit tests.

## Available Helpers

### ContextTestHelpers.kt

Extension functions for mocking Android `Context` behavior.

**Usage**:
```kotlin
@Mock lateinit var context: Context

@Before
fun setup() {
  MockitoAnnotations.openMocks(this)

  // Mock single string
  context.mockGetString(R.string.app_name, "Quran Android")

  // Mock formatted string
  context.mockGetStringWithFormat(R.string.bookmark_header, "Bookmarks: %d")

  // Mock multiple strings at once
  context.mockStrings(mapOf(
    R.string.app_name to "Quran Android",
    R.string.settings to "Settings"
  ))

  // Mock any getString() call with default value
  context.mockAnyString("Test")

  // Mock other Context methods
  context.mockResources(mockResources)
  context.mockContentResolver(mockContentResolver)
  context.mockApplicationContext()
}
```

**Available functions**:
- `Context.mockGetString(id: Int, value: String)`
- `Context.mockGetStringWithFormat(id: Int, template: String)`
- `Context.mockResources(resources: Resources)`
- `Context.mockContentResolver(contentResolver: ContentResolver)`
- `Context.mockApplicationContext(applicationContext: Context = this)`
- `Context.mockStrings(strings: Map<Int, String>)`
- `Context.mockStringFormats(formats: Map<Int, String>)`
- `Context.mockAnyString(defaultValue: String = "Test")`

### ResourcesTestHelpers.kt

Extension functions for mocking Android `Resources` behavior.

**Usage**:
```kotlin
@Mock lateinit var resources: Resources

@Before
fun setup() {
  MockitoAnnotations.openMocks(this)

  // Mock single string array
  resources.mockStringArray(R.array.sura_names, arrayOf("Al-Fatiha", "Al-Baqarah"))

  // Mock multiple string arrays
  resources.mockStringArrays(mapOf(
    R.array.sura_names to arrayOf("Al-Fatiha", "Al-Baqarah"),
    R.array.juz_names to arrayOf("Juz 1", "Juz 2")
  ))

  // Mock other resource types
  resources.mockInteger(R.integer.max_pages, 604)
  resources.mockBoolean(R.bool.is_tablet, false)
  resources.mockDimension(R.dimen.text_size, 16f)
  resources.mockColor(R.color.primary, 0xFF6200EE.toInt())
}
```

**Available functions**:
- `Resources.mockStringArray(id: Int, value: Array<String>)`
- `Resources.mockInteger(id: Int, value: Int)`
- `Resources.mockBoolean(id: Int, value: Boolean)`
- `Resources.mockDimension(id: Int, value: Float)`
- `Resources.mockColor(id: Int, value: Int)`
- `Resources.mockStringArrays(arrays: Map<Int, Array<String>>)`
- `Resources.mockAnyStringArray(defaultValue: Array<String> = emptyArray())`

### DatabaseHandlerTestHelpers.kt

Extension functions for mocking `DatabaseHandler` behavior and creating test data.

**Usage**:
```kotlin
@Mock lateinit var databaseHandler: DatabaseHandler

@Before
fun setup() {
  MockitoAnnotations.openMocks(this)

  // Mock basic properties
  databaseHandler.mockValidDatabase(true)
  databaseHandler.mockSchemaVersion(1)
  databaseHandler.mockTextVersion(1)

  // Mock verse queries with test data
  databaseHandler.mockGetVersesCursor(listOf(
    Triple(1, 1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"),
    Triple(1, 2, "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ")
  ))

  // Use pre-defined test data
  val fatihaVerses = createFatihaVerses()
  databaseHandler.mockGetVersesCursor(fatihaVerses)

  // Generate test verses programmatically
  val testVerses = createTestVerses(sura = 2, count = 5)
  databaseHandler.mockGetVersesByIds(testVerses)
}
```

**Available functions**:
- `DatabaseHandler.mockValidDatabase(isValid: Boolean = true)`
- `DatabaseHandler.mockSchemaVersion(version: Int = 1)`
- `DatabaseHandler.mockTextVersion(version: Int = 1)`
- `DatabaseHandler.mockGetVersesCursor(verses: List<Triple<Int, Int, String>>)`
- `DatabaseHandler.mockGetVersesByIds(verses: List<Triple<Int, Int, String>>)`
- `DatabaseHandler.mockSearch(verses: List<Triple<Int, Int, String>>)`
- `createFatihaVerses(): List<Triple<Int, Int, String>>` - Full text of Sura Al-Fatiha
- `createTestVerses(sura: Int, startAyah: Int = 1, count: Int): List<Triple<Int, Int, String>>` - Generate simple test verses

## Example: Complete Test Setup

```kotlin
class MyPresenterTest {

  @Mock lateinit var context: Context
  @Mock lateinit var resources: Resources
  @Mock lateinit var databaseHandler: DatabaseHandler

  private lateinit var presenter: MyPresenter

  @Before
  fun setup() {
    MockitoAnnotations.openMocks(this)

    // Setup Context
    context.mockStrings(mapOf(
      R.string.app_name to "Quran Android",
      R.string.loading to "Loading..."
    ))
    context.mockResources(resources)

    // Setup Resources
    resources.mockStringArray(
      R.array.sura_names,
      arrayOf("Al-Fatiha", "Al-Baqarah")
    )

    // Setup DatabaseHandler
    databaseHandler.mockValidDatabase(true)
    databaseHandler.mockGetVersesCursor(createFatihaVerses())

    // Create presenter
    presenter = MyPresenter(context, databaseHandler)
  }

  @Test
  fun testSomething() {
    // Test code here
  }
}
```

## Benefits

1. **Reduced Boilerplate**: No more repetitive `whenever()` calls
2. **Readability**: Clear, declarative test setup
3. **Consistency**: Standardized mocking patterns across tests
4. **Reusability**: Common test data (like Fatiha) available out-of-the-box
5. **Maintainability**: Changes to mocking patterns centralized

## Why Not Fakes?

See `../fakes/ANDROID_FRAMEWORK_FAKES.md` for a detailed explanation of why we use test helpers with mocks rather than fake implementations for Android framework classes.

## Adding New Helpers

When adding new helpers:

1. **Follow naming conventions**: `Class.mockMethodName()` for mocking, `createTestData()` for data
2. **Add KDoc comments**: Explain what the helper does and show usage example
3. **Keep it simple**: One helper should do one thing well
4. **Use Mockito idioms**: Leverage `whenever()`, `any()`, `eq()`, etc.
5. **Make it reusable**: Think about common use cases across multiple tests
