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
)
{
   init {
      require(apiKey.isNotBlank()) { "ApiKey is required." }
      require(!refreshInterval.isNegative()) { "RefreshInterval must be zero or greater." }
      require(!heartbeatInterval.isNegative()) { "HeartbeatInterval must be zero or greater." }
   }
}
