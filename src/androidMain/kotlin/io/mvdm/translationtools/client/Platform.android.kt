package io.mvdm.translationtools.client

internal actual fun currentPlatform(): String = "kmp-android"

internal actual fun newClientId(): String = java.util.UUID.randomUUID().toString()

internal actual fun formatWholeNumber(number: Long, localeTag: String): String =
   java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag(localeTag)).format(number)
