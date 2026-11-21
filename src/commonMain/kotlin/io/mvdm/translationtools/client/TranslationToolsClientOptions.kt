package io.mvdm.translationtools.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

public data class TranslationToolsClientOptions(
   val apiKey: String,
   val preferredLocales: Set<String> = emptySet(),
   val refreshInterval: Duration = 1.hours,
   val backgroundRefreshEnabled: Boolean = true,
   val currentLocaleProvider: () -> String? = { null },
   val snapshotStore: TranslationSnapshotStore = NoOpTranslationSnapshotStore,
   val bundledSnapshot: StoredTranslations? = null,
   val environment: String? = null,
   val heartbeatEnabled: Boolean = true,
   val heartbeatInterval: Duration = 1.hours,
   /**
    * Global Placeholders declared by this application: token name -> ambient resolver. Each resolver
    * is invoked per render. A resolver that throws or returns null degrades its token to the raw
    * `{token}` (plus a warning, unless [throwOnPlaceholderError] is set). The warning goes to
    * [onPlaceholderWarning], or to the platform log when that is unset. The registered names are
    * pushed to the server at startup as this environment's globals (spec §6).
    */
   val globalPlaceholders: Map<String, () -> String?> = emptyMap(),
   /**
    * Placeholder failure behavior (spec §5). Default `false` = warn + degrade (append the raw token).
    * The warning goes to [onPlaceholderWarning], or to the platform log when that is unset.
    * Set `true` to throw [io.mvdm.translationtools.client.placeholders.PlaceholderSubstitutionException]
    * on every warn case instead, and report no warning.
    */
   val throwOnPlaceholderError: Boolean = false,
   /**
    * Receives each Placeholder warning, as text that ends with the Key, Origin, and Locale of the value
    * rendered. The client passes on each distinct warning once. When this is `null` (the default), the
    * warning goes to the platform log instead: Logcat on Android (tag `TranslationTools`, warning level),
    * standard error on JVM, and `NSLog` on iOS. Set a handler to send warnings to your own logger, or
    * `{}` to mute them.
    */
   val onPlaceholderWarning: ((String) -> Unit)? = null,
)
{
   init {
      require(apiKey.isNotBlank()) { "ApiKey is required." }
      require(!refreshInterval.isNegative()) { "RefreshInterval must be zero or greater." }
      require(!heartbeatInterval.isNegative()) { "HeartbeatInterval must be zero or greater." }
      globalPlaceholders.keys.forEach { name ->
         require(globalPlaceholderNameRegex.matches(name)) {
            "Invalid global placeholder name '$name'. Names must match [a-z][a-zA-Z0-9]*."
         }
      }
   }
}

private val globalPlaceholderNameRegex = Regex("^[a-z][a-zA-Z0-9]*$")
