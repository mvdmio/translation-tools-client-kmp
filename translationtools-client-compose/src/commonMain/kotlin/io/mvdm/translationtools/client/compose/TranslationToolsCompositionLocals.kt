package io.mvdm.translationtools.client.compose

import androidx.compose.runtime.staticCompositionLocalOf
import io.mvdm.translationtools.client.TranslationToolsClient

public val LocalTranslationToolsClient = staticCompositionLocalOf<TranslationToolsClient> {
   error("No TranslationToolsClient provided")
}

public val LocalTranslationToolsLocale = staticCompositionLocalOf<String?> {
   null
}
