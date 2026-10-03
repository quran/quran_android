package org.quran.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import org.quran.app.model.AppLanguage

/** Applies the saved in-app language to Compose Multiplatform string resources. */
expect object AppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(language: AppLanguage): ProvidedValue<*>
}
