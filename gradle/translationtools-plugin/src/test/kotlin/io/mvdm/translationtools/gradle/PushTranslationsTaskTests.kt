package io.mvdm.translationtools.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class PushTranslationsTaskTests
{
   @Test
   fun mergeRemoteAndLocalPushItems_should_preserve_remote_only_items_when_not_pruning()
   {
      val remote = PulledTranslations(
         project = PulledProject(defaultLocale = "en", locales = listOf("en")),
         items = listOf(
            PulledTranslationItem(
               origin = ":app:/strings.xml",
               key = "remote_only",
               valuesByLocale = mapOf("en" to "Remote only"),
            )
         ),
      )

      val local = listOf(
         TranslationPushItemRequest(
            origin = ":app:/strings.xml",
            locale = "en",
            key = "home_title",
            value = "Home",
         )
      )

      val merged = mergeRemoteAndLocalPushItems(remote, local)

      assertEquals(2, merged.size)
      assertEquals(setOf("home_title", "remote_only"), merged.map { it.key }.toSet())
   }

   @Test
   fun mergeRemoteAndLocalPushItems_should_prefer_local_values_for_same_origin_locale_and_key()
   {
      val remote = PulledTranslations(
         project = PulledProject(defaultLocale = "en", locales = listOf("en")),
         items = listOf(
            PulledTranslationItem(
               origin = ":app:/strings.xml",
               key = "home_title",
               valuesByLocale = mapOf("en" to "Old"),
            )
         ),
      )

      val local = listOf(
         TranslationPushItemRequest(
            origin = ":app:/strings.xml",
            locale = "en",
            key = "home_title",
            value = "New",
         )
      )

      val merged = mergeRemoteAndLocalPushItems(remote, local)

      assertEquals(1, merged.size)
      assertEquals("New", merged.single().value)
   }
}
