package io.mvdm.translationtools.client

internal expect fun currentPlatform(): String

internal expect fun newClientId(): String

/** Formats [number] for [localeTag] with that Locale's digit grouping and minus sign, and no decimals. */
internal expect fun formatWholeNumber(number: Long, localeTag: String): String

/** Writes the Placeholder warning [message] to the platform log, tagged `TranslationTools`. */
internal expect fun logPlaceholderWarning(message: String)
