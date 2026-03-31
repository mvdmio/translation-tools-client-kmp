package io.mvdm.translationtools.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

public class TranslationToolsClient(
   public val options: TranslationToolsClientOptions,
   private val api: TranslationToolsApi,
   private val now: () -> Instant = { Clock.System.now() },
)
{
   public constructor(
      options: TranslationToolsClientOptions,
      api: TranslationToolsApi,
   ) : this(options = options, api = api, now = { Clock.System.now() })

   private val refreshLock = Mutex()
   private val projectMetadataFlow = MutableStateFlow<ProjectMetadata?>(null)
   private val translationsFlow = MutableStateFlow<Map<String, Map<String, TranslationItem>>>(emptyMap())
   private val refreshStateFlow = MutableStateFlow(TranslationRefreshState())
   private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
   private var lastSuccessfulRefreshAt: Instant? = null

   public suspend fun initialize()
   {
      var restored = false
      refreshStateFlow.value = TranslationRefreshState(status = TranslationRefreshStatus.RestoringCache, lastSuccessfulRefreshAt = lastSuccessfulRefreshAt)

      options.snapshotStore.load()?.let {
         restore(it)
         restored = true
      }

      if (!restored) {
         options.bundledSnapshot?.let {
            restore(it)
            restored = true
         }
      }

      if (!restored) {
         try {
            refresh(force = true)
         }
         catch (exception: Throwable) {
            refreshStateFlow.value = TranslationRefreshState(
               status = TranslationRefreshStatus.Failed,
               lastSuccessfulRefreshAt = lastSuccessfulRefreshAt,
               lastFailureMessage = exception.message,
            )
            throw exception
         }
         return
      }

      if (!options.backgroundRefreshEnabled)
         return

      backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
          try {
             refresh(force = true)
          }
         catch (_: Throwable) {
         }
      }
   }

   public fun getCached(key: String, locale: String? = null): String?
   {
      val validatedKey = validateKey(key)
      val resolvedLocale = resolveLocale(locale)
      return translationsFlow.value[resolvedLocale]?.get(validatedKey)?.value
   }

   public fun getCached(resource: TranslationStringResource, locale: String? = null): String
   {
      return getCached(resource.key, locale) ?: resource.fallback ?: resource.key
   }

   public suspend fun get(key: String, locale: String? = null, defaultValue: String? = null): String
   {
      val validatedKey = validateKey(key)
      val resolvedLocale = resolveLocale(locale)
      val cachedItem = translationsFlow.value[resolvedLocale]?.get(validatedKey)
      if (cachedItem != null)
         return cachedItem.value ?: defaultValue ?: validatedKey

      val fetched = api.getTranslation(resolvedLocale, validatedKey, defaultValue)
      putItem(resolvedLocale, fetched)
      persist()
      return fetched.value ?: defaultValue ?: validatedKey
   }

   public suspend fun get(resource: TranslationStringResource, locale: String? = null): String
   {
      return get(resource.key, locale, resource.fallback)
   }

   public fun observe(key: String, locale: String? = null): Flow<String?>
   {
      val validatedKey = validateKey(key)
      return translationsFlow.map {
         val resolvedLocale = resolveLocale(locale)
         it[resolvedLocale]?.get(validatedKey)?.value
      }.distinctUntilChanged()
   }

   public fun observe(resource: TranslationStringResource, locale: String? = null): Flow<String>
   {
      return observe(resource.key, locale)
         .map { it ?: resource.fallback ?: resource.key }
         .distinctUntilChanged()
   }

   public fun observeRefreshState(): Flow<TranslationRefreshState>
   {
      return refreshStateFlow
   }

   public suspend fun refreshIfStale()
   {
      if (!isRefreshStale(now()))
         return

      refresh(force = false)
   }

   public suspend fun refresh()
   {
      refresh(force = true)
   }

   private suspend fun refresh(force: Boolean)
   {
      val requestedAt = now()

      refreshLock.withLock {
         if (!force && !isRefreshStale(requestedAt))
            return

         refreshStateFlow.value = TranslationRefreshState(
            status = TranslationRefreshStatus.Refreshing,
            lastSuccessfulRefreshAt = lastSuccessfulRefreshAt,
         )

         try {
            val metadata = api.getProjectMetadata().normalize()
            val locales = selectLocales(metadata)
            val snapshots = locales.map { locale ->
               TranslationSnapshot(locale, api.getLocale(locale).map { item -> item.copy(key = validateKey(item.key)) })
            }

            val completedAt = now()
            replaceState(metadata, snapshots, completedAt)
            persist()
            refreshStateFlow.value = TranslationRefreshState(
               status = TranslationRefreshStatus.Ready,
               lastSuccessfulRefreshAt = completedAt,
            )
         }
         catch (exception: Throwable) {
            refreshStateFlow.value = TranslationRefreshState(
               status = TranslationRefreshStatus.Failed,
               lastSuccessfulRefreshAt = lastSuccessfulRefreshAt,
               lastFailureMessage = exception.message,
            )
            throw exception
         }
      }
   }

   private fun restore(stored: StoredTranslations)
   {
      projectMetadataFlow.value = stored.projectMetadata?.normalize()
      translationsFlow.value = stored.snapshots.associate { snapshot ->
         normalizeLocale(snapshot.locale) to snapshot.items.associateBy { validateKey(it.key) }
      }
      lastSuccessfulRefreshAt = stored.lastSuccessfulRefreshAt
      refreshStateFlow.value = TranslationRefreshState(
         status = TranslationRefreshStatus.Ready,
         lastSuccessfulRefreshAt = lastSuccessfulRefreshAt,
      )
   }

   private suspend fun persist()
   {
      options.snapshotStore.save(
         StoredTranslations(
            projectMetadata = projectMetadataFlow.value,
            snapshots = translationsFlow.value.map { (locale, items) ->
               TranslationSnapshot(locale, items.values.toList())
            },
            lastSuccessfulRefreshAt = lastSuccessfulRefreshAt,
         )
      )
   }

   private fun replaceState(metadata: ProjectMetadata, snapshots: List<TranslationSnapshot>, completedAt: Instant)
   {
      projectMetadataFlow.value = metadata
      translationsFlow.value = snapshots.associate { snapshot ->
         normalizeLocale(snapshot.locale) to snapshot.items.associateBy { validateKey(it.key) }
      }
      lastSuccessfulRefreshAt = completedAt
   }

   private fun putItem(locale: String, item: TranslationItem)
   {
      val normalizedLocale = normalizeLocale(locale)
      val validatedKey = validateKey(item.key)

      translationsFlow.update { current ->
         val localeItems = current[normalizedLocale].orEmpty().toMutableMap()
         localeItems[validatedKey] = item.copy(key = validatedKey)
         current + (normalizedLocale to localeItems)
      }
   }

   private fun selectLocales(metadata: ProjectMetadata): List<String>
   {
      val projectLocales = metadata.locales.map(::normalizeLocale).toSet()
      val configuredLocales = if (options.preferredLocales.isNotEmpty()) {
         options.preferredLocales.map(::normalizeLocale)
      }
      else {
         listOfNotNull(options.currentLocaleProvider(), metadata.defaultLocale)
            .map(::normalizeLocale)
      }

      val selected = configuredLocales.filter(projectLocales::contains).distinct()
      return if (selected.isNotEmpty()) selected else listOf(metadata.defaultLocale)
   }

   private fun resolveLocale(locale: String?): String
   {
      return locale?.let(::normalizeLocale)
         ?: options.currentLocaleProvider()?.takeIf { it.isNotBlank() }?.let(::normalizeLocale)
         ?: projectMetadataFlow.value?.defaultLocale
         ?: DEFAULT_LOCALE
   }

   private fun isRefreshStale(referenceTime: Instant): Boolean
   {
      val lastRefresh = lastSuccessfulRefreshAt ?: return true
      return lastRefresh + options.refreshInterval <= referenceTime
   }

   private fun ProjectMetadata.normalize(): ProjectMetadata
   {
      val normalizedLocales = locales.map(::normalizeLocale).distinct()
      val normalizedDefaultLocale = normalizeLocale(defaultLocale)
      return copy(
         locales = if (normalizedDefaultLocale in normalizedLocales) normalizedLocales else normalizedLocales + normalizedDefaultLocale,
         defaultLocale = normalizedDefaultLocale,
      )
   }

   private companion object
   {
      const val DEFAULT_LOCALE = "en"
   }
}

private val validKeyRegex = Regex("^[A-Za-z0-9._-]+$")

private fun validateKey(key: String): String
{
   if (key.isBlank())
      throw TranslationToolsValidationException("Translation key is required.")

   if (!validKeyRegex.matches(key))
      throw TranslationToolsValidationException("Invalid translation key: $key")

   return key
}

private fun normalizeLocale(locale: String): String
{
   return locale.trim().lowercase()
}
