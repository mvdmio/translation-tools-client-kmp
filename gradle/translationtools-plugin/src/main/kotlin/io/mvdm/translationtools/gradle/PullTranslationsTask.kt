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
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class PullTranslationsTask : DefaultTask()
{
   @get:Input
   abstract val apiKey: Property<String>

   @get:Input
   abstract val defaultLocale: Property<String>

   @get:Input
   abstract val resourceDirectories: ListProperty<String>

   @get:Input
   abstract val keyOverrides: MapProperty<String, String>

   @get:Input
   abstract val configuredLocales: ListProperty<String>

   @get:Internal
   internal var httpClientFactory: () -> HttpClient = {
      createDefaultPullHttpClient()
   }

   @TaskAction
   fun pull()
   {
      val resolvedApiKey = apiKey.orNull?.takeIf { it.isNotBlank() }
         ?: throw GradleException("TranslationTools API key is required. Set -Ptranslationtools.apiKey, TRANSLATIONTOOLS_API_KEY, or apiKey in translationtools.yaml.")
      val resolvedDefaultLocale = defaultLocale.orNull?.takeIf { it.isNotBlank() } ?: "en"
      val directories = resourceDirectories.get().map(project::file).filter(File::exists).distinct()
      if (directories.isEmpty())
         throw GradleException("No Android resource directories found. Configure translationtools.yaml androidResources.resourceDirectories.")

      runBlocking {
         val client = httpClientFactory()
         try {
            val remote = pullTranslations(client, resolvedApiKey, configuredLocales.get())
            val local = AndroidStringResourceParser().parse(directories, resolvedDefaultLocale, keyOverrides.getOrElse(emptyMap()), project.path)
            local.warnings.forEach { warning -> logger.warn(warning) }

            val byOrigin = local.entries.groupBy { it.origin }
            val discovery = discoverLocaleFiles(directories, resolvedDefaultLocale)
            val filesByBase = discovery.files.groupBy { it.relativeBaseFile }
            val normalizedDefaultLocale = local.defaultLocale
            val locales = listOf(remote.project.defaultLocale) + remote.project.locales
            val effectiveLocales = locales.distinct().sorted()

            remote.items.groupBy { it.origin }.forEach { (origin, entries) ->
               val mapped = byOrigin[origin]
               if (mapped.isNullOrEmpty()) {
                  logger.warn("Ignored pulled origin '$origin' because no local XML file maps to it.")
                  return@forEach
               }

                val baseFile = mapped.first().owningBaseFile
                val targetVariants = filesByBase[baseFile].orEmpty()
                val targetRoot = targetVariants.firstOrNull()?.resourceDirectory
                if (targetRoot == null) {
                   logger.warn("Ignored pulled origin '$origin' because local base file '$baseFile' could not be resolved.")
                   return@forEach
                }

                effectiveLocales.forEach { locale ->
                  val valuesDirectory = toAndroidValuesDirectory(locale, normalizedDefaultLocale)
                  val targetFile = File(targetRoot, "$valuesDirectory/$baseFile")
                  val targetEntries = entries.associateBy { it.key }
                  val existing = if (targetFile.exists()) parseStringsFile(targetFile.readText()).imported.associateBy { it.resourceName } else emptyMap()
                  val merged = existing.toMutableMap()
                  targetEntries.values.forEach entryLoop@{ entry ->
                     if (!entry.valuesByLocale.containsKey(locale))
                        return@entryLoop

                     val existingEntry = merged[entry.key]
                     merged[entry.key] = ParsedAndroidResourceValue(
                        resourceName = entry.key,
                        value = entry.valuesByLocale[locale],
                        managedRemotely = existingEntry?.managedRemotely ?: true,
                     )
                  }

                  writeStringsFile(
                     targetFile,
                     merged.values.sortedBy { it.resourceName },
                  )
               }
            }
         }
         catch (exception: PullTranslationsException) {
            throw GradleException(exception.message.orEmpty(), exception)
         }
         finally {
            client.close()
         }
      }

      logger.lifecycle("Loaded config from translationtools.yaml")
      logger.lifecycle("Pulled translations into Android XML resources.")
   }
}

internal suspend fun pullTranslations(
   client: HttpClient,
   apiKey: String,
   configuredLocales: List<String>,
): PulledTranslations
{
   val metadata = fetchProjectMetadata(client, apiKey)
   val locales = (listOfNotNull(metadata.defaultLocale) + configuredLocales + metadata.locales).distinct().sorted()
   if (locales.isEmpty())
      throw PullTranslationsException("TranslationTools project has no locales configured.")

   val itemsByOriginKey = linkedMapOf<Pair<String, String>, MutableMap<String, String?>>()
   locales.forEach { locale ->
      fetchLocaleTranslations(client, apiKey, locale).forEach { item ->
         itemsByOriginKey.getOrPut(item.origin to item.key) { linkedMapOf() }[locale] = item.value
      }
   }

   return PulledTranslations(
      project = PulledProject(
         defaultLocale = metadata.defaultLocale ?: locales.first(),
         locales = locales,
      ),
      items = itemsByOriginKey.entries.map { (ref, values) ->
         PulledTranslationItem(origin = ref.first, key = ref.second, valuesByLocale = values.toMap())
      }.sortedWith(compareBy<PulledTranslationItem> { it.origin }.thenBy { it.key }),
   )
}

internal suspend fun fetchProjectMetadata(client: HttpClient, apiKey: String): ProjectMetadataResponse
{
   return executePullRequest("project metadata") {
      val body = client.get("$BASE_URL/api/v1/translations/project") {
         header(HttpHeaders.Authorization, apiKey)
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
   val origin: String,
   val key: String,
   val value: String?,
)

internal data class PulledTranslations(
   val project: PulledProject,
   val items: List<PulledTranslationItem>,
)

internal data class PulledProject(
   val defaultLocale: String,
   val locales: List<String>,
)

internal data class PulledTranslationItem(
   val origin: String,
   val key: String,
   val valuesByLocale: Map<String, String?>,
)

internal fun writeStringsFile(output: File, entries: List<ParsedAndroidResourceValue>)
{
   output.parentFile.mkdirs()
   val presentEntries = entries.filter { it.value != null }
   val rendered = buildString {
      appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
      appendLine("<resources>")
      presentEntries.forEach { entry ->
          append("   <string name=\"")
          append(entry.resourceName)
          append("\"")
         if (!entry.managedRemotely)
            append(" translatable=\"false\"")
         append(">")
         append(escapeXml(entry.value.orEmpty()))
         appendLine("</string>")
      }
      appendLine("</resources>")
   }

   if (!output.exists() || output.readText() != rendered)
      output.writeText(rendered)
}

private fun escapeXml(value: String): String
{
   return value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}

internal fun toAndroidValuesDirectory(locale: String, defaultLocale: String): String
{
   if (locale == defaultLocale)
      return "values"

   val parts = locale.split('-')
   return when (parts.size) {
      1 -> "values-${parts[0]}"
      2 -> "values-${parts[0]}-r${parts[1].uppercase()}"
      else -> "values-${locale}"
   }
}
