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
import kotlin.test.assertTrue

class TranslationToolsClientTests
{
   private val homeTitleRef = TranslationRef(TEST_ORIGIN, "home_title")
   private val checkoutTitleRef = TranslationRef(TEST_ORIGIN, "checkout_title")

   @Test
    fun initialize_should_fetch_default_and_current_locale_snapshots() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en", "nl"), defaultLocale = "en"),
         localeItems = mapOf(
            "en" to listOf(TranslationItem(homeTitleRef, "Hello")),
            "nl" to listOf(TranslationItem(homeTitleRef, "Hallo")),
         ),
      )
      val client = createClient(api, currentLocale = "nl")

      client.initialize()

      assertEquals(listOf("en", "nl"), api.localeRequests.sorted())
      assertEquals("Hallo", client.getCached(homeTitleRef, "nl"))
      assertEquals("Hello", client.getCached(homeTitleRef, "en"))
   }

   @Test
   fun get_should_fetch_and_cache_single_item_on_miss() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         singleItems = mutableMapOf("en|$TEST_ORIGIN|checkout_title" to TranslationItem(checkoutTitleRef, "Checkout")),
      )
      val client = createClient(api)

      val value = client.get(checkoutTitleRef, "en")

      assertEquals("Checkout", value)
      assertEquals(listOf("en|$TEST_ORIGIN|checkout_title|<null>"), api.singleItemRequests)
      assertEquals("Checkout", client.getCached(checkoutTitleRef, "en"))
   }

   @Test
   fun getCached_should_not_fetch_when_value_missing() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(api)

      assertNull(client.getCached(TranslationRef(TEST_ORIGIN, "missing_key"), "en"))
      assertEquals(emptyList(), api.singleItemRequests)
   }

   @Test
   fun getCached_with_resource_should_return_cached_value_before_fallback() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Hello"))),
      )
      val client = createClient(api)

      client.initialize()

      assertEquals(
         "Hello",
         client.getCached(TranslationStringResource(ref = homeTitleRef, fallback = "Home"), "en"),
      )
   }

   @Test
   fun getCached_with_resource_should_return_fallback_then_key_when_value_missing() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(api)

      assertEquals(
         "Home",
         client.getCached(TranslationStringResource(ref = homeTitleRef, fallback = "Home"), "en"),
      )
      assertEquals(
         "home_title",
         client.getCached(TranslationStringResource(ref = homeTitleRef), "en"),
      )
   }

   @Test
   fun get_with_resource_should_pass_fallback_to_existing_fetch_path() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         singleItems = mutableMapOf("en|$TEST_ORIGIN|checkout_title" to TranslationItem(checkoutTitleRef, null)),
      )
      val client = createClient(api)

      val value = client.get(TranslationStringResource(ref = checkoutTitleRef, fallback = "Checkout"), "en")

      assertEquals("Checkout", value)
      assertEquals(listOf("en|$TEST_ORIGIN|checkout_title|Checkout"), api.singleItemRequests)
   }

   @Test
   fun observe_with_resource_should_emit_fallback_aware_values() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(api)

      assertEquals(
         "Home",
         client.observe(TranslationStringResource(ref = homeTitleRef, fallback = "Home"), "en").first(),
      )
      assertEquals(
         "home_title",
         client.observe(TranslationStringResource(ref = homeTitleRef), "en").first(),
      )
   }

   @Test
   fun get_with_local_only_resource_should_not_fetch_remote() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(api)

      val value = client.get(
         TranslationStringResource(ref = TranslationRef(TEST_ORIGIN, "about_title"), fallback = "About", managedRemotely = false),
         "en",
      )

      assertEquals("About", value)
      assertTrue(api.singleItemRequests.isEmpty())
   }

   @Test
   fun initialize_should_restore_snapshot_store_before_refresh() = runTest {
      val store = FakeTranslationSnapshotStore(
         stored = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Cached")))),
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
         )
      )
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Fresh"))),
      )
      val client = createClient(api, store = store)

      client.initialize()

      assertEquals("Fresh", client.getCached(homeTitleRef, "en"))
      assertEquals("en", api.localeRequests.single())
      assertEquals("Fresh", store.saved.single().snapshots.single().items.single().value)
   }

   @Test
   fun initialize_should_restore_bundled_snapshot_before_network_when_persisted_store_empty() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Fresh"))),
      )
      val client = createClient(
         api = api,
         bundledSnapshot = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Bundled")))),
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
         ),
         backgroundRefreshEnabled = false,
      )

      client.initialize()

      assertEquals("Bundled", client.getCached(homeTitleRef, "en"))
      assertEquals(0, api.projectRequests)
   }

   @Test
   fun initialize_should_skip_background_refresh_when_disabled() = runTest {
      val store = FakeTranslationSnapshotStore(
         stored = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Cached")))),
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
         )
      )
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Fresh"))),
      )
      val client = createClient(api, store = store, backgroundRefreshEnabled = false)

      client.initialize()

      assertEquals("Cached", client.getCached(homeTitleRef, "en"))
      assertEquals(0, api.projectRequests)
      assertTrue(store.saved.isEmpty())
   }

   @Test
   fun refreshIfStale_should_skip_inside_throttle_window() = runTest {
      val clock = MutableClock(Instant.parse("2026-03-25T10:00:00Z"))
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Hello"))),
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
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Hello"))),
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
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Hello"))),
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
         client.get(TranslationRef(TEST_ORIGIN, "invalid key"), "en")
      }
   }

   private fun createClient(
      api: FakeTranslationToolsApi,
      currentLocale: String? = "en",
      store: TranslationSnapshotStore = NoOpTranslationSnapshotStore,
      bundledSnapshot: StoredTranslations? = null,
      backgroundRefreshEnabled: Boolean = true,
      now: () -> Instant = { Instant.parse("2026-03-25T10:00:00Z") },
   ): TranslationToolsClient {
      return TranslationToolsClient(
         api = api,
         options = TranslationToolsClientOptions(
            apiKey = "test-api-key",
            backgroundRefreshEnabled = backgroundRefreshEnabled,
            currentLocaleProvider = { currentLocale },
            snapshotStore = store,
            bundledSnapshot = bundledSnapshot,
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

   override suspend fun getTranslation(locale: String, ref: TranslationRef, defaultValue: String?): TranslationItem {
      singleItemRequests += "$locale|${ref.origin}|${ref.key}|${defaultValue ?: "<null>"}"
      return singleItems["$locale|${ref.origin}|${ref.key}"] ?: TranslationItem(ref, defaultValue)
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

private const val TEST_ORIGIN = ":test:/strings.xml"
