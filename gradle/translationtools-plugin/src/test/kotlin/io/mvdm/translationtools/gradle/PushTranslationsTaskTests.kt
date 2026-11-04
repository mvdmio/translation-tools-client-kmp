package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushTranslationsTaskTests
{
   @Test
   fun push_should_upload_both_android_and_apple_strings()
   {
      val projectDir = createTempDirectory("translationtools-push-apple").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         "<resources><string name=\"home_title\">Home</string></resources>"
      )
      File(projectDir, "ios/en.lproj").mkdirs()
      File(projectDir, "ios/en.lproj/InfoPlist.strings").writeText(
         "\"NSCameraUsageDescription\" = \"Camera\";"
      )

      var capturedBody = ""
      val client = createHttpClient(
         MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
               content = """{"receivedKeyCount":2,"createdKeyCount":2,"updatedKeyCount":0,"removedKeyCount":0}""",
               status = HttpStatusCode.OK,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
         }
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.appleResourceDirectories.from(File(projectDir, "ios"))
      task.keyOverrides.set(emptyMap())
      task.projectPathInput.set(project.path)
      task.baseUrl.set(BASE_URL)
      task.httpClientFactory = { client }

      task.push()

      assertTrue(capturedBody.contains(":/strings.xml"))
      assertTrue(capturedBody.contains("home_title"))
      assertTrue(capturedBody.contains(":/InfoPlist.strings"))
      assertTrue(capturedBody.contains("NSCameraUsageDescription"))
   }


   @Test
   fun push_should_send_only_the_sorted_local_items_in_one_request_without_fetching()
   {
      val projectDir = createTempDirectory("translationtools-push-local-only").toFile()
      File(projectDir, "res/values").mkdirs()
      File(projectDir, "res/values/strings.xml").writeText(
         "<resources><string name=\"b_title\">Bee</string><string name=\"a_title\">Ay</string></resources>"
      )

      val requests = mutableListOf<Pair<HttpMethod, String>>()
      var capturedBody = ""
      val client = createHttpClient(
         MockEngine { request ->
            requests += request.method to request.url.encodedPath
            capturedBody = (request.body as TextContent).text
            respond(
               content = """{"receivedKeyCount":2,"createdKeyCount":0,"updatedKeyCount":0,"removedKeyCount":0}""",
               status = HttpStatusCode.OK,
               headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
         }
      )

      val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
      val task = project.tasks.create("pushTranslations", PushTranslationsTask::class.java)
      task.apiKey.set("test-key")
      task.defaultLocale.set("en")
      task.resourceDirectories.from(File(projectDir, "res"))
      task.keyOverrides.set(emptyMap())
      task.projectPathInput.set(project.path)
      task.baseUrl.set(BASE_URL)
      task.httpClientFactory = { client }

      task.push()

      assertEquals(listOf(HttpMethod.Post to "/api/v1/translations/project"), requests)
      val sentKeys = Json.parseToJsonElement(capturedBody).jsonObject.getValue("items").jsonArray
         .map { it.jsonObject.getValue("key").jsonPrimitive.content }
      assertEquals(listOf("a_title", "b_title"), sentKeys)
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
