package io.mvdm.translationtools.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationResourceGeneratorTests
{
   @Test
   fun sanitizeKey_should_handle_common_cases()
   {
      assertEquals("home_title", sanitizeResourcePropertyName("home.title"))
      assertEquals("checkout_primary_cta", sanitizeResourcePropertyName("checkout/primary-cta"))
      assertEquals("key_404_title", sanitizeResourcePropertyName("404.title"))
      assertEquals("object_", sanitizeResourcePropertyName("object"))
      assertEquals("key", sanitizeResourcePropertyName("---"))
   }

   @Test
   fun generate_should_add_stable_hash_suffix_for_collisions()
   {
      val snapshot = TranslationSnapshotFile(
         schemaVersion = 1,
         project = SnapshotProject(defaultLocale = "en", locales = listOf("en")),
         translations = mapOf(
            "en" to mapOf(
               "home.title" to "Home",
               "home-title" to "Home dash",
            ),
         ),
      )

      val result = renderTranslationResources(
         snapshot = snapshot,
         packageName = "com.example.translations",
         objectName = "Res",
      )

      assertTrue(result.contains("home_title__"))
      assertTrue(result.contains("key = \"home.title\""))
      assertTrue(result.contains("key = \"home-title\""))
   }

   @Test
   fun generate_should_sort_entries_and_embed_default_locale_fallbacks()
   {
      val snapshot = TranslationSnapshotFile(
         schemaVersion = 1,
         project = SnapshotProject(defaultLocale = "en", locales = listOf("nl", "en")),
         translations = mapOf(
            "nl" to mapOf(
               "z.key" to "Zed",
               "a.key" to "A",
            ),
            "en" to mapOf(
               "z.key" to "Zee",
               "a.key" to "Aye",
            ),
         ),
      )

      val result = renderTranslationResources(
         snapshot = snapshot,
         packageName = "com.example.translations",
         objectName = "Res",
      )

      assertTrue(result.indexOf("val a_key") < result.indexOf("val z_key"))
      assertTrue(result.contains("fallback = \"Aye\""))
      assertTrue(result.contains("fallback = \"Zee\""))
   }
}
