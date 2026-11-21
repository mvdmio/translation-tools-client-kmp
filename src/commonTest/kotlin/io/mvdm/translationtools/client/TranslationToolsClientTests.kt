package io.mvdm.translationtools.client

import io.mvdm.translationtools.client.placeholders.PlaceholderSubstitutionException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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

   @Test
   fun initialize_should_declare_bundled_keys_with_environment_and_values() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         bundledSnapshot = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en", "nl"), defaultLocale = "en"),
            snapshots = listOf(
               TranslationSnapshot("nl", listOf(TranslationItem(homeTitleRef, "Hallo"), TranslationItem(checkoutTitleRef, null))),
               TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Hello"), TranslationItem(checkoutTitleRef, "Checkout"))),
            ),
            lastSuccessfulRefreshAt = null,
         ),
         environment = " production ",
         backgroundRefreshEnabled = false,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      val (environment, items) = api.declarations.single()
      assertEquals("production", environment)
      assertEquals(
         listOf(
            TranslationDeclarationItem(checkoutTitleRef, "en", "Checkout"),
            TranslationDeclarationItem(homeTitleRef, "en", "Hello"),
            TranslationDeclarationItem(homeTitleRef, "nl", "Hallo"),
         ),
         items,
      )
   }

   @Test
   fun initialize_should_declare_key_without_value_in_default_locale() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         bundledSnapshot = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en", "nl"), defaultLocale = "nl"),
            snapshots = listOf(
               TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Hello"), TranslationItem(checkoutTitleRef, ""))),
               TranslationSnapshot("nl", listOf(TranslationItem(homeTitleRef, "Hallo"), TranslationItem(checkoutTitleRef, null))),
            ),
            lastSuccessfulRefreshAt = null,
         ),
         backgroundRefreshEnabled = false,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      val (environment, items) = api.declarations.single()
      assertNull(environment)
      assertEquals(
         listOf(
            TranslationDeclarationItem(homeTitleRef, "en", "Hello"),
            TranslationDeclarationItem(checkoutTitleRef, "nl", ""),
            TranslationDeclarationItem(homeTitleRef, "nl", "Hallo"),
         ),
         items,
      )
   }

   @Test
   fun initialize_should_declare_key_without_value_in_en_when_bundled_snapshot_has_no_metadata() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         bundledSnapshot = StoredTranslations(
            projectMetadata = null,
            snapshots = listOf(TranslationSnapshot("nl", listOf(TranslationItem(homeTitleRef, null)))),
            lastSuccessfulRefreshAt = null,
         ),
         backgroundRefreshEnabled = false,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      assertEquals(listOf(TranslationDeclarationItem(homeTitleRef, "en", "")), api.declarations.single().second)
   }

   @Test
   fun initialize_should_declare_bundled_keys_when_stored_snapshot_is_restored() = runTest {
      val store = FakeTranslationSnapshotStore(
         stored = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem(checkoutTitleRef, "Stored")))),
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
         )
      )
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         store = store,
         bundledSnapshot = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Bundled")))),
            lastSuccessfulRefreshAt = null,
         ),
         backgroundRefreshEnabled = false,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      assertEquals(listOf(TranslationDeclarationItem(homeTitleRef, "en", "Bundled")), api.declarations.single().second)
   }

   @Test
   fun initialize_should_not_declare_without_bundled_snapshot() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = mapOf("en" to listOf(TranslationItem(homeTitleRef, "Fresh"))),
      )
      val client = createClient(api, backgroundScope = backgroundScope)

      client.initialize()
      runCurrent()

      assertTrue(api.declarations.isEmpty())
   }

   @Test
   fun initialize_should_not_declare_when_bundled_snapshot_has_no_keys() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = createClient(
         api,
         bundledSnapshot = StoredTranslations(
            projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
            snapshots = listOf(TranslationSnapshot("en", emptyList())),
            lastSuccessfulRefreshAt = null,
         ),
         backgroundRefreshEnabled = false,
         backgroundScope = backgroundScope,
      )

      client.initialize()
      runCurrent()

      assertTrue(api.declarations.isEmpty())
   }

   @Test
   fun declaration_failure_should_not_fail_initialize_and_next_start_declares_again() = runTest {
      val bundled = StoredTranslations(
         projectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         snapshots = listOf(TranslationSnapshot("en", listOf(TranslationItem(homeTitleRef, "Hello")))),
         lastSuccessfulRefreshAt = null,
      )
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         declarationFailsFirst = true,
      )

      val first = createClient(api, bundledSnapshot = bundled, backgroundRefreshEnabled = false, backgroundScope = backgroundScope)
      first.initialize()
      runCurrent()
      assertEquals("Hello", first.getCached(homeTitleRef, "en"))
      assertEquals(1, api.declarations.size)

      val second = createClient(api, bundledSnapshot = bundled, backgroundRefreshEnabled = false, backgroundScope = backgroundScope)
      second.initialize()
      runCurrent()

      assertEquals(2, api.declarations.size)
      assertEquals(listOf(TranslationDeclarationItem(homeTitleRef, "en", "Hello")), api.declarations.last().second)
   }

   private val cartRef = TranslationRef(TEST_ORIGIN, "cart_items")
   private val cartResource = TranslationStringResource(cartRef, fallback = ENGLISH_CART)

   @Test
   fun plural_should_render_the_english_form_the_number_picks() = runTest {
      val client = pluralClient("en" to ENGLISH_CART)

      val rendered = listOf(1, 5, 0, 1000, -1).map {
         client.withPlaceholders(cartResource, "en").setPluralNumber(it).setPlaceholder("cartName", "Cart").render()
      }

      assertEquals(
         listOf("1 item in Cart", "5 items in Cart", "0 items in Cart", "1,000 items in Cart", "-1 item in Cart"),
         rendered,
      )
   }

   @Test
   fun plural_should_render_the_polish_form_the_number_picks() = runTest {
      val client = pluralClient("pl" to POLISH_CART)

      val rendered = listOf(1L, 22L, 25L, 12L).map {
         client.withPlaceholders(cartResource, "pl").setPluralNumber(it).setPlaceholder("cartName", "Koszyk").render()
      }

      assertEquals(
         listOf("1 produkt w Koszyk", "22 produkty w Koszyk", "25 produktów w Koszyk", "12 produktów w Koszyk"),
         rendered,
      )
   }

   @Test
   fun plural_should_accept_an_int_number() = runTest {
      val client = pluralClient("pl" to POLISH_CART)

      val rendered = client.withPlaceholders(cartResource, "pl")
         .setPluralNumber(listOf(1, 2).size)
         .setPlaceholder("cartName", "Koszyk")
         .render()

      assertEquals("2 produkty w Koszyk", rendered)
   }

   @Test
   fun plural_should_use_the_last_number_set() = runTest {
      val client = pluralClient("en" to "{count, plural, one {one} other {other}}")

      assertEquals("other", client.withPlaceholders(cartResource, "en").setPluralNumber(1).setPluralNumber(5L).render())
   }

   @Test
   fun plural_exact_case_should_win_over_category() = runTest {
      val client = pluralClient("en" to "{count, plural, =0 {Your cart is empty} one {# item} other {# items}}")

      assertEquals("Your cart is empty", client.withPlaceholders(cartResource, "en").setPluralNumber(0).render())
      assertEquals("1 item", client.withPlaceholders(cartResource, "en").setPluralNumber(1).render())
   }

   @Test
   fun plural_exact_case_should_read_a_leading_zero_as_the_number_and_never_match_an_overflowing_one() = runTest {
      val client = pluralClient("en" to "{count, plural, =99999999999999999999 {big} =01 {uno} other {#}}")

      assertEquals("uno", client.withPlaceholders(cartResource, "en").setPluralNumber(1).render())
      assertEquals("9223372036854775807", client.withPlaceholders(cartResource, "en").setPluralNumber(Long.MAX_VALUE).render().replace(",", ""))
   }

   @Test
   fun plural_in_ja_should_pick_other_for_one() = runTest {
      val client = pluralClient("ja" to "{count, plural, one {# item} other {# items}}")

      assertEquals("1 items", client.withPlaceholders(cartResource, "ja").setPluralNumber(1).render())
   }

   @Test
   fun plural_in_pt_br_should_use_the_pt_rules() = runTest {
      val client = pluralClient("pt-br" to "{count, plural, one {# item} other {# itens}}")

      assertEquals("0 item", client.withPlaceholders(cartResource, "pt-BR").setPluralNumber(0).render())
      assertEquals("2 itens", client.withPlaceholders(cartResource, "pt-BR").setPluralNumber(2).render())
   }

   @Test
   fun plural_in_an_underscore_tag_should_not_split_it_into_the_language() = runTest {
      val client = pluralClient("pt_br" to "{count, plural, one {# item} other {# itens}}")

      assertEquals("1 itens", client.withPlaceholders(cartResource, "pt_br").setPluralNumber(1).render())
   }

   @Test
   fun plural_in_an_unknown_region_of_an_unknown_language_should_pick_other() = runTest {
      val client = pluralClient("xx-yy" to "{count, plural, one {# item} other {# items}}")

      assertEquals("1 items", client.withPlaceholders(cartResource, "xx-yy").setPluralNumber(1).render())
   }

   @Test
   fun plural_in_a_region_with_its_own_rules_should_use_them_before_the_language() = runTest {
      val client = pluralClient("pt-pt" to "{count, plural, one {# item} other {# itens}}")

      assertEquals("0 itens", client.withPlaceholders(cartResource, "pt-PT").setPluralNumber(0).render())
      assertEquals("1 item", client.withPlaceholders(cartResource, "pt-PT").setPluralNumber(1).render())
   }

   @Test
   fun plural_in_arabic_should_pick_zero_and_two() = runTest {
      val client = pluralClient("ar" to "{count, plural, zero {zero} two {two} other {other}}")

      assertEquals("zero", client.withPlaceholders(cartResource, "ar").setPluralNumber(0).render())
      assertEquals("two", client.withPlaceholders(cartResource, "ar").setPluralNumber(2).render())
   }

   @Test
   fun plural_should_pick_by_the_size_of_the_smallest_long_without_overflow() = runTest {
      val client = pluralClient("pl" to "{count, plural, one {one} few {few} many {many} other {other}}")

      // |Long.MIN_VALUE| = 9223372036854775808 ends in 8, so Polish picks many.
      assertEquals("many", client.withPlaceholders(cartResource, "pl").setPluralNumber(Long.MIN_VALUE).render())
      assertEquals("few", client.withPlaceholders(cartResource, "pl").setPluralNumber(-22).render())
   }

   @Test
   fun plural_in_an_unknown_language_should_pick_other_with_plain_digits() = runTest {
      val client = pluralClient("xx" to "{count, plural, one {# item} other {# items}}")

      assertEquals("1 items", client.withPlaceholders(cartResource, "xx").setPluralNumber(1).render())
      assertEquals("1000 items", client.withPlaceholders(cartResource, "xx").setPluralNumber(1000).render())
   }

   @Test
   fun plural_should_group_thousands_in_dutch() = runTest {
      val client = pluralClient("nl" to "{count, plural, one {# artikel} other {# artikelen}}")

      assertEquals("1.000 artikelen", client.withPlaceholders(cartResource, "nl").setPluralNumber(1000).render())
   }

   @Test
   fun plural_should_fall_to_other_when_the_category_has_no_form() = runTest {
      val client = pluralClient("pl" to "{count, plural, one {#a} other {#b}}")

      assertEquals("22b", client.withPlaceholders(cartResource, "pl").setPluralNumber(22).render())
   }

   @Test
   fun plural_should_fall_to_the_last_form_when_there_is_no_other() = runTest {
      val client = pluralClient("en" to "{count, plural, one {single} few {last}}")

      assertEquals("single", client.withPlaceholders(cartResource, "en").setPluralNumber(1).render())
      assertEquals("last", client.withPlaceholders(cartResource, "en").setPluralNumber(5).render())
   }

   @Test
   fun plural_should_print_the_number_for_the_argument_inside_a_form() = runTest {
      val client = pluralClient("en" to "{count, plural, one {{count} item} other {{count} items}}")

      assertEquals("1,000 items", client.withPlaceholders(cartResource, "en").setPluralNumber(1000).render())
   }

   @Test
   fun plural_should_print_a_quoted_hash_literally() = runTest {
      val client = pluralClient("en" to "{count, plural, other {Item '#' of #}}")

      assertEquals("Item # of 3", client.withPlaceholders(cartResource, "en").setPluralNumber(3).render())
   }

   @Test
   fun plural_should_fill_placeholders_and_globals_inside_the_picked_form() = runTest {
      val client = pluralClient(
         "en" to "{count, plural, one {# item in {cartName} on {appName}} other {# items in {cartName} on {appName}}}",
         globalPlaceholders = mapOf("appName" to { "Acme" }),
      )

      val rendered = client.withPlaceholders(cartResource, "en").setPluralNumber(2).setPlaceholder("cartName", "Cart").render()

      assertEquals("2 items in Cart on Acme", rendered)
   }

   @Test
   fun plural_should_not_take_its_number_from_a_global_placeholder() = runTest {
      val client = pluralClient(
         "en" to "{count, plural, other {{count} items}}",
         globalPlaceholders = mapOf("count" to { "GLOBAL" }),
      )

      assertEquals("3 items", client.withPlaceholders(cartResource, "en").setPluralNumber(3).render())
   }

   @Test
   fun plural_should_render_the_default_locale_form_for_fallback_text() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en", "ja"), defaultLocale = "en"),
         singleItems = mutableMapOf("ja|$TEST_ORIGIN|cart_items" to TranslationItem(cartRef, null)),
      )
      val client = pluralClient("en" to ENGLISH_CART, api = api)

      val rendered = client.withPlaceholders(cartResource, "ja").setPluralNumber(1).setPlaceholder("cartName", "Cart").render()

      assertEquals("1 item in Cart", rendered)
      assertEquals(listOf("ja|$TEST_ORIGIN|cart_items|$ENGLISH_CART"), api.singleItemRequests)
   }

   @Test
   fun plural_should_render_the_default_locale_form_for_a_default_value_when_the_cached_value_is_null() = runTest {
      val client = pluralClient("ja" to null)

      val rendered = client.withPlaceholders(cartRef, "ja", ENGLISH_CART).setPluralNumber(1).setPlaceholder("cartName", "Cart").render()

      assertEquals("1 item in Cart", rendered)
   }

   @Test
   fun plural_should_pick_the_form_by_the_locale_of_a_value_from_the_service() = runTest {
      val api = FakeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en", "ja"), defaultLocale = "en"),
         singleItems = mutableMapOf("ja|$TEST_ORIGIN|cart_items" to TranslationItem(cartRef, ENGLISH_CART)),
      )
      val client = pluralClient("en" to ENGLISH_CART, api = api)

      val rendered = client.withPlaceholders(cartResource, "ja").setPluralNumber(1).setPlaceholder("cartName", "Cart").render()

      assertEquals("1 items in Cart", rendered)
   }

   @Test
   fun withPlaceholders_resource_should_read_a_managed_resource_through_the_service() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = pluralClient("en" to ENGLISH_CART, api = api)
      val other = TranslationStringResource(TranslationRef(TEST_ORIGIN, "other_key"), fallback = "Hello {userName}!")

      val rendered = client.withPlaceholders(other, "en").setPlaceholder("userName", "Sam").render()

      assertEquals("Hello Sam!", rendered)
      assertEquals(listOf("en|$TEST_ORIGIN|other_key|Hello {userName}!"), api.singleItemRequests)
   }

   @Test
   fun withPlaceholders_resource_should_render_an_unmanaged_resource_without_a_request() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = pluralClient("en" to ENGLISH_CART, api = api)
      val missing = TranslationStringResource(TranslationRef(TEST_ORIGIN, "local_only"), fallback = ENGLISH_CART, managedRemotely = false)
      val keyOnly = TranslationStringResource(TranslationRef(TEST_ORIGIN, "local_key"), managedRemotely = false)

      val cached = client.withPlaceholders(cartResource.copy(managedRemotely = false), "en").setPluralNumber(5).setPlaceholder("cartName", "Cart").render()
      val fallback = client.withPlaceholders(missing, "en").setPluralNumber(1).setPlaceholder("cartName", "Cart").render()
      val key = client.withPlaceholders(keyOnly, "en").render()

      assertEquals("5 items in Cart", cached)
      assertEquals("1 item in Cart", fallback)
      assertEquals("local_key", key)
      assertEquals(emptyList(), api.singleItemRequests)
   }

   @Test
   fun plural_without_a_number_should_render_the_other_form_with_hash_and_warn() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to ENGLISH_CART, onPlaceholderWarning = { warnings += it })

      val rendered = client.get(cartResource, "en")

      assertEquals("# items in {cartName}", rendered)
      assertEquals(
         listOf(
            "No number supplied for the Plural in this value. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'.",
            "No value supplied for placeholder '{cartName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'.",
         ),
         warnings,
      )
   }

   @Test
   fun plural_without_a_number_should_render_the_last_form_when_there_is_no_other() = runTest {
      val client = pluralClient("en" to "{count, plural, one {# a} few {# b}}")

      assertEquals("# b", client.get(cartResource, "en"))
   }

   @Test
   fun plural_without_a_number_should_throw_when_configured() = runTest {
      val client = pluralClient("en" to ENGLISH_CART, throwOnPlaceholderError = true)

      val fromGet = assertFailsWith<PlaceholderSubstitutionException> { client.get(cartResource, "en") }
      val fromBuilder = assertFailsWith<PlaceholderSubstitutionException> {
         client.withPlaceholders(cartResource, "en").setPlaceholder("cartName", "Cart").render()
      }

      assertEquals("No number supplied for the Plural in this value.", fromGet.message)
      assertEquals("No number supplied for the Plural in this value.", fromBuilder.message)
   }

   @Test
   fun plural_should_not_warn_about_a_placeholder_only_a_form_the_number_did_not_pick_holds() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "{count, plural, =0 {Your cart is empty} other {# items in {cartName}}}", onPlaceholderWarning = { warnings += it })

      val rendered = client.withPlaceholders(cartResource, "en").setPluralNumber(0).setPlaceholder("cartName", "Cart").render()

      assertEquals("Your cart is empty", rendered)
      assertEquals(emptyList(), warnings)
   }

   @Test
   fun plural_should_warn_and_keep_the_raw_placeholder_the_picked_form_needs() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to ENGLISH_CART, onPlaceholderWarning = { warnings += it })

      val rendered = client.withPlaceholders(cartResource, "en").setPluralNumber(1).render()

      assertEquals("1 item in {cartName}", rendered)
      assertEquals(
         listOf("No value supplied for placeholder '{cartName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'."),
         warnings,
      )
   }

   @Test
   fun plural_number_on_a_plain_value_should_be_ignored_silently() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello {userName}!", onPlaceholderWarning = { warnings += it }, throwOnPlaceholderError = true)

      val rendered = client.withPlaceholders(cartResource, "en").setPluralNumber(3).setPlaceholder("userName", "Sam").render()

      assertEquals("Hello Sam!", rendered)
      assertEquals(emptyList(), warnings)
   }

   @Test
   fun plural_fragment_inside_other_text_should_render_as_before() = runTest {
      val client = pluralClient("en" to "You have {count, plural, one {# item} other {# items}} in {cartName}")

      val rendered = client.withPlaceholders(cartResource, "en").setPluralNumber(2).setPlaceholder("cartName", "Cart").render()

      assertEquals("You have {count, plural, one {# item} other {# items}} in Cart", rendered)
   }

   @Test
   fun two_plurals_in_one_value_should_render_as_before() = runTest {
      val twoPlurals = "{a, plural, one {x} other {y}}{b, plural, one {x} other {y}}"
      val client = pluralClient("en" to twoPlurals)

      assertEquals(twoPlurals, client.withPlaceholders(cartResource, "en").setPluralNumber(1).render())
   }

   @Test
   fun brace_zero_should_render_literally_in_a_plain_value_and_in_a_form() = runTest {
      val plain = pluralClient("en" to "Item {0}")
      val inForm = pluralClient("en" to "{count, plural, one {{0} item} other {{0} items}}")

      assertEquals("Item {0}", plain.withPlaceholders(cartResource, "en").setPluralNumber(1).render())
      assertEquals("{0} items", inForm.withPlaceholders(cartResource, "en").setPluralNumber(2).render())
   }

   @Test
   fun getCached_and_observe_should_return_the_raw_plural() = runTest {
      val client = pluralClient("en" to ENGLISH_CART)

      assertEquals(ENGLISH_CART, client.getCached(cartRef, "en"))
      assertEquals(ENGLISH_CART, client.observe(cartRef, "en").first())
   }

   private val greetingBound = BoundTranslationStringResource(cartResource, mapOf("userName" to "Sam"))

   @Test
   fun bound_placeholder_should_render_in_get_getCached_and_observe() = runTest {
      val client = pluralClient("en" to "Hello {userName}!")

      assertEquals("Hello Sam!", client.get(greetingBound, "en"))
      assertEquals("Hello Sam!", client.getCached(greetingBound, "en"))
      assertEquals("Hello Sam!", client.observe(greetingBound, "en").first())
   }

   @Test
   fun bound_plural_should_render_in_get_getCached_and_observe() = runTest {
      val client = pluralClient("en" to ENGLISH_CART, "pl" to POLISH_CART)
      val english = BoundTranslationStringResource(cartResource, mapOf("cartName" to "Cart"), pluralNumber = 1)
      val polish = BoundTranslationStringResource(cartResource, mapOf("cartName" to "Koszyk"), pluralNumber = 22)

      assertEquals("1 item in Cart", client.get(english, "en"))
      assertEquals("1 item in Cart", client.getCached(english, "en"))
      assertEquals("1 item in Cart", client.observe(english, "en").first())
      assertEquals("22 produkty w Koszyk", client.get(polish, "pl"))
      assertEquals("22 produkty w Koszyk", client.getCached(polish, "pl"))
      assertEquals("22 produkty w Koszyk", client.observe(polish, "pl").first())
   }

   @Test
   fun bound_value_should_fill_a_global_placeholder_and_let_its_own_entry_shadow_it() = runTest {
      val client = pluralClient("en" to "{appName} by {company}", globalPlaceholders = mapOf("appName" to { "Shop" }))
      val bound = BoundTranslationStringResource(cartResource, mapOf("company" to "Acme"))
      val shadowing = BoundTranslationStringResource(cartResource, mapOf("company" to "Acme", "appName" to "Other"))

      assertEquals("Shop by Acme", client.get(bound, "en"))
      assertEquals("Shop by Acme", client.getCached(bound, "en"))
      assertEquals("Shop by Acme", client.observe(bound, "en").first())
      assertEquals("Other by Acme", client.getCached(shadowing, "en"))
   }

   @Test
   fun bound_value_that_misses_nothing_should_not_throw_when_configured() = runTest {
      val client = pluralClient("en" to "Hello {userName}!", throwOnPlaceholderError = true)

      assertEquals("Hello Sam!", client.get(greetingBound, "en"))
      assertEquals("Hello Sam!", client.getCached(greetingBound, "en"))
      assertEquals("Hello Sam!", client.observe(greetingBound, "en").first())
   }

   @Test
   fun bound_value_that_misses_a_placeholder_should_throw_when_configured() = runTest {
      val client = pluralClient("en" to "Hello {userName}!", throwOnPlaceholderError = true)
      val missing = BoundTranslationStringResource(cartResource)

      val fromGet = assertFailsWith<PlaceholderSubstitutionException> { client.get(missing, "en") }
      val fromGetCached = assertFailsWith<PlaceholderSubstitutionException> { client.getCached(missing, "en") }
      val fromObserve = assertFailsWith<PlaceholderSubstitutionException> { client.observe(missing, "en").first() }

      assertEquals("No value supplied for placeholder '{userName}'.", fromGet.message)
      assertEquals("No value supplied for placeholder '{userName}'.", fromGetCached.message)
      assertEquals("No value supplied for placeholder '{userName}'.", fromObserve.message)
   }

   @Test
   fun bound_getCached_and_observe_should_serve_the_fallback_by_default_locale_rules_without_a_request() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en", "ja"), defaultLocale = "en"))
      val client = pluralClient("en" to ENGLISH_CART, api = api)
      val missing = TranslationStringResource(TranslationRef(TEST_ORIGIN, "local_only"), fallback = ENGLISH_CART)
      val bound = BoundTranslationStringResource(missing, mapOf("cartName" to "Cart"), pluralNumber = 1)

      assertEquals("1 item in Cart", client.getCached(bound, "ja"))
      assertEquals("1 item in Cart", client.observe(bound, "ja").first())
      assertEquals(emptyList(), api.singleItemRequests)
   }

   @Test
   fun bound_getCached_and_observe_should_serve_the_key_when_there_is_no_fallback() = runTest {
      val client = pluralClient("en" to ENGLISH_CART)
      val keyOnly = BoundTranslationStringResource(
         TranslationStringResource(TranslationRef(TEST_ORIGIN, "local_key")),
         mapOf("userName" to "Sam"),
      )

      assertEquals("local_key", client.getCached(keyOnly, "en"))
      assertEquals("local_key", client.observe(keyOnly, "en").first())
   }

   @Test
   fun bound_get_should_render_an_unmanaged_resource_without_a_request() = runTest {
      val api = FakeTranslationToolsApi(metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"))
      val client = pluralClient("en" to ENGLISH_CART, api = api)
      val missing = TranslationStringResource(TranslationRef(TEST_ORIGIN, "local_only"), fallback = ENGLISH_CART, managedRemotely = false)

      val rendered = client.get(BoundTranslationStringResource(missing, mapOf("cartName" to "Cart"), pluralNumber = 1), "en")

      assertEquals("1 item in Cart", rendered)
      assertEquals(emptyList(), api.singleItemRequests)
   }

   @Test
   fun getCached_with_a_plain_resource_should_still_return_the_raw_value() = runTest {
      val client = pluralClient("en" to "Hello {userName}!")

      assertEquals("Hello {userName}!", client.getCached(cartResource, "en"))
      assertEquals("Hello {userName}!", client.observe(cartResource, "en").first())
   }

   @Test
   fun a_missing_placeholder_should_reach_the_handler_with_key_origin_and_locale() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello {userName}", onPlaceholderWarning = { warnings += it })

      val rendered = client.get(cartRef, "en")

      assertEquals("Hello {userName}", rendered)
      assertEquals(
         listOf("No value supplied for placeholder '{userName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'."),
         warnings,
      )
   }

   @Test
   fun the_same_warning_should_reach_the_handler_once_per_client() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello {userName}", onPlaceholderWarning = { warnings += it })

      repeat(3) { client.get(cartRef, "en") }

      assertEquals(1, warnings.size)
   }

   @Test
   fun the_same_broken_placeholder_in_two_locales_should_warn_once_per_locale() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello {userName}", "nl" to "Hallo {userName}", onPlaceholderWarning = { warnings += it })

      client.get(cartRef, "en")
      client.get(cartRef, "nl")
      client.get(cartRef, "nl")

      assertEquals(
         listOf(
            "No value supplied for placeholder '{userName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'.",
            "No value supplied for placeholder '{userName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'nl'.",
         ),
         warnings,
      )
   }

   @Test
   fun a_warning_for_the_fallback_text_should_name_the_default_locale() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("ja" to null, onPlaceholderWarning = { warnings += it })
      val resource = TranslationStringResource(cartRef, fallback = "Hello {userName}")

      val rendered = client.get(resource, "ja")

      assertEquals("Hello {userName}", rendered)
      assertEquals(
         listOf("No value supplied for placeholder '{userName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'."),
         warnings,
      )
   }

   @Test
   fun a_global_placeholder_resolving_to_null_should_warn_with_key_origin_and_locale() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient(
         "en" to "Welcome to {appName}",
         globalPlaceholders = mapOf("appName" to { null }),
         onPlaceholderWarning = { warnings += it },
      )

      val rendered = client.get(cartRef, "en")

      assertEquals("Welcome to {appName}", rendered)
      assertEquals(
         listOf(
            "Could not resolve global placeholder '{appName}' (resolver failed or returned null)." +
               " Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'.",
         ),
         warnings,
      )
   }

   @Test
   fun a_binding_the_value_does_not_use_should_warn_with_key_origin_and_locale() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello", onPlaceholderWarning = { warnings += it })

      val rendered = client.withPlaceholders(cartRef, "en").setPlaceholder("userName", "Sam").render()

      assertEquals("Hello", rendered)
      assertEquals(
         listOf("Supplied placeholder 'userName' is not present in the value. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'."),
         warnings,
      )
   }

   @Test
   fun a_plural_looked_up_by_ref_without_a_number_should_warn_with_key_origin_and_locale() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "{count, plural, one {# item} other {# items}}", onPlaceholderWarning = { warnings += it })

      val rendered = client.get(cartRef, "en")

      assertEquals("# items", rendered)
      assertEquals(
         listOf("No number supplied for the Plural in this value. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'."),
         warnings,
      )
   }

   @Test
   fun getCached_with_a_bound_resource_should_warn_like_get() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello {userName}", onPlaceholderWarning = { warnings += it })

      val rendered = client.getCached(BoundTranslationStringResource(TranslationStringResource(cartRef)), "en")

      assertEquals("Hello {userName}", rendered)
      assertEquals(
         listOf("No value supplied for placeholder '{userName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'."),
         warnings,
      )
   }

   @Test
   fun observe_with_a_bound_resource_should_warn_like_get() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello {userName}", onPlaceholderWarning = { warnings += it })

      val rendered = client.observe(BoundTranslationStringResource(TranslationStringResource(cartRef)), "en").first()

      assertEquals("Hello {userName}", rendered)
      assertEquals(
         listOf("No value supplied for placeholder '{userName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'."),
         warnings,
      )
   }

   @Test
   fun getCached_with_a_plain_resource_or_ref_should_not_warn() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello {userName}", onPlaceholderWarning = { warnings += it })

      assertEquals("Hello {userName}", client.getCached(cartResource, "en"))
      assertEquals("Hello {userName}", client.getCached(cartRef, "en"))
      assertEquals(emptyList(), warnings)
   }

   @Test
   fun throwOnPlaceholderError_should_throw_the_plain_message_and_report_nothing() = runTest {
      val warnings = mutableListOf<String>()
      val client = pluralClient("en" to "Hello {userName}", throwOnPlaceholderError = true, onPlaceholderWarning = { warnings += it })

      val thrown = assertFailsWith<PlaceholderSubstitutionException> { client.get(cartRef, "en") }

      assertEquals("No value supplied for placeholder '{userName}'.", thrown.message)
      assertEquals(emptyList(), warnings)
   }

   @Test
   fun two_clients_should_each_report_the_same_warning_once() = runTest {
      val first = mutableListOf<String>()
      val second = mutableListOf<String>()
      val firstClient = pluralClient("en" to "Hello {userName}", onPlaceholderWarning = { first += it })
      val secondClient = pluralClient("en" to "Hello {userName}", onPlaceholderWarning = { second += it })

      repeat(2) {
         firstClient.get(cartRef, "en")
         secondClient.get(cartRef, "en")
      }

      assertEquals(
         listOf("No value supplied for placeholder '{userName}'. Key 'cart_items', Origin '$TEST_ORIGIN', Locale 'en'."),
         first,
      )
      assertEquals(first, second)
   }

   @Test
   fun a_muting_handler_should_degrade_without_throwing() = runTest {
      val client = pluralClient("en" to "Hello {userName}", onPlaceholderWarning = {})

      assertEquals("Hello {userName}", client.get(cartRef, "en"))
   }

   private suspend fun pluralClient(
      vararg values: Pair<String, String?>,
      api: FakeTranslationToolsApi? = null,
      onPlaceholderWarning: ((String) -> Unit)? = null,
      globalPlaceholders: Map<String, () -> String?> = emptyMap(),
      throwOnPlaceholderError: Boolean = false,
   ): TranslationToolsClient {
      val locales = (listOf("en") + values.map { it.first }).distinct()
      val metadata = ProjectMetadata(locales = locales, defaultLocale = "en")
      val client = createClient(
         api = api ?: FakeTranslationToolsApi(metadata = metadata),
         bundledSnapshot = StoredTranslations(
            projectMetadata = metadata,
            snapshots = values.map { (locale, value) -> TranslationSnapshot(locale, listOf(TranslationItem(cartRef, value))) },
            lastSuccessfulRefreshAt = Instant.parse("2026-03-25T10:00:00Z"),
         ),
         backgroundRefreshEnabled = false,
         globalPlaceholders = globalPlaceholders,
         throwOnPlaceholderError = throwOnPlaceholderError,
         onPlaceholderWarning = onPlaceholderWarning,
      )

      client.initialize()
      return client
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
      onPlaceholderWarning: ((String) -> Unit)? = null,
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
         onPlaceholderWarning = onPlaceholderWarning,
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
   private val declarationFailsFirst: Boolean = false,
) : TranslationToolsApi
{
   var projectRequests: Int = 0
   val localeRequests = mutableListOf<String>()
   val singleItemRequests = mutableListOf<String>()
   var heartbeatRequests = 0
   var lastHeartbeat: HeartbeatRecord? = null
   val globalsPushes = mutableListOf<Pair<String?, List<String>>>()
   val declarations = mutableListOf<Pair<String?, List<TranslationDeclarationItem>>>()

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

   override suspend fun declareKeys(environment: String?, items: List<TranslationDeclarationItem>) {
      declarations += environment to items
      if (declarationFailsFirst && declarations.size == 1)
         throw RuntimeException("declaration boom")
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

private const val ENGLISH_CART = "{count, plural, one {# item in {cartName}} other {# items in {cartName}}}"

private const val POLISH_CART =
   "{count, plural, one {# produkt w {cartName}} few {# produkty w {cartName}} many {# produktów w {cartName}} other {# produktu w {cartName}}}"
