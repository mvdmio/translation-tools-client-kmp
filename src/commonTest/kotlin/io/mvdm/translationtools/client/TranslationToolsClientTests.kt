package io.mvdm.translationtools.client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

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
      assertEquals("Fresh", store.saved.last().snapshots.single().items.single().value)
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
      // Background refresh is disabled, so no network refresh persists — but a freshly
      // minted client id must still be saved so the device keeps a stable identity.
      val persisted = store.saved.single()
      assertTrue(persisted.clientId!!.isNotBlank())
      assertEquals("Cached", persisted.snapshots.single().items.single().value)
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

   @Test
   fun heartbeat_should_fire_once_on_initialize() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         environment = "staging",
         heartbeatEnabled = true,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      assertEquals(1, api.heartbeatRequests)
      val heartbeat = api.lastHeartbeat!!
      assertTrue(heartbeat.clientId.isNotBlank())
      assertEquals("staging", heartbeat.environment)
      assertTrue(heartbeat.platform in setOf("kmp-jvm", "kmp-android", "kmp-ios"))
      assertTrue(heartbeat.version.isNotBlank())
   }

   @Test
   fun heartbeat_should_fire_again_after_interval() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         heartbeatEnabled = true,
         heartbeatInterval = 1.hours,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()
      assertEquals(1, api.heartbeatRequests)

      advanceTimeBy(1.hours)
      runCurrent()

      assertEquals(2, api.heartbeatRequests)
   }

   @Test
   fun heartbeat_should_not_fire_when_disabled() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         heartbeatEnabled = false,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      assertEquals(0, api.heartbeatRequests)
   }

   @Test
   fun heartbeat_should_use_persisted_client_id_and_carry_it_forward() = runTest {
      val store = FakeTranslationSnapshotStore(
         stored = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Cached")))),
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
            clientId = "fixed-id",
         )
      )
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Fresh"))),
      )
      val client = createClient(
         api,
         store = store,
         heartbeatEnabled = true,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      assertEquals("fixed-id", api.lastHeartbeat!!.clientId)
      assertEquals("fixed-id", store.saved.last().clientId)
   }

   @Test
   fun initialize_should_persist_freshly_generated_client_id_when_background_refresh_disabled() = runTest {
      // Pre-upgrade cache: a snapshot exists but carries no client id, and background refresh is off.
      val store = FakeTranslationSnapshotStore(
         stored = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Cached")))),
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
         )
      )
      val firstApi = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val firstClient = createClient(
         firstApi,
         store = store,
         heartbeatEnabled = true,
         backgroundRefreshEnabled = false,
         backgroundScope = backgroundScope,
      )

      firstClient.initialize()
      runCurrent()

      val firstId = firstApi.lastHeartbeat!!.clientId
      assertEquals(0, firstApi.projectRequests)
      assertEquals(firstId, store.saved.last().clientId)

      // Simulate a restart backed by what the first client persisted: the id must be reused.
      val secondApi = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val secondClient = createClient(
         secondApi,
         store = FakeTranslationSnapshotStore(stored = store.saved.last()),
         heartbeatEnabled = true,
         backgroundRefreshEnabled = false,
         backgroundScope = backgroundScope,
      )

      secondClient.initialize()
      runCurrent()

      assertEquals(firstId, secondApi.lastHeartbeat!!.clientId)
   }

   @Test
   fun initialize_should_persist_freshly_generated_client_id_even_when_initial_refresh_fails() = runTest {
      // No cache and the network is down: the initial refresh throws out of initialize(),
      // but the freshly minted client id must already have been persisted.
      val store = FakeTranslationSnapshotStore()
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         onProjectMetadata = { throw RuntimeException("offline") },
      )
      val client = createClient(
         api,
         store = store,
         heartbeatEnabled = true,
         backgroundScope = backgroundScope,
      )

      assertFailsWith<RuntimeException> { client.initialize() }
      runCurrent()

      val persistedId = store.saved.single().clientId
      assertTrue(persistedId!!.isNotBlank())
      assertEquals(persistedId, api.lastHeartbeat!!.clientId)
   }

   @Test
   fun heartbeat_should_generate_distinct_ids_without_persistence() = runTest {
      val apiA = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val apiB = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val clientA = createClient(apiA, heartbeatEnabled = true, backgroundScope = backgroundScope)
      val clientB = createClient(apiB, heartbeatEnabled = true, backgroundScope = backgroundScope)

      clientA.initialize()
      clientB.initialize()
      runCurrent()

      assertNotEquals(apiA.lastHeartbeat!!.clientId, apiB.lastHeartbeat!!.clientId)
   }

   @Test
   fun heartbeat_failure_should_not_crash_initialize_and_retries_next_interval() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         heartbeatFailsFirst = true,
      )
      val client = createClient(
         api,
         heartbeatEnabled = true,
         heartbeatInterval = 1.hours,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()
      assertEquals(1, api.heartbeatRequests)

      advanceTimeBy(1.hours)
      runCurrent()

      assertEquals(2, api.heartbeatRequests)
   }

   @Test
   fun get_with_placeholders_map_should_substitute_bound_tokens() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Hello {userName}!"))),
      )
      val client = createClient(api)
      client.initialize()

      assertEquals("Hello Bob!", client.get(homeTitleRef, "en", placeholders = mapOf("userName" to "Bob")))
   }

   @Test
   fun get_without_placeholders_should_degrade_unbound_token_to_raw() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Hello {userName}!"))),
      )
      val client = createClient(api)
      client.initialize()

      assertEquals("Hello {userName}!", client.get(homeTitleRef, "en"))
   }

   @Test
   fun get_should_leave_value_without_tokens_unchanged() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Plain value"))),
      )
      val client = createClient(api)
      client.initialize()

      assertEquals("Plain value", client.get(homeTitleRef, "en"))
   }

   @Test
   fun withPlaceholders_builder_should_render_bound_value() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Hello {userName}!"))),
      )
      val client = createClient(api)
      client.initialize()

      val rendered = client.withPlaceholders(homeTitleRef, "en")
         .setPlaceholder("userName", "Bob")
         .render()

      assertEquals("Hello Bob!", rendered)
   }

   @Test
   fun get_should_resolve_registered_global_ambiently() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Welcome to {appName}"))),
      )
      val client = createClient(api, globalPlaceholders = mapOf("appName" to { "Acme" }))
      client.initialize()

      assertEquals("Welcome to Acme", client.get(homeTitleRef, "en"))
   }

   @Test
   fun get_binding_should_shadow_global_of_same_name() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Welcome to {appName}"))),
      )
      val client = createClient(api, globalPlaceholders = mapOf("appName" to { "Acme" }))
      client.initialize()

      assertEquals(
         "Welcome to Local",
         client.get(homeTitleRef, "en", placeholders = mapOf("appName" to "Local")),
      )
   }

   @Test
   fun get_should_throw_on_unbound_token_when_configured() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Hello {userName}!"))),
      )
      val client = createClient(api, throwOnPlaceholderError = true)
      client.initialize()

      assertFailsWith<io.mvdm.translationtools.client.placeholders.PlaceholderSubstitutionException> {
         client.get(homeTitleRef, "en")
      }
   }

   @Test
   fun initialize_should_push_registered_globals_at_startup() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         environment = "staging",
         globalPlaceholders = linkedMapOf("appName" to { "Acme" }, "year" to { "2026" }),
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      assertEquals(1, api.globalsPushes.size)
      val (environment, names) = api.globalsPushes.single()
      assertEquals("staging", environment)
      assertEquals(listOf("appName", "year"), names)
   }

   @Test
   fun initialize_should_not_push_globals_when_none_registered() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(api, backgroundScope = backgroundScope)

      client.initialize()
      runCurrent()

      assertTrue(api.globalsPushes.isEmpty())
   }

   private fun createClient(
      api: FakeTranslationToolsApi,
      currentLocale: String? = "en",
      store: TranslationSnapshotStore = NoOpTranslationSnapshotStore,
      bundledSnapshot: StoredTranslations? = null,
      backgroundRefreshEnabled: Boolean = true,
      environment: String? = null,
      heartbeatEnabled: Boolean = false,
      heartbeatInterval: Duration = 1.hours,
      backgroundScope: CoroutineScope? = null,
      globalPlaceholders: Map<String, () -> String?> = emptyMap(),
      throwOnPlaceholderError: Boolean = false,
      now: () -> Instant = { Instant.parse("2026-03-25T10:00:00Z") },
   ): TranslationToolsClient {
      val options = TranslationToolsClientOptions(
         apiKey = "test-api-key",
         backgroundRefreshEnabled = backgroundRefreshEnabled,
         currentLocaleProvider = { currentLocale },
         snapshotStore = store,
         bundledSnapshot = bundledSnapshot,
         environment = environment,
         heartbeatEnabled = heartbeatEnabled,
         heartbeatInterval = heartbeatInterval,
         globalPlaceholders = globalPlaceholders,
         throwOnPlaceholderError = throwOnPlaceholderError,
      )

      return if (backgroundScope != null) {
         TranslationToolsClient(
            api = api,
            options = options,
            now = now,
            backgroundScope = backgroundScope,
         )
      }
      else {
         TranslationToolsClient(
            api = api,
            options = options,
            now = now,
         )
      }
   }
}

