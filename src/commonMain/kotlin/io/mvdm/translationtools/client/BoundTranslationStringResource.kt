package io.mvdm.translationtools.client

/**
 * A [TranslationStringResource] together with the values to render it with: one value per Placeholder and,
 * for a Plural, the number that picks the form. Generated `Translations` functions return it, and the client
 * renders it in [TranslationToolsClient.get], [TranslationToolsClient.getCached], and
 * [TranslationToolsClient.observe].
 *
 * Rendering it equals [TranslationToolsClient.withPlaceholders] on [resource] with each of [placeholders]
 * set and, when [pluralNumber] is not null, [TranslationRenderBuilder.setPluralNumber] set.
 */
public data class BoundTranslationStringResource(
   val resource: TranslationStringResource,
   val placeholders: Map<String, String> = emptyMap(),
   val pluralNumber: Long? = null,
)
