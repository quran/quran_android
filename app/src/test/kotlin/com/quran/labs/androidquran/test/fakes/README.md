# UI Screen Fakes - Phase 4

This directory contains fake implementations for UI interfaces that support verify() replacement in tests.

## Overview

These fakes follow the **call tracking pattern** to enable test assertions without Mockito's verify(). Each fake:
- Tracks all method calls with their arguments
- Provides assertion helpers for common verifications
- Offers query methods to inspect call history
- Includes reset() for test isolation

## Available Fakes

### 1. FakeQuranPageScreen
**For:** `QuranPagePresenterTest`

**Pattern:** Call tracking + assertion helpers

**Methods tracked:**
- `setPageCoordinates(PageCoordinates)`
- `setAyahCoordinatesData(AyahCoordinates)`
- `setAyahCoordinatesError()`
- `hidePageDownloadError()`
- `setPageBitmap(page: Int, bitmap: Bitmap)`
- `setPageDownloadError(errorRes: Int)`

**Example:**
```kotlin
val fakeScreen = FakeQuranPageScreen()
presenter.bind(fakeScreen)

// Assert method was called
fakeScreen.assertSetPageCoordinatesCalled(expectedCoordinates)
fakeScreen.assertSetAyahCoordinatesErrorCalled()

// Query call history
val lastCoordinates = fakeScreen.getLastPageCoordinates()
val callCount = fakeScreen.getPageCoordinatesCallCount()
```

**Replaces:**
```kotlin
// OLD: Mockito verify
verify(screen).setPageCoordinates(mockPageCoordinates)
verify(screen).setAyahCoordinatesError()

// NEW: Fake assertions
fakeScreen.assertSetPageCoordinatesCalled(expectedCoordinates)
fakeScreen.assertSetAyahCoordinatesErrorCalled()
```

### 2. FakePagerActivity
**For:** `AudioPresenterTest`

**Pattern:** Capture requests (ArgumentCaptor replacement)

**Methods tracked:**
- `handlePlayback(AudioRequest)`
- `handleRequiredDownload(Intent, String, ...)`

**Example:**
```kotlin
val fakeActivity = FakePagerActivity()
presenter.bind(fakeActivity)
presenter.play(...)

// Get captured request
val request = fakeActivity.getLastPlaybackRequest()
assertThat(request?.start).isEqualTo(expectedStart)

// Assert call count
fakeActivity.assertPlaybackCalledTimes(2)
```

**Replaces:**
```kotlin
// OLD: Mockito ArgumentCaptor
val captor = ArgumentCaptor.forClass(AudioRequest::class.java)
verify(pagerActivity).handlePlayback(captor.capture())
val request = captor.value

// NEW: Fake capture
val request = fakeActivity.getLastPlaybackRequest()
```

### 3. FakeTagBookmarkDialog
**For:** `TagBookmarkPresenterTest`

**Pattern:** Call tracking + state

**Methods tracked:**
- `showAddTagDialog()`
- `setData(tags: List<Tag>?, checkedTags: HashSet<Long>)`

**Example:**
```kotlin
val fakeDialog = FakeTagBookmarkDialog()
presenter.bind(fakeDialog)
presenter.toggleTag(-1)

// Assert dialog shown
fakeDialog.assertShowAddTagDialogCalled()

// Check data updates
val lastCall = fakeDialog.getLastSetDataCall()
assertThat(lastCall?.tags).hasSize(3)
```

### 4. FakeCoordinatesModel
**For:** `QuranPagePresenterTest`

**Pattern:** Configurable observables

**Methods:**
- `getPageCoordinates(...): Observable<PageCoordinates>`
- `getAyahCoordinates(page: Int): Observable<AyahCoordinates>`

**Example:**
```kotlin
val fakeModel = FakeCoordinatesModel()
fakeModel.setPageCoordinatesResponse(Observable.just(mockCoordinates))
fakeModel.setAyahCoordinatesError(RuntimeException("Test error"))

// Use in presenter
val presenter = QuranPagePresenter(fakeModel, ...)

// Assert calls made
fakeModel.assertGetPageCoordinatesCalled(wantPageBounds = true, 1, 2, 3)
```

