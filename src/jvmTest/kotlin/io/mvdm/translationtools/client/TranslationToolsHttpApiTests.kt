package io.mvdm.translationtools.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TranslationToolsHttpApiTests
{
   @Test
   fun getProjectMetadata_should_parse_response_and_send_headers() = runTest {
      var capturedPath = ""
      var capturedAuthorization = ""
      var capturedAccept = ""
      val engine = MockEngine { request ->
         capturedPath = request.url.fullPath
         capturedAuthorization = request.headers[HttpHeaders.Authorization].orEmpty()
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
   fun getTranslation_should_encode_origin_path_and_default_value_query() = runTest {
      var capturedPath = ""
      var capturedQuery = ""
      val engine = MockEngine { request ->
         capturedPath = request.url.encodedPath
         capturedQuery = request.url.encodedQuery
         respond(
            content = """{"origin":":app:/strings.xml","key":"home_title","value":"Hello world"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = createApi(engine)

      val response = api.getTranslation("en", TranslationRef(":app:/strings.xml", "home_title"), "Hello world")

      assertEquals("/api/v1/translations/:app:%2Fstrings.xml/en/home_title", capturedPath)
      assertEquals("defaultValue=Hello+world", capturedQuery)
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

   @Test
   fun environment_segment_should_be_appended_trimmed_on_locale_pull() = runTest {
      var capturedPath = ""
      val engine = MockEngine { request ->
         capturedPath = request.url.fullPath
         respond(
            content = "[]",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = TranslationToolsHttpApi(HttpClient(engine), "test-api-key", environment = "  staging  ")

      api.getLocale("nl")

      assertEquals("/api/v1/translations/nl/staging", capturedPath)
   }

   @Test
   fun environment_segment_should_be_appended_as_last_segment_on_single_translation() = runTest {
      var capturedPath = ""
      val engine = MockEngine { request ->
         capturedPath = request.url.fullPath
         respond(
            content = """{"origin":":app:/strings.xml","key":"home_title","value":"Hello"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = TranslationToolsHttpApi(HttpClient(engine), "test-api-key", environment = "prod")

      api.getTranslation("en", TranslationRef(":app:/strings.xml", "home_title"))

      // Environment is the last path segment, after {origin}/{locale}/{key}.
      assertTrue(capturedPath.endsWith("/home_title/prod"), "path was $capturedPath")
   }

   @Test
   fun environment_segment_should_be_omitted_when_not_configured() = runTest {
      val capturedPaths = mutableListOf<String>()
      val engine = MockEngine { request ->
         capturedPaths += request.url.fullPath
         val body = if (request.url.fullPath.endsWith("/project")) """{"locales":["en"],"defaultLocale":"en"}""" else "[]"
         respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = createApi(engine)

      api.getLocale("nl")
      api.getProjectMetadata()

      assertEquals("/api/v1/translations/nl", capturedPaths[0])
      assertEquals("/api/v1/translations/project", capturedPaths[1])
   }

   @Test
   fun project_metadata_should_not_be_environment_scoped() = runTest {
      var capturedPath = ""
      val engine = MockEngine { request ->
         capturedPath = request.url.fullPath
         respond(
            content = """{"locales":["en"],"defaultLocale":"en"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
         )
      }
      val api = TranslationToolsHttpApi(HttpClient(engine), "test-api-key", environment = "prod")

      api.getProjectMetadata()

      assertEquals("/api/v1/translations/project", capturedPath)
   }

   @Test
   fun sendHeartbeat_should_post_json_body_with_auth_and_environment() = runTest {
      var capturedMethod: HttpMethod? = null
      var capturedPath = ""
      var capturedAuthorization = ""
      var capturedBody = ""
      val engine = MockEngine { request ->
         capturedMethod = request.method
         capturedPath = request.url.fullPath
         capturedAuthorization = request.headers[HttpHeaders.Authorization].orEmpty()
         capturedBody = (request.body as TextContent).text
         respond(content = "", status = HttpStatusCode.OK)
      }
      val api = createApi(engine)

      api.sendHeartbeat("client-123", "staging", "kmp-jvm", "2.0.0")

      assertEquals(HttpMethod.Post, capturedMethod)
      assertEquals("/api/v1/translations/heartbeat", capturedPath)
      assertEquals("test-api-key", capturedAuthorization)
      assertContains(capturedBody, "\"clientId\":\"client-123\"")
      assertContains(capturedBody, "\"environment\":\"staging\"")
      assertContains(capturedBody, "\"platform\":\"kmp-jvm\"")
      assertContains(capturedBody, "\"version\":\"2.0.0\"")
   }

   @Test
   fun sendHeartbeat_should_serialize_null_environment() = runTest {
      var capturedBody = ""
      val engine = MockEngine { request ->
         capturedBody = (request.body as TextContent).text
         respond(content = "", status = HttpStatusCode.OK)
      }
      val api = createApi(engine)

      api.sendHeartbeat("client-123", null, "kmp-jvm", "2.0.0")

      assertContains(capturedBody, "\"environment\":null")
   }

   @Test
   fun pushGlobals_should_post_project_with_empty_items_and_globals_list() = runTest {
      var capturedMethod: HttpMethod? = null
      var capturedPath = ""
      var capturedAuthorization = ""
      var capturedBody = ""
      val engine = MockEngine { request ->
         capturedMethod = request.method
         capturedPath = request.url.fullPath
         capturedAuthorization = request.headers[HttpHeaders.Authorization].orEmpty()
         capturedBody = (request.body as TextContent).text
         respond(content = "", status = HttpStatusCode.OK)
      }
      val api = createApi(engine)

      api.pushGlobals("staging", listOf("appName", "year"))

      assertEquals(HttpMethod.Post, capturedMethod)
      assertEquals("/api/v1/translations/project", capturedPath)
      assertEquals("test-api-key", capturedAuthorization)
      assertContains(capturedBody, "\"items\":[]")
      assertContains(capturedBody, "\"environment\":\"staging\"")
      assertContains(capturedBody, "\"globals\":[\"appName\",\"year\"]")
   }

   @Test
   fun pushGlobals_should_serialize_null_environment_and_empty_globals() = runTest {
      var capturedBody = ""
      val engine = MockEngine { request ->
         capturedBody = (request.body as TextContent).text
         respond(content = "", status = HttpStatusCode.OK)
      }
      val api = createApi(engine)

      api.pushGlobals(null, emptyList())

      assertContains(capturedBody, "\"items\":[]")
      assertContains(capturedBody, "\"environment\":null")
      assertContains(capturedBody, "\"globals\":[]")
   }

   private fun createApi(engine: MockEngine): TranslationToolsHttpApi
   {
      val client = HttpClient(engine)
      return TranslationToolsHttpApi(client, "test-api-key")
   }
}
