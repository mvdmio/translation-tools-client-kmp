package io.mvdm.translationtools.client

internal actual fun currentPlatform(): String = "kmp-jvm"

internal actual fun newClientId(): String = java.util.UUID.randomUUID().toString()

internal actual fun formatWholeNumber(number: Long, localeTag: String): String =
   java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag(localeTag)).format(number)

internal actual fun logPlaceholderWarning(message: String) = System.err.println("TranslationTools: $message")
