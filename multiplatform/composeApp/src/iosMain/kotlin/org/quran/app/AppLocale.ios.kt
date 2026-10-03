package org.quran.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSLocale
import org.quran.app.model.AppLanguage

actual object AppLocale {
    private const val languageKey = "AppleLanguages"
    private val defaultLanguage = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    private val localLanguage = staticCompositionLocalOf { defaultLanguage }

    actual val current: String
        @Composable get() = localLanguage.current

    @Composable
    actual infix fun provides(language: AppLanguage): ProvidedValue<*> {
        val selected = language.code
        NSUserDefaults.standardUserDefaults.setObject(listOf(selected), forKey = languageKey)
        return localLanguage.provides(selected)
    }
}
