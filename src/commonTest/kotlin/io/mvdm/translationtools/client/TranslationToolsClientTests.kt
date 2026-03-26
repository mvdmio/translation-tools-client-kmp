package io.mvdm.translationtools.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TranslationToolsClientTests
{
   @Test
   fun initialize_should_fetch_default_and_current_locale_snapshots() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en", "nl"), defaultLocale = "en"),
         localeItems = mapOf(
            "en" to listOf(TranslationItem("home.title", "Hello")),
            "nl" to listOf(TranslationItem("home.title", "Hallo")),
         ),
      )
      val client = createClient(api, currentLocale = "nl")

      client.initialize()

      assertEquals(listOf("en", "nl"), api.localeRequests.sorted())
      assertEquals("Hallo", client.getCached("home.title", "nl"))
      assertEquals("Hello", client.getCached("home.title", "en"))
   }

   @Test
   fun get_should_fetch_and_cache_single_item_on_miss() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         singleItems = mutableMapOf("en|checkout.title" to TranslationItem("checkout.title", "Checkout")),
      )
      val client = createClient(api)

      val value = client.get("checkout.title", "en")

      assertEquals("Checkout", value)
      assertEquals(listOf("en|checkout.title|<null>"), api.singleItemRequests)
      assertEquals("Checkout", client.getCached("checkout.title", "en"))
   }

   @Test
   fun getCached_should_not_fetch_when_value_missing() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(api)

      assertNull(client.getCached("missing.key", "en"))
      assertEquals(emptyList(), api.singleItemRequests)
   }

   @Test
   fun initialize_should_restore_snapshot_store_before_refresh() = runTest {
      val store = FakeTranslationSnapshotStore(
         stored = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem("home.title", "Cached")))),
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
         )
      )
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem("home.title", "Fresh"))),
      )
      val client = createClient(api, store = store)

      client.initialize()

      assertEquals("Fresh", client.getCached("home.title", "en"))
      assertEquals("en", api.localeRequests.single())
      assertEquals("Fresh", store.saved.single().snapshots.single().items.single().value)
   }

   @Test
   fun refreshIfStale_should_skip_inside_throttle_window() = runTest {
      val clock = MutableClock(Instant.parse("2026-03-25T10:00:00Z"))
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem("home.title", "Hello"))),
      )
      val client = createClient(api, now = clock::now)

      client.initialize()
      clock.current = Instant.parse("2026-03-25T10:30:00Z")
      client.refreshIfStale()

      assertEquals(1, api.projectRequests)
      assertEquals(TranslationRefreshStatus.Ready, client.observeRefreshState().first().status)
   }

   @Test
   fun refreshIfStale_should_refresh_after_throttle_window() = runTest {
      val clock = MutableClock(Instant.parse("2026-03-25T10:00:00Z"))
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem("home.title", "Hello"))),
      )
      val client = createClient(api, now = clock::now)

      client.initialize()
      clock.current = Instant.parse("2026-03-25T11:01:00Z")
      client.refreshIfStale()

      assertEquals(2, api.projectRequests)
   }

   @Test
   fun refreshIfStale_should_collapse_concurrent_calls() = runTest {
      val clock = MutableClock(Instant.parse("2026-03-25T11:01:00Z"))
      val gate = CompletableDeferred<Unit>()
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem("home.title", "Hello"))),
         onProjectMetadata = { gate.await() },
      )
      val client = createClient(api, now = clock::now)

      api.projectRequests = 0

      val first = async { client.refreshIfStale() }
      val second = async { client.refreshIfStale() }
      launch { gate.complete(Unit) }
      awaitAll(first, second)

      assertEquals(1, api.projectRequests)
   }

   @Test
   fun get_should_reject_invalid_key() = runTest {
      val client = createClient(FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en")))

      assertFailsWith<TranslationToolsValidationException> {
         client.get("invalid key", "en")
      }
   }

   private fun createClient(
      api: FakeTranslationToolsApi,
      currentLocale: String? = "en",
      store: TranslationSnapshotStore = NoOpTranslationSnapshotStore,
      now: () -> Instant = { Instant.parse("2026-03-25T10:00:00Z") },
   ): TranslationToolsClient {
      return TranslationToolsClient(
         api = api,
         options = TranslationToolsClientOptions(
            apiKey = "test-api-key",
            currentLocaleProvider = { currentLocale },
            snapshotStore = store,
         ),
         now = now,
      )
   }
}

private class FakeTranslationToolsApi(
   private val metadata: ProjectMetadata,
   private val localeItems: Map<String, List<TranslationItem>> = emptyMap(),
   val singleItems: MutableMap<String, TranslationItem> = mutableMapOf(),
   private val onProjectMetadata: suspend () -> Unit = {},
) : TranslationToolsApi
{
   var projectRequests: Int = 0
   val localeRequests = mutableListOf<String>()
   val singleItemRequests = mutableListOf<String>()

   override suspend fun getProjectMetadata(): ProjectMetadata {
      projectRequests += 1
      onProjectMetadata()
      return metadata
   }

   override suspend fun getLocale(locale: String): List<TranslationItem> {
      localeRequests += locale
      return localeItems[locale].orEmpty()
   }

   override suspend fun getTranslation(locale: String, key: String, defaultValue: String?): TranslationItem {
      singleItemRequests += "$locale|$key|${defaultValue ?: "<null>"}"
      return singleItems["$locale|$key"] ?: TranslationItem(key, defaultValue)
   }
}

private class FakeTranslationSnapshotStore(
   private val stored: StoredTranslations? = null,
) : TranslationSnapshotStore
{
   val saved = mutableListOf<StoredTranslations>()

   override suspend fun load(): StoredTranslations?
   {
      return stored
   }

   override suspend fun save(translations: StoredTranslations)
   {
      saved += translations
   }

   override suspend fun clear()
   {
   }
}

private class MutableClock(var current: Instant)
{
   fun now(): Instant = current
}
