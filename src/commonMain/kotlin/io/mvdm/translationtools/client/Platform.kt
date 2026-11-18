package io.mvdm.translationtools.client

internal expect fun currentPlatform(): String

internal expect fun newClientId(): String

/** Formats [number] for [localeTag] with that Locale's digit grouping and minus sign, and no decimals. */
internal expect fun formatWholeNumber(number: Long, localeTag: String): String
