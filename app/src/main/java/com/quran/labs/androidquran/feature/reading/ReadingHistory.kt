package com.quran.labs.androidquran.feature.reading

class ReadingHistory {
  private val entries = mutableListOf<ReadingPlace>()
  private var index = -1
  private var primaryDirection = ReadingHistoryDirection.BACK

  fun recordJump(leaving: ReadingPlace, destination: ReadingPlace) {
    if (entries.isEmpty()) {
      if (leaving == destination) {
        return
      }
      entries += leaving
      entries += destination
      index = entries.lastIndex
      primaryDirection = ReadingHistoryDirection.BACK
      return
    }

    if (entries[index].page == leaving.page && entries[index] == destination) {
      return
    }

    if (index < entries.lastIndex) {
      entries.subList(index + 1, entries.size).clear()
    }

    if (entries[index].page != leaving.page) {
      entries += leaving
      index = entries.lastIndex
    }

    if (entries[index] != destination) {
      entries += destination
      index = entries.lastIndex
    }
    primaryDirection = ReadingHistoryDirection.BACK
  }

  fun back(): ReadingPlace? {
    if (index <= 0) {
      return null
    }
    index -= 1
    if (index == 0) {
      primaryDirection = ReadingHistoryDirection.FORWARD
    }
    return entries[index]
  }

  fun forward(): ReadingPlace? {
    if (entries.isEmpty() || index >= entries.lastIndex) {
      return null
    }
    index += 1
    if (index == entries.lastIndex) {
      primaryDirection = ReadingHistoryDirection.BACK
    }
    return entries[index]
  }

  fun canGoBack(): Boolean = index > 0

  fun canGoForward(): Boolean = entries.isNotEmpty() && index < entries.lastIndex

  fun chrome(): ReadingHistoryChrome {
    if (entries.size < 2 || index !in entries.indices) {
      return ReadingHistoryChrome()
    }
    val canGoBack = index > 0
    val canGoForward = index < entries.lastIndex
    return when {
      canGoBack && canGoForward -> ReadingHistoryChrome(primaryDirection, showsCaret = true)
      canGoBack -> ReadingHistoryChrome(ReadingHistoryDirection.BACK)
      canGoForward -> ReadingHistoryChrome(ReadingHistoryDirection.FORWARD)
      else -> ReadingHistoryChrome()
    }
  }

  fun snapshot(): ReadingHistorySnapshot {
    return ReadingHistorySnapshot(
      entries = entries.toList(),
      index = index,
      primaryDirection = primaryDirection
    )
  }

  fun restore(snapshot: ReadingHistorySnapshot) {
    entries.clear()
    if (snapshot.entries.size < 2 || snapshot.index !in snapshot.entries.indices) {
      index = -1
      primaryDirection = ReadingHistoryDirection.BACK
      return
    }
    entries.addAll(snapshot.entries)
    index = snapshot.index
    primaryDirection = when (index) {
      0 -> ReadingHistoryDirection.FORWARD
      entries.lastIndex -> ReadingHistoryDirection.BACK
      else -> snapshot.primaryDirection
    }
  }
}

data class ReadingPlace(
  val page: Int,
  val sura: Int = NO_HIGHLIGHT,
  val ayah: Int = NO_HIGHLIGHT
) {
  val hasHighlight: Boolean
    get() = sura > 0 && ayah > 0

  companion object {
    const val NO_HIGHLIGHT = -1
  }
}

enum class ReadingHistoryDirection {
  BACK,
  FORWARD;

  fun opposite(): ReadingHistoryDirection {
    return when (this) {
      BACK -> FORWARD
      FORWARD -> BACK
    }
  }
}

data class ReadingHistoryChrome(
  val primary: ReadingHistoryDirection? = null,
  val showsCaret: Boolean = false
) {
  val isVisible: Boolean
    get() = primary != null
}

data class ReadingHistorySnapshot(
  val entries: List<ReadingPlace>,
  val index: Int,
  val primaryDirection: ReadingHistoryDirection
)
