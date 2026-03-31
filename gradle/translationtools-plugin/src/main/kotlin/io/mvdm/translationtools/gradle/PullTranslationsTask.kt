package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class PullTranslationsTask : DefaultTask()
{
   @get:Input
   abstract val apiKey: Property<String>

   @get:Input
   abstract val configuredLocales: ListProperty<String>

   @get:OutputFile
   abstract val snapshotFile: RegularFileProperty

   internal var httpClientFactory: () -> HttpClient = {
      createDefaultPullHttpClient()
   }

   @TaskAction
   fun pull()
   {
      val resolvedApiKey = apiKey.orNull?.takeIf { it.isNotBlank() }
         ?: throw GradleException("TranslationTools API key is required. Set -Ptranslationtools.apiKey, TRANSLATIONTOOLS_API_KEY, or apiKey in translationtools.yaml.")

      val snapshot = runBlocking {
         val client = httpClientFactory()
         try {
            pullSnapshot(client, resolvedApiKey, configuredLocales.get())
         }
         catch (exception: PullTranslationsException) {
            throw GradleException(exception.message.orEmpty(), exception)
         }
         finally {
            client.close()
         }
      }

      val output = snapshotFile.asFile.get()
      output.parentFile.mkdirs()
      writeSnapshotFile(output, snapshot)

      logger.lifecycle("Loaded config from translationtools.yaml")
      logger.lifecycle("Pulled locales: ${snapshot.project.locales.joinToString(", ")}")
      logger.lifecycle("Updated ${project.relativePath(output)}")
   }
}

internal suspend fun pullSnapshot(
   client: HttpClient,
   apiKey: String,
   configuredLocales: List<String>,
): TranslationSnapshotFile
{
   val metadata = fetchProjectMetadata(client, apiKey)
   val locales = (listOfNotNull(metadata.defaultLocale) + configuredLocales + metadata.locales).distinct().sorted()
   if (locales.isEmpty())
      throw PullTranslationsException("TranslationTools project has no locales configured.")

   val defaultLocale = metadata.defaultLocale ?: locales.first()
   val translations = locales.associateWith { locale ->
      fetchLocaleTranslations(client, apiKey, locale)
         .associate { it.key to it.value }
         .toSortedMap()
   }.toSortedMap()

   return TranslationSnapshotFile(
      schemaVersion = 1,
      project = SnapshotProject(
         defaultLocale = defaultLocale,
         locales = locales,
      ),
      translations = translations,
   )
}

internal suspend fun fetchProjectMetadata(client: HttpClient, apiKey: String): ProjectMetadataResponse
{
   return executePullRequest("project metadata") {
      val body = client.get("$BASE_URL/api/v1/translations/project") {
         header(HttpHeaders.Authorization, apiKey)
         header(HttpHeaders.AcceptEncoding, "gzip")
      }.requireSuccessBodyText()

      pullJson.decodeFromString(ProjectMetadataResponse.serializer(), body)
   }
}

private suspend fun fetchLocaleTranslations(
   client: HttpClient,
   apiKey: String,
   locale: String,
): List<TranslationItemResponse>
{
   return executePullRequest("locale '$locale'") {
      val body = client.get("$BASE_URL/api/v1/translations/$locale") {
         header(HttpHeaders.Authorization, apiKey)
         header(HttpHeaders.AcceptEncoding, "gzip")
      }.requireSuccessBodyText()

      pullJson.decodeFromString(ListSerializer(TranslationItemResponse.serializer()), body)
   }
}

private suspend fun <T> executePullRequest(label: String, action: suspend () -> T): T
{
   try {
      return action()
   }
   catch (exception: PullTranslationsException) {
      throw exception
   }
   catch (exception: SerializationException) {
      throw PullTranslationsException("Failed to parse TranslationTools $label response.", exception)
   }
   catch (exception: ClientRequestException) {
      throw PullTranslationsException(
         "TranslationTools $label request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: ServerResponseException) {
      throw PullTranslationsException(
         "TranslationTools $label request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: Throwable) {
      throw PullTranslationsException("TranslationTools $label request failed: ${exception.message ?: "unknown error"}", exception)
   }
}

private suspend fun io.ktor.client.statement.HttpResponse.requireSuccessBodyText(): String
{
   if (!status.isSuccess()) {
      val body = bodyAsText().trim()
      throw PullTranslationsException("TranslationTools request failed with status ${status.value}: $body")
   }

   return bodyAsText()
}

internal fun createDefaultPullHttpClient(): HttpClient
{
   return HttpClient(io.ktor.client.engine.cio.CIO) {
      install(ContentNegotiation) {
         json(Json { ignoreUnknownKeys = true })
      }
   }
}

internal class PullTranslationsException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal const val BASE_URL = "https://translations.mvdm.io"
private val pullJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class ProjectMetadataResponse(
   val locales: List<String>,
   val defaultLocale: String?,
)

@Serializable
internal data class TranslationItemResponse(
   val key: String,
   val value: String?,
)

private fun normalizeJson(content: String): String
{
   return content.replace("\r\n", "\n").trim()
}

internal fun writeSnapshotFile(output: java.io.File, snapshot: TranslationSnapshotFile)
{
   output.parentFile.mkdirs()
   val rendered = Json { prettyPrint = true }.encodeToString(TranslationSnapshotFile.serializer(), snapshot)
   if (!output.exists() || normalizeJson(output.readText()) != normalizeJson(rendered))
      output.writeText(rendered + System.lineSeparator())
}
