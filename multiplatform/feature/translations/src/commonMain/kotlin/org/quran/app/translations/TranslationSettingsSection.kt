package org.quran.app.translations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.PaperCard
import org.quran.app.designsystem.label
import org.quran.app.model.AppLanguage
import org.quran.app.model.TranslationEdition

@Composable
fun TranslationSettingsSection(
    language: AppLanguage,
    editions: List<TranslationEdition>,
    selectedEditionId: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedEdition = editions.firstOrNull { it.id == selectedEditionId }

    PaperCard {
        Text(label(language, "Translation of the meanings", "ترجمة المعاني"), style = MaterialTheme.typography.titleLarge)
        Text(selectedEdition?.title ?: label(language, "Arabic only", "العربية فقط"))
        if (selectedEdition != null) {
            Text("${selectedEdition.languageName} · ${selectedEdition.translator} · v${selectedEdition.version}")
        }
        TextButton(onClick = { showPicker = true }) {
            Text(label(language, "Choose edition", "اختر ترجمة"))
        }
        if (isLoading) Text(label(language, "Updating translation list…", "جارٍ تحديث قائمة الترجمات…"))
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onRefresh) { Text(label(language, "Refresh list", "تحديث القائمة")) }
        Text(
            label(
                language,
                "Translation wording is human interpretation of the meanings. Arabic Quran text remains available offline.",
                "صياغة الترجمة تفسير بشري للمعاني. يبقى نص القرآن العربي متاحاً دون إنترنت.",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showPicker) {
        TranslationEditionPicker(
            language = language,
            editions = editions,
            selectedEditionId = selectedEditionId,
            onDismiss = { showPicker = false },
            onSelect = { editionId ->
                onSelect(editionId)
                showPicker = false
            },
        )
    }
}

@Composable
private fun TranslationEditionPicker(
    language: AppLanguage,
    editions: List<TranslationEdition>,
    selectedEditionId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredEditions = remember(editions, query) {
        editions.filter { edition ->
            edition.title.contains(query, ignoreCase = true) ||
                edition.languageName.contains(query, ignoreCase = true) ||
                edition.languageCode.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label(language, "Choose a translation", "اختر ترجمة")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(label(language, "Language or translator", "اللغة أو المترجم")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    item {
                        TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label(language, "Arabic only", "العربية فقط"))
                        }
                    }
                    items(filteredEditions, key = TranslationEdition::id) { edition ->
                        TextButton(
                            onClick = { onSelect(edition.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(edition.title)
                                Text(
                                    "${edition.languageName} · ${edition.translator} · v${edition.version}" +
                                        if (edition.id == selectedEditionId) " · ✓" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(label(language, "Close", "إغلاق")) }
        },
    )
}
