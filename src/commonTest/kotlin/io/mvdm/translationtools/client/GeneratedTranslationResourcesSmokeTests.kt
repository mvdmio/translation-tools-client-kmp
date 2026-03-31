package io.mvdm.translationtools.client

import io.mvdm.translationtools.client.resources.Res
import io.mvdm.translationtools.client.resources.ResBundledSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GeneratedTranslationResourcesSmokeTests
{
   @Test
   fun generated_resources_should_expose_expected_keys_and_fallbacks()
   {
      assertEquals("checkout.title", Res.string.checkout_title.key)
      assertEquals("Checkout", Res.string.checkout_title.fallback)
      assertEquals("home.title", Res.string.home_title.key)
      assertEquals("Home", Res.string.home_title.fallback)
      assertNotNull(ResBundledSnapshot.value.projectMetadata)
      assertEquals("en", ResBundledSnapshot.value.projectMetadata?.defaultLocale)
   }
}
