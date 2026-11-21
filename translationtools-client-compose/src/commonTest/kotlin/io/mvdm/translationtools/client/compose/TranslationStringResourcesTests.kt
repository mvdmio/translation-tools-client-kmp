package io.mvdm.translationtools.client.compose

import io.mvdm.translationtools.client.BoundTranslationStringResource
import io.mvdm.translationtools.client.placeholders.PlaceholderSubstitutionException
import io.mvdm.translationtools.client.ProjectMetadata
import io.mvdm.translationtools.client.StoredTranslations
import io.mvdm.translationtools.client.TranslationItem
import io.mvdm.translationtools.client.TranslationRef
import io.mvdm.translationtools.client.TranslationSnapshotStore
import io.mvdm.translationtools.client.TranslationStringResource
import io.mvdm.translationtools.client.TranslationToolsApi
import io.mvdm.translationtools.client.TranslationToolsClient
import io.mvdm.translationtools.client.TranslationToolsClientOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

class TranslationStringResourcesTests
{
   @Test
   fun resolveStringResourceLocale_should_prefer_explicit_locale()
   {
      assertEquals("nl", resolveStringResourceLocale("nl", "en"))
   }

   @Test
   fun resolveStringResourceLocale_should_fall_back_to_composition_locale()
   {
      assertEquals("en", resolveStringResourceLocale(null, "en"))
   }

   @Test
   fun initialStringResourceValue_should_use_cached_value() = runTest {
      val client = createClient(
         localeItems = listOf(TranslationItem(TranslationRef(TEST_ORIGIN, "home_title"), "Home")),
      )
      client.initialize()

      assertEquals(
         "Home",
         initialStringResourceValue(client, TranslationStringResource(TranslationRef(TEST_ORIGIN, "home_title"), "Fallback"), "en"),
      )
   }

   @Test
   fun initialStringResourceValue_should_use_typed_runtime_fallback_chain() = runTest {
      val client = createClient(localeItems = emptyList())

      assertEquals(
         "Fallback",
         initialStringResourceValue(client, TranslationStringResource(TranslationRef(TEST_ORIGIN, "missing_key"), "Fallback"), "en"),
      )
      assertEquals(
         "missing_key",
         initialStringResourceValue(client, TranslationStringResource(TranslationRef(TEST_ORIGIN, "missing_key")), "en"),
      )
   }

   @Test
   fun initialStringResourceValue_should_render_a_bound_resource() = runTest {
      val ref = TranslationRef(TEST_ORIGIN, "greeting")
      val client = createClient(localeItems = listOf(TranslationItem(ref, "Hello {userName}!")))
      client.initialize()
      val bound = BoundTranslationStringResource(TranslationStringResource(ref), mapOf("userName" to "Sam"))

      assertEquals("Hello Sam!", initialStringResourceValue(client, bound, "en"))
   }

   @Test
   fun initialStringResourceValue_should_fill_a_global_placeholder_in_a_plain_resource() = runTest {
      val ref = TranslationRef(TEST_ORIGIN, "welcome")
      val client = createClient(
         localeItems = listOf(TranslationItem(ref, "Welcome to {appName}")),
         globalPlaceholders = mapOf("appName" to { "My App" }),
      )
      client.initialize()
      val resource = TranslationStringResource(ref)

      assertEquals("Welcome to My App", initialStringResourceValue(client, resource, "en"))
      assertEquals(client.get(resource, "en"), initialStringResourceValue(client, resource, "en"))
   }

   @Test
   fun initialStringResourceValue_should_return_a_plain_value_without_braces_unchanged() = runTest {
      val ref = TranslationRef(TEST_ORIGIN, "home_title")
      val items = listOf(TranslationItem(ref, "Home"))
      val withoutGlobals = createClient(localeItems = items)
      val withGlobals = createClient(localeItems = items, globalPlaceholders = mapOf("appName" to { "My App" }))
      withoutGlobals.initialize()
      withGlobals.initialize()
      val resource = TranslationStringResource(ref)

      assertEquals("Home", initialStringResourceValue(withoutGlobals, resource, "en"))
      assertEquals("Home", initialStringResourceValue(withGlobals, resource, "en"))
   }

   @Test
   fun initialStringResourceValue_should_keep_an_unregistered_token_in_a_plain_resource() = runTest {
      val ref = TranslationRef(TEST_ORIGIN, "welcome")
      val client = createClient(localeItems = listOf(TranslationItem(ref, "Welcome to {nobody}")))
      client.initialize()

      assertEquals("Welcome to {nobody}", initialStringResourceValue(client, TranslationStringResource(ref), "en"))
   }

   @Test
   fun initialStringResourceValue_should_throw_for_an_unregistered_token_when_throwing_is_on() = runTest {
      val ref = TranslationRef(TEST_ORIGIN, "welcome")
      val client = createClient(
         localeItems = listOf(TranslationItem(ref, "Welcome to {nobody}")),
         throwOnPlaceholderError = true,
      )
      client.initialize()

      assertFailsWith<PlaceholderSubstitutionException> {
         initialStringResourceValue(client, TranslationStringResource(ref), "en")
      }
   }

   @Test
   fun initialStringResourceValue_should_render_the_fallback_of_a_missing_key() = runTest {
      val client = createClient(localeItems = emptyList(), globalPlaceholders = mapOf("appName" to { "My App" }))
      val resource = TranslationStringResource(TranslationRef(TEST_ORIGIN, "missing_key"), "Welcome to {appName}")

      assertEquals("Welcome to My App", initialStringResourceValue(client, resource, "en"))
      assertEquals(client.get(resource, "en"), initialStringResourceValue(client, resource, "en"))
   }
}

private fun createClient(
   localeItems: List<TranslationItem>,
   globalPlaceholders: Map<String, () -> String?> = emptyMap(),
   throwOnPlaceholderError: Boolean = false,
): TranslationToolsClient
{
   return TranslationToolsClient(
      options = TranslationToolsClientOptions(
         apiKey = "test-api-key",
         currentLocaleProvider = { "en" },
         snapshotStore = NoOpSnapshotStore,
         globalPlaceholders = globalPlaceholders,
         throwOnPlaceholderError = throwOnPlaceholderError,
      ),
      api = FakeComposeTranslationToolsApi(
         metadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en"),
         localeItems = localeItems,
      ),
      now = { Instant.parse("2026-03-25T10:00:00Z") },
   )
}

private class FakeComposeTranslationToolsApi(
   private val metadata: ProjectMetadata,
   private val localeItems: List<TranslationItem>,
) : TranslationToolsApi
{
   override suspend fun getProjectMetadata(): ProjectMetadata = metadata

   override suspend fun getLocale(locale: String): List<TranslationItem> = localeItems

   override suspend fun getTranslation(locale: String, ref: TranslationRef, defaultValue: String?): TranslationItem
   {
      return localeItems.firstOrNull { it.ref == ref } ?: TranslationItem(ref, defaultValue)
   }
}

private const val TEST_ORIGIN = ":test:/strings.xml"

private object NoOpSnapshotStore : TranslationSnapshotStore
{
   override suspend fun load(): StoredTranslations? = null

   override suspend fun save(translations: StoredTranslations)
   {
   }

   override suspend fun clear()
   {
   }
}
