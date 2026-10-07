package com.sami.livetv.ui.localization

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.sami.livetv.data.preferences.AppLanguage
import java.util.Locale

@Composable
fun AppLanguageLayout(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLayoutDirection provides when (language) {
            AppLanguage.Arabic -> LayoutDirection.Rtl
            AppLanguage.English -> LayoutDirection.Ltr
        },
        content = content,
    )
}

@Composable
fun localizedString(
    @StringRes stringId: Int,
    language: AppLanguage,
    vararg formatArgs: Any,
): String {
    val context = LocalContext.current
    val localizedContext = remember(context, language) {
        val locale = when (language) {
            AppLanguage.Arabic -> Locale.forLanguageTag("ar")
            AppLanguage.English -> Locale.ENGLISH
        }
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        context.createConfigurationContext(configuration)
    }

    return if (formatArgs.isEmpty()) {
        localizedContext.getString(stringId)
    } else {
        localizedContext.getString(stringId, *formatArgs)
    }
}
