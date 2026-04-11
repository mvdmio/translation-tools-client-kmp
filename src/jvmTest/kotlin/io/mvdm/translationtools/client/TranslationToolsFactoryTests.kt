package io.mvdm.translationtools.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationToolsFactoryTests
{
   @Test
   fun createClient_should_build_client_from_http_client_and_options() = runTest {
      val engine = MockEngine { request ->
         when (request.url.fullPath) {
            "/api/v1/translations/project" -> respond(
               content = """{"locales":["en"],"defaultLocale":"en"}""",
               status = HttpStatusCode.OK,
               headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )

            "/api/v1/translations/en" -> respond(
               content = """[{"origin":":app:/strings.xml","key":"home_title","value":"Hello"}]""",
               status = HttpStatusCode.OK,
               headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )

            else -> respond(
               content = "not found",
               status = HttpStatusCode.NotFound,
               headers = headersOf("Content-Type", ContentType.Text.Plain.toString()),
            )
         }
      }
      val httpClient = HttpClient(engine)
      val store = TranslationSnapshotStores.file(
         filePath = "/cache/translations.json",
         fileSystem = FakeFileSystem(),
      )

      val client = TranslationTools.createClient(
         httpClient = httpClient,
         options = TranslationToolsClientOptions(
            apiKey = "test-api-key",
            snapshotStore = store,
         ),
      )

       client.initialize()

       assertEquals(TranslationRefreshStatus.Ready, client.observeRefreshState().first().status)
       assertEquals("Hello", client.getCached(TranslationRef(":app:/strings.xml", "home_title"), "en"))
    }
}
