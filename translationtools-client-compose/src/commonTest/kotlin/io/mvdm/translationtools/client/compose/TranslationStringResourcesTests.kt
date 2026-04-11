package io.mvdm.translationtools.client.compose

import io.mvdm.translationtools.client.ProjectMetadata
import io.mvdm.translationtools.client.StoredTranslations
import io.mvdm.translationtools.client.TranslationItem
import io.mvdm.translationtools.client.TranslationRef
import io.mvdm.translationtools.client.TranslationSnapshotStore
import io.mvdm.translationtools.client.TranslationStringResource
import io.mvdm.translationtools.client.TranslationToolsApi
import io.mvdm.translationtools.client.TranslationToolsClient
import io.mvdm.translationtools.client.TranslationToolsClientOptions
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

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
}

private fun createClient(localeItems: List<TranslationItem>): TranslationToolsClient
{
   return TranslationToolsClient(
      options = TranslationToolsClientOptions(
         apiKey = "test-api-key",
         currentLocaleProvider = { "en" },
         snapshotStore = NoOpSnapshotStore,
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
