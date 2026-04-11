package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullTranslationsTaskTests
{
   @Test
   fun pullTranslations_should_build_origin_aware_state_from_metadata_and_locales() = runBlocking {
      val client = createHttpClient(
         MockEngine { request ->
            when (request.url.encodedPath) {
               "/api/v1/translations/project" -> respondJson(
                  """{"locales":["en","nl"],"defaultLocale":"en"}"""
               )
               "/api/v1/translations/en" -> respondJson(
                  """[{"origin":":app:/strings.xml","key":"home_title","value":"Home"}]"""
               )
               "/api/v1/translations/nl" -> respondJson(
                  """[{"origin":":app:/strings.xml","key":"home_title","value":"Start"}]"""
               )
               else -> error("Unexpected path: ${request.url.encodedPath}")
            }
         }
      )

      val pulled = pullTranslations(client, apiKey = "test-key", configuredLocales = listOf("nl"))

      assertEquals("en", pulled.project.defaultLocale)
      assertEquals(listOf("en", "nl"), pulled.project.locales)
      assertEquals(1, pulled.items.size)
      assertEquals(":app:/strings.xml", pulled.items.single().origin)
      assertEquals("home_title", pulled.items.single().key)
      assertEquals("Home", pulled.items.single().valuesByLocale["en"])
      assertEquals("Start", pulled.items.single().valuesByLocale["nl"])
   }

   @Test
   fun pullSnapshot_should_throw_clear_error_for_http_failure() {
      val client = createHttpClient(
         MockEngine {
            respond(
               content = "forbidden",
               status = HttpStatusCode.Forbidden,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
         }
      )

      val exception = assertFailsWith<PullTranslationsException> {
         runBlocking {
             pullTranslations(client, apiKey = "test-key", configuredLocales = emptyList())
          }
       }

      assertTrue(exception.message.orEmpty().contains("status 403"))
      assertTrue(exception.message.orEmpty().contains("forbidden"))
   }

   @Test
    fun pullSnapshot_should_throw_clear_error_for_invalid_payload() {
      val client = createHttpClient(
         MockEngine {
            respondJson("""{"bad":true}""")
         }
      )

      val exception = assertFailsWith<PullTranslationsException> {
         runBlocking {
            pullTranslations(client, apiKey = "test-key", configuredLocales = emptyList())
          }
       }

       assertTrue(exception.message.orEmpty().contains("Failed to parse"))
    }

    @Test
     fun pullTranslations_should_fall_back_to_first_locale_when_remote_default_locale_is_missing() = runBlocking {
       val client = createHttpClient(
          MockEngine { request ->
             when (request.url.encodedPath) {
                "/api/v1/translations/project" -> respondJson(
                   """{"locales":["nl","en"],"defaultLocale":null}"""
                )
                "/api/v1/translations/en" -> respondJson("[]")
                "/api/v1/translations/nl" -> respondJson("[]")
                else -> error("Unexpected path: ${request.url.encodedPath}")
             }
          }
       )

        val pulled = pullTranslations(client, apiKey = "test-key", configuredLocales = emptyList())

         assertEquals("en", pulled.project.defaultLocale)
         assertEquals(listOf("en", "nl"), pulled.project.locales)
      }

   @Test
   fun writeStringsFile_should_write_deterministic_xml_and_preserve_local_only_flag()
   {
      val directory = createTempDirectory("translationtools-pull-writer").toFile()
      val target = File(directory, "values/strings.xml")

      writeStringsFile(
         target,
         listOf(
            ParsedAndroidResourceValue(resourceName = "home_title", value = "Home", managedRemotely = true),
            ParsedAndroidResourceValue(resourceName = "device_name", value = "Phone", managedRemotely = false),
         ),
      )

      val rendered = target.readText()
      assertTrue(rendered.contains("<string name=\"home_title\">Home</string>"))
      assertTrue(rendered.contains("<string name=\"device_name\" translatable=\"false\">Phone</string>"))
   }

   @Test
   fun toAndroidValuesDirectory_should_render_region_locale_directories()
   {
      assertEquals("values", toAndroidValuesDirectory("en", "en"))
      assertEquals("values-nl", toAndroidValuesDirectory("nl", "en"))
      assertEquals("values-pt-rBR", toAndroidValuesDirectory("pt-br", "en"))
   }

   @Test
   fun writeStringsFile_should_skip_entries_with_null_values_when_not_already_present()
   {
      val directory = createTempDirectory("translationtools-pull-writer-null").toFile()
      val target = File(directory, "values-nl/strings.xml")

      writeStringsFile(
         target,
         listOf(
            ParsedAndroidResourceValue(resourceName = "home_title", value = "Start", managedRemotely = true),
            ParsedAndroidResourceValue(resourceName = "checkout_title", value = null, managedRemotely = true),
         ),
      )

      val rendered = target.readText()
      assertTrue(rendered.contains("home_title"))
      assertFalse(rendered.contains("checkout_title"))
   }

   @Test
   fun discoverLocaleFiles_should_keep_non_default_locale_only_variants_available_for_base_file_resolution()
   {
      val root = createTempDirectory("translationtools-localized-only-origin").toFile()
      File(root, "values-nl").mkdirs()
      File(root, "values-nl/feature.xml").writeText(
         """
         <resources>
            <string name="only_nl">Alleen nl</string>
         </resources>
         """.trimIndent(),
      )

      val discovery = discoverLocaleFiles(listOf(root), "en")

      assertEquals(1, discovery.files.size)
      assertEquals("feature.xml", discovery.files.single().relativeBaseFile)
      assertEquals(root, discovery.files.single().resourceDirectory)
   }
}

private fun createHttpClient(engine: MockEngine): HttpClient
{
   return HttpClient(engine) {
      install(ContentNegotiation) {
         json(Json { ignoreUnknownKeys = true })
      }
   }
}

private fun MockRequestHandleScope.respondJson(content: String) = respond(
   content = content,
   status = HttpStatusCode.OK,
   headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