### 5. FakeQuranPageLoader
**For:** `QuranPagePresenterTest`

**Pattern:** Configurable responses

**Methods:**
- `loadPages(pages: Array<Int>): Observable<Response>`

**Example:**
```kotlin
val fakeLoader = FakeQuranPageLoader()
fakeLoader.setLoadPagesSuccess(mockResponse)
// or
fakeLoader.setLoadPagesError(RuntimeException("Download failed"))

val presenter = QuranPagePresenter(..., fakeLoader, ...)

// Verify correct pages loaded
fakeLoader.assertLoadPagesCalled(1, 2, 3)
```

### 6. FakeAudioExtensionDecider
**For:** `AudioPresenterTest`

**Pattern:** Simple return values

**Methods:**
- `audioExtensionForQari(qari): String`
- `allowedAudioExtensions(qari): List<String>`

**Example:**
```kotlin
val fakeDecider = FakeAudioExtensionDecider()
fakeDecider.setAudioExtension("mp3")
fakeDecider.setAllowedExtensions(listOf("mp3", "opus"))

val presenter = AudioPresenter(..., fakeDecider, ...)
```

### 7. FakeQuranDisplayData
**For:** `AudioPresenterTest`

**Pattern:** Simple configuration

**Methods:**
- `getSuraName(context, sura, wantPrefix): String`
- `getNotificationTitle(context, minVerse, maxVerse, isGapless): String`

**Example:**
```kotlin
val fakeDisplayData = FakeQuranDisplayData()
fakeDisplayData.setSuraName("Al-Fatiha")
fakeDisplayData.setNotificationTitle("Playing Al-Fatiha")

val presenter = AudioPresenter(fakeDisplayData, ...)
```

## Design Principles

### 1. Call Tracking
All fakes store method calls in lists:
```kotlin
private val methodCalls = mutableListOf<CallData>()

override fun someMethod(arg: String) {
  methodCalls.add(CallData(arg))
}
```

### 2. Assertion Helpers
Provide clear, readable assertions:
```kotlin
fun assertMethodCalled(expected: String) {
  require(methodCalls.any { it.arg == expected }) {
    "Expected method($expected) but was called with: $methodCalls"
  }
}
```

### 3. Query Methods
Allow flexible test verification:
```kotlin
fun getLastCall(): CallData? = methodCalls.lastOrNull()
fun getAllCalls(): List<CallData> = methodCalls.toList()
fun getCallCount(): Int = methodCalls.size
```

### 4. Reset for Isolation
Each fake provides reset():
```kotlin
fun reset() {
  methodCalls.clear()
  // Reset other state
}
```

## Migration Guide

### Before (Mockito verify):
```kotlin
@Mock private lateinit var screen: QuranPageScreen

@Test
fun test() {
  presenter.bind(screen)

  verify(screen).setPageCoordinates(any())
  verify(screen, times(2)).setAyahCoordinatesData(any())
}
```

### After (Fake assertions):
```kotlin
private val fakeScreen = FakeQuranPageScreen()

@Test
fun test() {
  presenter.bind(fakeScreen)

  fakeScreen.assertSetPageCoordinatesCalledOnce()
  assertThat(fakeScreen.getAyahCoordinatesDataCallCount()).isEqualTo(2)
}
```

## Benefits

1. **No Mockito dependency** - Pure Kotlin test doubles
2. **Type-safe** - Compile-time verification of arguments
3. **Readable** - Clear assertion methods instead of verify syntax
4. **Flexible** - Query call history in any way needed
5. **Debuggable** - Actual objects with real state, easy to inspect
6. **Fast** - No reflection overhead from mocking framework

## Next Steps

These fakes enable Phase 5: Replacing verify() calls in actual test files with fake assertions.
