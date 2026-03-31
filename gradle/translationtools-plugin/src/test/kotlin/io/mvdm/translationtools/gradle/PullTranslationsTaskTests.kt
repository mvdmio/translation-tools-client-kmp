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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PullTranslationsTaskTests
{
   @Test
   fun pullSnapshot_should_build_stable_snapshot_from_metadata_and_locales() = runBlocking {
      val client = createHttpClient(
         MockEngine { request ->
            when (request.url.encodedPath) {
               "/api/v1/translations/project" -> respondJson(
                  """{"locales":["en","nl"],"defaultLocale":"en"}"""
               )
               "/api/v1/translations/en" -> respondJson(
                  """[{"key":"home.title","value":"Home"}]"""
               )
               "/api/v1/translations/nl" -> respondJson(
                  """[{"key":"home.title","value":"Start"}]"""
               )
               else -> error("Unexpected path: ${request.url.encodedPath}")
            }
         }
      )

      val snapshot = pullSnapshot(client, apiKey = "test-key", configuredLocales = listOf("nl"))

      assertEquals(1, snapshot.schemaVersion)
      assertEquals("en", snapshot.project.defaultLocale)
      assertEquals(listOf("en", "nl"), snapshot.project.locales)
      assertEquals("Home", snapshot.translations.getValue("en").getValue("home.title"))
      assertEquals("Start", snapshot.translations.getValue("nl").getValue("home.title"))
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
            pullSnapshot(client, apiKey = "test-key", configuredLocales = emptyList())
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
            pullSnapshot(client, apiKey = "test-key", configuredLocales = emptyList())
         }
      }

       assertTrue(exception.message.orEmpty().contains("Failed to parse"))
    }

    @Test
    fun pullSnapshot_should_fall_back_to_first_locale_when_remote_default_locale_is_missing() = runBlocking {
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

       val snapshot = pullSnapshot(client, apiKey = "test-key", configuredLocales = emptyList())

       assertEquals("en", snapshot.project.defaultLocale)
       assertEquals(listOf("en", "nl"), snapshot.project.locales)
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
