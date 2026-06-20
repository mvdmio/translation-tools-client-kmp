package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
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
      task.resourceDirectories.set(listOf("res"))
      task.appleResourceDirectories.set(listOf("ios"))
      task.keyOverrides.set(emptyMap())
      task.prune.set(true)
      task.httpClientFactory = { client }

      task.push()

      assertTrue(capturedBody.contains(":/strings.xml"))
      assertTrue(capturedBody.contains("home_title"))
      assertTrue(capturedBody.contains(":/InfoPlist.strings"))
      assertTrue(capturedBody.contains("NSCameraUsageDescription"))
   }


   @Test
   fun mergeRemoteAndLocalPushItems_should_preserve_remote_only_items_when_not_pruning()
   {
      val remote = PulledTranslations(
         project = PulledProject(defaultLocale = "en", locales = listOf("en")),
         items = listOf(
            PulledTranslationItem(
               origin = ":app:/strings.xml",
               key = "remote_only",
               valuesByLocale = mapOf("en" to "Remote only"),
            )
         ),
      )

      val local = listOf(
         TranslationPushItemRequest(
            origin = ":app:/strings.xml",
            locale = "en",
            key = "home_title",
            value = "Home",
         )
      )

      val merged = mergeRemoteAndLocalPushItems(remote, local)

      assertEquals(2, merged.size)
      assertEquals(setOf("home_title", "remote_only"), merged.map { it.key }.toSet())
   }

   @Test
   fun mergeRemoteAndLocalPushItems_should_prefer_local_values_for_same_origin_locale_and_key()
   {
      val remote = PulledTranslations(
         project = PulledProject(defaultLocale = "en", locales = listOf("en")),
         items = listOf(
            PulledTranslationItem(
               origin = ":app:/strings.xml",
               key = "home_title",
               valuesByLocale = mapOf("en" to "Old"),
            )
         ),
      )

      val local = listOf(
         TranslationPushItemRequest(
            origin = ":app:/strings.xml",
            locale = "en",
            key = "home_title",
            value = "New",
         )
      )

      val merged = mergeRemoteAndLocalPushItems(remote, local)

      assertEquals(1, merged.size)
      assertEquals("New", merged.single().value)
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
