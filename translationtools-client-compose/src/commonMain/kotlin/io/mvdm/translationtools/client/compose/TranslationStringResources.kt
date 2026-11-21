package io.mvdm.translationtools.client.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.mvdm.translationtools.client.BoundTranslationStringResource
import io.mvdm.translationtools.client.TranslationStringResource
import io.mvdm.translationtools.client.TranslationToolsClient

internal fun resolveStringResourceLocale(explicitLocale: String?, compositionLocale: String?): String?
{
   return explicitLocale ?: compositionLocale
}

internal fun initialStringResourceValue(
   client: TranslationToolsClient,
   resource: TranslationStringResource,
   locale: String?,
): String
{
   return initialStringResourceValue(client, BoundTranslationStringResource(resource), locale)
}

internal fun initialStringResourceValue(
   client: TranslationToolsClient,
   resource: BoundTranslationStringResource,
   locale: String?,
): String
{
   return client.getCached(resource, locale)
}

@Composable
public fun stringResource(
   resource: TranslationStringResource,
   locale: String? = null,
): String
{
   return stringResource(BoundTranslationStringResource(resource), locale)
}

/**
 * Render [resource], a generated lookup bound to its Placeholder values and Plural number, and update when
 * the translation changes.
 */
@Composable
public fun stringResource(
   resource: BoundTranslationStringResource,
   locale: String? = null,
): String
{
   val client = LocalTranslationToolsClient.current
   val resolvedLocale = resolveStringResourceLocale(locale, LocalTranslationToolsLocale.current)
   val value by client.observe(resource, resolvedLocale).collectAsState(
      initial = initialStringResourceValue(client, resource, resolvedLocale),
   )
   return value
}
