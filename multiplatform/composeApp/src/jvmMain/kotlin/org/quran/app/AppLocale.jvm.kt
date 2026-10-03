package org.quran.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale
import org.quran.app.model.AppLanguage

actual object AppLocale {
    private val localLanguage = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = localLanguage.current

    @Composable
    actual infix fun provides(language: AppLanguage): ProvidedValue<*> {
        val locale = Locale.forLanguageTag(language.code)
        Locale.setDefault(locale)
        return localLanguage.provides(locale.toLanguageTag())
    }
}
