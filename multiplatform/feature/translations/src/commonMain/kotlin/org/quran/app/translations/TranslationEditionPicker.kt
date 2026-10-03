package org.quran.app.translations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import org.quran.app.designsystem.QuranDialog
import org.quran.app.designsystem.QuranTextField
import org.quran.app.designsystem.QuranTextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.quran.app.designsystem.QuranStrings
import org.quran.app.designsystem.appString
import org.quran.app.model.AppLanguage
import org.quran.app.model.TranslationEdition

@Composable
internal fun TranslationEditionPicker(
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
                edition.translator.contains(query, ignoreCase = true) ||
                edition.languageName.contains(query, ignoreCase = true) ||
                edition.languageCode.contains(query, ignoreCase = true)
        }
    }

    QuranDialog(
        onDismiss = onDismiss,
        title = appString(QuranStrings.chooseTranslation),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuranTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = appString(QuranStrings.languageOrTranslator),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    item {
                        TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(appString(QuranStrings.arabicOnly))
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
                                    appString(QuranStrings.translationEditionMetadata, edition.languageName, edition.translator, edition.version) +
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
            QuranTextButton(appString(QuranStrings.close), onClick = onDismiss)
        },
    )
}