private data class HeartbeatRecord(
   val clientId: String,
   val environment: String?,
   val platform: String,
   val version: String,
)

private class FakeTranslationToolsApi(
   private val metadata: ProjectMetadata,
   private val localeItems: Map<String, List<TranslationItem>> = emptyMap(),
   val singleItems: MutableMap<String, TranslationItem> = mutableMapOf(),
   private val onProjectMetadata: suspend () -> Unit = {},
   private val heartbeatFailsFirst: Boolean = false,
) : TranslationToolsApi
{
   var projectRequests: Int = 0
   val localeRequests = mutableListOf<String>()
   val singleItemRequests = mutableListOf<String>()
   var heartbeatRequests = 0
   var lastHeartbeat: HeartbeatRecord? = null
   val globalsPushes = mutableListOf<Pair<String?, List<String>>>()

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

   override suspend fun sendHeartbeat(clientId: String, environment: String?, platform: String, version: String) {
      heartbeatRequests += 1
      lastHeartbeat = HeartbeatRecord(clientId, environment, platform, version)
      if (heartbeatFailsFirst && heartbeatRequests == 1)
         throw RuntimeException("heartbeat boom")
   }

   override suspend fun pushGlobals(environment: String?, names: List<String>) {
      globalsPushes += environment to names
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
