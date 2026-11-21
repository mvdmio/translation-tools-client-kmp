package io.mvdm.translationtools.client

import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaceholderWarningPlatformLogTests
{
   @Test
   fun a_warning_should_go_to_standard_error_when_the_app_sets_no_handler() = runTest {
      val ref = TranslationRef(":/strings.xml", "greeting")
      val api = object : TranslationToolsApi
      {
         override suspend fun getProjectMetadata(): ProjectMetadata = ProjectMetadata(locales = listOf("en"), defaultLocale = "en")

         override suspend fun getLocale(locale: String): List<TranslationItem> = emptyList()

         override suspend fun getTranslation(locale: String, ref: TranslationRef, defaultValue: String?): TranslationItem =
            TranslationItem(ref, "Hello {userName}")
      }
      val client = TranslationToolsClient(
         options = TranslationToolsClientOptions(apiKey = "test-api-key", backgroundRefreshEnabled = false, heartbeatEnabled = false),
         api = api,
      )
      val buffer = ByteArrayOutputStream()
      val originalErr = System.err

      try {
         System.setErr(PrintStream(buffer, true))
         assertEquals("Hello {userName}", client.get(ref, "en"))
         client.get(ref, "en")
      }
      finally {
         System.setErr(originalErr)
      }

      val lines = buffer.toString().lines().filter { it.isNotEmpty() }
      assertEquals(1, lines.size)
      assertEquals(
         "TranslationTools: No value supplied for placeholder '{userName}'. Key 'greeting', Origin ':/strings.xml', Locale 'en'.",
         lines.single(),
      )
   }
}
