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
      assertEquals(":/strings.xml", Res.string.checkout_title.ref.origin)
      assertEquals("checkout_title", Res.string.checkout_title.ref.key)
      assertEquals("Checkout", Res.string.checkout_title.fallback)
      assertEquals("home_title", Res.string.home_title.ref.key)
      assertEquals("Home", Res.string.home_title.fallback)
      assertNotNull(ResBundledSnapshot.value.projectMetadata)
      assertEquals("en", ResBundledSnapshot.value.projectMetadata?.defaultLocale)
   }
}
