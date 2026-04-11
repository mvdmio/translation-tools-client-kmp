package io.mvdm.translationtools.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationResourceGeneratorTests
{
   private val baseProject = AndroidTranslationProject(
      defaultLocale = "en",
      locales = listOf("en", "nl"),
      entries = emptyList(),
      warnings = emptyList(),
   )

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
      val project = baseProject.copy(
         locales = listOf("en"),
         entries = listOf(
            AndroidTranslationEntry(":app:/strings.xml", "home.title", "home.title", mapOf("en" to "Home"), "strings.xml", true),
            AndroidTranslationEntry(":app:/strings.xml", "home-title", "home-title", mapOf("en" to "Home dash"), "strings.xml", true),
         ),
      )

      val result = renderTranslationResources(
         project = project,
         packageName = "com.example.translations",
         objectName = "Res",
      )

      assertTrue(result.contains("home_title__"))
      assertTrue(result.contains("key = \"home.title\""))
      assertTrue(result.contains("key = \"home-title\""))
      assertTrue(result.contains("TranslationRef("))
    }

   @Test
   fun generate_should_sort_entries_and_embed_default_locale_fallbacks()
   {
      val project = baseProject.copy(
         entries = listOf(
            AndroidTranslationEntry(":app:/strings.xml", "z.key", "z.key", mapOf("en" to "Zee", "nl" to "Zed"), "strings.xml", true),
            AndroidTranslationEntry(":app:/strings.xml", "a.key", "a.key", mapOf("en" to "Aye", "nl" to "A"), "strings.xml", true),
         ),
      )

      val result = renderTranslationResources(
         project = project,
         packageName = "com.example.translations",
         objectName = "Res",
      )

      assertTrue(result.indexOf("val a_key") < result.indexOf("val z_key"))
      assertTrue(result.contains("fallback = \"Aye\""))
      assertTrue(result.contains("fallback = \"Zee\""))
   }
}
