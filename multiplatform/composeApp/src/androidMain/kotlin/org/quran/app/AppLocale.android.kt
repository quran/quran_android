package org.quran.app

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import org.quran.app.model.AppLanguage

actual object AppLocale {
    private var initialLocale: Locale? = null

    actual val current: String
        @Composable get() = LocalConfiguration.current.locales[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(language: AppLanguage): ProvidedValue<*> {
        if (initialLocale == null) initialLocale = Locale.getDefault()
        val locale = Locale.forLanguageTag(language.code)
        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
        Locale.setDefault(locale)
        return LocalConfiguration.provides(configuration)
    }
}
