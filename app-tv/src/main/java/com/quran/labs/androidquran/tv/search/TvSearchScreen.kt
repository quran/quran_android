package com.quran.labs.androidquran.tv.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Search result data model
 */
data class SearchResult(
  val sura: Int,
  val ayah: Int,
  val page: Int,
  val suraName: String,
  val suraNameEnglish: String,
  val text: String
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchScreen(
  onSearchResultClick: (Int, Int, Int) -> Unit = { _, _, _ -> },
  onBackClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }
  var isFocused by remember { mutableStateOf(false) }
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  val coroutineScope = rememberCoroutineScope()

  // Auto-focus search field when screen loads
  LaunchedEffect(Unit) {
    delay(300)
    focusRequester.requestFocus()
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Header
      Text(
        text = "Search Quran",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 24.dp)
      )

      // Search input field
      TextField(
        value = searchQuery,
        onValueChange = {
          searchQuery = it
          if (it.length >= 3) {
            isSearching = true
            // Simulate search - in production, use actual search
            performSearch(it) { results ->
              searchResults = results
              isSearching = false
            }
          } else {
            searchResults = emptyList()
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester)
          .onFocusChanged { focusState ->
            isFocused = focusState.hasFocus
          }
          .then(
            if (isFocused) {
              Modifier.border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
              )
            } else {
              Modifier
            }
          ),
        label = { Text("Enter search text (min 3 characters)") },
        placeholder = { Text("Search in Arabic or English") },
        keyboardOptions = KeyboardOptions(
          imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
          onSearch = {
            keyboardController?.hide()
          }
        ),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge
      )

      // Search status
      if (isSearching) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Searching...",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else if (searchQuery.length in 1..2) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Enter at least 3 characters to search",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else if (searchQuery.length >= 3 && searchResults.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No results found for \"$searchQuery\"",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Search results
      if (searchResults.isNotEmpty()) {
        Text(
          text = "${searchResults.size} result${if (searchResults.size > 1) "s" else ""} found",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 16.dp)
        )

        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(searchResults) { result ->
            SearchResultItem(
              result = result,
              onClick = { onSearchResultClick(result.sura, result.ayah, result.page) }
            )
          }
        }
      }
    }
  }
}

@Composable
fun SearchResultItem(
  result: SearchResult,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isFocused by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(120.dp)
      .background(
        if (isFocused) {
          Brush.horizontalGradient(
            colors = listOf(
              MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
              MaterialTheme.colorScheme.surfaceVariant
            )
          )
        } else {
          Brush.horizontalGradient(
            colors = listOf(
              MaterialTheme.colorScheme.surfaceVariant,
              MaterialTheme.colorScheme.surface
            )
          )
        }
      )
      .border(
        width = if (isFocused) 2.dp else 1.dp,
        color = if (isFocused) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
      )
      .focusable()
      .onFocusChanged { focusState ->
        isFocused = focusState.hasFocus
      }
      .padding(20.dp)
      .then(
        if (isFocused) {
          Modifier.then(
            // Add click listener when focused - simulate TV button press
            Modifier.focusable()
          )
        } else {
          Modifier
        }
      ),
    contentAlignment = Alignment.CenterStart
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Surah and verse info
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "${result.suraNameEnglish} (${result.suraName})",
            style = MaterialTheme.typography.titleLarge,
            color = if (isFocused) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = "Verse ${result.ayah} • Page ${result.page}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
              else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Box(
          modifier = Modifier
            .background(
              if (isFocused) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.secondary,
              shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Text(
            text = "View",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
          )
        }
      }

      // Preview text
      Text(
        text = result.text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        modifier = Modifier.padding(top = 8.dp)
      )
    }
  }
}

/**
 * Perform search - this is a placeholder implementation
 * In production, this would use the QuranDataProvider ContentResolver
 */
private fun performSearch(query: String, onResults: (List<SearchResult>) -> Unit) {
  // Placeholder implementation with sample data
  // In production, this would query the actual Quran database

  val isArabic = query.any { it in '\u0600'..'\u06FF' }

  val sampleResults = if (isArabic || query.contains("muhammad", ignoreCase = true)) {
    listOf(
      SearchResult(
        sura = 47,
        ayah = 1,
        page = 508,
        suraName = "محمد",
        suraNameEnglish = "Muhammad",
        text = "Those who disbelieve and hinder [others] from the way of Allah - He will waste their deeds."
      ),
      SearchResult(
        sura = 3,
        ayah = 144,
        page = 67,
        suraName = "آل عمران",
        suraNameEnglish = "Ali 'Imran",
        text = "Muhammad is not the father of [any] of your men, but [he is] the Messenger of Allah and last of the prophets..."
      )
    )
  } else if (query.contains("mercy", ignoreCase = true) || query.contains("rahman", ignoreCase = true)) {
    listOf(
      SearchResult(
        sura = 55,
        ayah = 1,
        page = 531,
        suraName = "الرحمن",
        suraNameEnglish = "Ar-Rahman",
        text = "The Beneficent, Taught the Quran, Created man..."
      ),
      SearchResult(
        sura = 1,
        ayah = 1,
        page = 1,
        suraName = "الفاتحة",
        suraNameEnglish = "Al-Fatihah",
        text = "In the name of Allah, the Entirely Merciful, the Especially Merciful."
      )
    )
  } else {
    emptyList()
  }

  onResults(sampleResults)
}
