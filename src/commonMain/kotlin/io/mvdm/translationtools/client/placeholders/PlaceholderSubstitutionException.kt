package io.mvdm.translationtools.client.placeholders

import io.mvdm.translationtools.client.TranslationToolsException

/**
 * Thrown when placeholder substitution encounters a failure case and
 * [io.mvdm.translationtools.client.TranslationToolsClientOptions.throwOnPlaceholderError] is enabled.
 * When the flag is disabled (the default), the same cases warn and degrade to the raw token instead.
 */
public class PlaceholderSubstitutionException(
   message: String,
) : TranslationToolsException(message)
