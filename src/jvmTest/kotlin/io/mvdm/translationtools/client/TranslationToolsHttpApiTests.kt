package io.mvdm.translationtools.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranslationToolsHttpApiTests
{
   @Test
   fun getProjectMetadata_should_parse_response_and_send_headers() = runTest {
      var capturedPath = ""
      var capturedAuthorization = ""
      var capturedAcceptEncoding = ""
      var capturedAccept = ""
      val engine = MockEngine { request ->
         capturedPath = request.url.fullPath
         capturedAuthorization = request.headers[HttpHeaders.Authorization].orEmpty()
         capturedAcceptEncoding = request.headers[HttpHeaders.AcceptEncoding].orEmpty()
         capturedAccept = request.headers[HttpHeaders.Accept].orEmpty()

         respond(
            content = """{"locales":["en","nl"],"defaultLocale":"en"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = createApi(engine)

      val response = api.getProjectMetadata()

      assertEquals("/api/v1/translations/project", capturedPath)
      assertEquals("test-api-key", capturedAuthorization)
      assertEquals("gzip", capturedAcceptEncoding)
      assertContains(capturedAccept, ContentType.Application.Json.toString())
      assertEquals(ProjectMetadata(locales = listOf("en", "nl"), defaultLocale = "en"), response)
   }

   @Test
   fun getLocale_should_use_locale_path_and_parse_items() = runTest {
      var capturedPath = ""
      val engine = MockEngine { request ->
         capturedPath = request.url.fullPath
         respond(
            content = """[{"origin":":app:/strings.xml","key":"home_title","value":"Hallo"}]""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = createApi(engine)

      val response = api.getLocale("nl")

      assertEquals("/api/v1/translations/nl", capturedPath)
      assertEquals(listOf(TranslationItem(TranslationRef(":app:/strings.xml", "home_title"), "Hallo")), response)
   }

   @Test
   fun getTranslation_should_encode_default_value_query() = runTest {
      var capturedPath = ""
      var capturedQuery = ""
      val engine = MockEngine { request ->
         capturedPath = request.url.encodedPath
         capturedQuery = request.url.encodedQuery.orEmpty()
         respond(
            content = """{"origin":":app:/strings.xml","key":"home_title","value":"Hello world"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = createApi(engine)

      val response = api.getTranslation("en", TranslationRef(":app:/strings.xml", "home_title"), "Hello world")

      assertEquals("/api/v1/translations/en/home_title", capturedPath)
      assertEquals("origin=%3Aapp%3A%2Fstrings.xml&defaultValue=Hello+world", capturedQuery)
      assertEquals(TranslationItem(TranslationRef(":app:/strings.xml", "home_title"), "Hello world"), response)
   }

   @Test
   fun getProjectMetadata_should_throw_on_non_success() = runTest {
      val engine = MockEngine {
         respond(
            content = "forbidden",
            status = HttpStatusCode.Forbidden,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
         )
      }
      val api = createApi(engine)

      val exception = assertFailsWith<TranslationToolsHttpException> {
         api.getProjectMetadata()
      }

      assertEquals(403, exception.statusCode)
      assertEquals("forbidden", exception.responseBody)
      assertContains(exception.message.orEmpty(), "status 403")
      assertContains(exception.message.orEmpty(), "forbidden")
   }

   @Test
   fun getProjectMetadata_should_throw_serialization_exception_for_invalid_json() = runTest {
      val engine = MockEngine {
         respond(
            content = "{\"bad\":true}",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = createApi(engine)

      val exception = assertFailsWith<TranslationToolsSerializationException> {
         api.getProjectMetadata()
      }

      assertContains(exception.message.orEmpty(), "deserialize")
   }

   private fun createApi(engine: MockEngine): TranslationToolsHttpApi
   {
      val client = HttpClient(engine)
      return TranslationToolsHttpApi(client, "test-api-key")
   }
}
