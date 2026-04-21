package io.mvdm.translationtools.client

import io.mvdm.translationtools.client.resources.Translations
import io.mvdm.translationtools.client.resources.TranslationsBundledSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeneratedTranslationResourcesSmokeTests
{
   @Test
   fun generated_resources_should_expose_expected_keys_and_fallbacks()
   {
      assertEquals(":/strings.xml", Translations.checkout_title.ref.origin)
      assertEquals("checkout_title", Translations.checkout_title.ref.key)
      assertEquals("Checkout", Translations.checkout_title.fallback)
      assertEquals("home_title", Translations.home_title.ref.key)
      assertEquals("Home", Translations.home_title.fallback)
      assertNotNull(TranslationsBundledSnapshot.value.projectMetadata)
      assertEquals("en", TranslationsBundledSnapshot.value.projectMetadata?.defaultLocale)
   }
}
