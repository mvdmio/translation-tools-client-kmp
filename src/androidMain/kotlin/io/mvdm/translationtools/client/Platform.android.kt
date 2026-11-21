package io.mvdm.translationtools.client

internal actual fun currentPlatform(): String = "kmp-android"

internal actual fun newClientId(): String = java.util.UUID.randomUUID().toString()

internal actual fun formatWholeNumber(number: Long, localeTag: String): String =
   java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag(localeTag)).format(number)

internal actual fun logPlaceholderWarning(message: String)
{
   try {
      android.util.Log.w("TranslationTools", message)
   }
   catch (_: RuntimeException) {
      // The SDK's JVM-test jar stubs android.util.Log and throws "Method w in android.util.Log not mocked".
      println("TranslationTools: $message")
   }
}
