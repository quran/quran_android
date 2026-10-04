package org.quran.app

import androidx.compose.runtime.Composable
import org.quran.app.designsystem.*
import org.quran.app.model.ReadingPreferences

@Composable
internal fun ReadingPreferencesCard(preferences: ReadingPreferences, childrenMode: Boolean, onChanged: (ReadingPreferences) -> Unit) {
    PaperCard {
        QuranText(appString(QuranStrings.readingPreferences), variant = QuranTextVariant.Title)
        QuranText(appString(QuranStrings.readingPreferencesHelp), variant = QuranTextVariant.Supporting)
        ReadingTextSizeChoices(appString(QuranStrings.arabicTextSize), "arabic", preferences.arabicTextSize) {
            onChanged(preferences.copy(arabicTextSize = it))
        }
        ReadingTextSizeChoices(appString(QuranStrings.translationTextSize), "translation", preferences.translationTextSize) {
            onChanged(preferences.copy(translationTextSize = it))
        }
        if (childrenMode) QuranText(appString(QuranStrings.childrenLargeText), variant = QuranTextVariant.Supporting)
    }
}
