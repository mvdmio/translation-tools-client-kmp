package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

abstract class ImportAndroidResourcesTask : DefaultTask()
{
   @get:Input
   abstract val apiKey: Property<String>

   @get:InputDirectory
   @get:Optional
   abstract val primaryResourceDirectory: DirectoryProperty

   @get:Input
   abstract val additionalResourceDirectories: org.gradle.api.provider.ListProperty<String>

   @get:Input
   abstract val keyOverrides: MapProperty<String, String>

   internal var httpClientFactory: () -> HttpClient = {
      createDefaultPushHttpClient()
   }

   @TaskAction
   fun importResources()
   {
      val resolvedApiKey = apiKey.orNull?.takeIf { it.isNotBlank() }
         ?: throw GradleException("TranslationTools API key is required. Set -Ptranslationtools.apiKey, TRANSLATIONTOOLS_API_KEY, or apiKey in translationtools.yaml.")

      val resourceDirectories = buildList {
         primaryResourceDirectory.orNull?.asFile?.let(::add)
         addAll(additionalResourceDirectories.get().map(project::file))
      }.distinct().filter { it.exists() }

      if (resourceDirectories.isEmpty())
         throw GradleException("No Android resource directories found. Configure translationtools.yaml androidResources.resourceDirectories.")

      val imported = loadAndroidResources(resourceDirectories, keyOverrides.get())
      val request = buildTranslationPushRequest(imported)
      val response = runBlocking {
         val client = httpClientFactory()
         try {
            pushProjectTranslations(client, resolvedApiKey, request)
         }
         catch (exception: TranslationToolsPushException) {
            throw GradleException(exception.message.orEmpty(), exception)
         }
         finally {
            client.close()
         }
      }

      logger.lifecycle("Imported ${request.items.size} keys from Android resources.")
      logger.lifecycle("Created: ${response.createdKeyCount}. Updated default values: ${response.updatedKeyCount}. Removed: ${response.removedKeyCount}.")

      if (imported.localizedEntries.isNotEmpty()) {
         logger.warn(
            "Parsed localized Android resources for ${imported.localizedEntries.keys.joinToString(", ")}, but the current TranslationTools push API only accepts default-locale values.",
         )
      }

      if (imported.skippedEntries.isNotEmpty()) {
         logger.warn("Skipped ${imported.skippedEntries.size} unsupported Android resources:")
         imported.skippedEntries.forEach { entry ->
            logger.warn("- ${entry.resourceName}: ${entry.reason}")
         }
      }
   }
}

internal data class ImportedAndroidResources(
   val defaultLocaleEntries: Map<String, String>,
   val localizedEntries: Map<String, Map<String, String>>,
   val skippedEntries: List<SkippedAndroidResource>,
)

internal data class SkippedAndroidResource(
   val resourceName: String,
   val reason: String,
)

@Serializable
internal data class TranslationPushRequest(
   val items: List<TranslationPushItemRequest>,
)

@Serializable
internal data class TranslationPushItemRequest(
   val key: String,
   val defaultValue: String?,
)

@Serializable
internal data class TranslationPushResponse(
   val receivedKeyCount: Int,
   val createdKeyCount: Int,
   val updatedKeyCount: Int,
   val removedKeyCount: Int,
)

internal fun buildTranslationPushRequest(imported: ImportedAndroidResources): TranslationPushRequest
{
   val items = imported.defaultLocaleEntries.entries
      .sortedBy { it.key }
      .map { (key, value) -> TranslationPushItemRequest(key = key, defaultValue = value) }

   return TranslationPushRequest(items = items)
}

internal fun loadAndroidResources(
   resourceDirectories: List<java.io.File>,
   keyOverrides: Map<String, String>,
): ImportedAndroidResources
{
   val defaultEntries = linkedMapOf<String, String>()
   val localizedEntries = linkedMapOf<String, MutableMap<String, String>>()
   val skippedEntries = mutableListOf<SkippedAndroidResource>()
   val seenKeys = mutableMapOf<String, String>()

   resourceDirectories.forEach { resourceDirectory ->
      resourceDirectory.listFiles()
         ?.filter { it.isDirectory && it.name.startsWith("values") }
         ?.sortedBy { it.name }
         ?.forEach valuesDirectoryLoop@{ valuesDirectory ->
            val locale = valuesDirectory.name.removePrefix("values").trimStart('-').takeIf { it.isNotBlank() }
            if (locale != null && !locale.matches(Regex("[a-z]{2}(?:-r[A-Z]{2})?")))
               return@valuesDirectoryLoop

            val stringsFile = java.io.File(valuesDirectory, "strings.xml")
            if (!stringsFile.exists())
               return@valuesDirectoryLoop

            parseStringResources(stringsFile.readText()).forEach parsedResourceLoop@{ resource ->
               when (resource) {
                  is ParsedStringResource.StringValue -> {
                     if (!resource.translatable) {
                        skippedEntries += SkippedAndroidResource(resource.name, "translatable=false")
                        return@parsedResourceLoop
                     }

                     val translationKey = keyOverrides[resource.name] ?: resource.name.replace('_', '.')
                     val existingName = seenKeys.putIfAbsent(translationKey, resource.name)
                     if (existingName != null && existingName != resource.name)
                        throw IllegalStateException("Android resources '$existingName' and '${resource.name}' both map to TranslationTools key '$translationKey'. Add an explicit key override.")

                     if (locale == null)
                        defaultEntries[translationKey] = resource.value
                     else
                        localizedEntries.getOrPut(normalizeAndroidLocale(locale)) { linkedMapOf() }[translationKey] = resource.value
                  }

                  is ParsedStringResource.Unsupported -> {
                     skippedEntries += SkippedAndroidResource(resource.name, resource.reason)
                  }
               }
            }
         }
   }

   return ImportedAndroidResources(
      defaultLocaleEntries = defaultEntries.toSortedMap(),
      localizedEntries = localizedEntries.toSortedMap().mapValues { (_, value) -> value.toSortedMap() },
      skippedEntries = skippedEntries,
   )
}

private fun normalizeAndroidLocale(qualifier: String): String
{
   return qualifier.replace("-r", "-")
}

private sealed interface ParsedStringResource
{
   data class StringValue(
      val name: String,
      val value: String,
      val translatable: Boolean,
   ) : ParsedStringResource

   data class Unsupported(
      val name: String,
      val reason: String,
   ) : ParsedStringResource
}

private fun parseStringResources(xml: String): List<ParsedStringResource>
{
   val documentBuilder = DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = false
      isIgnoringComments = true
      setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      setFeature("http://xml.org/sax/features/external-general-entities", false)
      setFeature("http://xml.org/sax/features/external-parameter-entities", false)
   }.newDocumentBuilder()
   val document = documentBuilder.parse(InputSource(StringReader(xml)))
   val resources = document.documentElement ?: return emptyList()
   val parsed = mutableListOf<ParsedStringResource>()
   val children = resources.childNodes

   for (index in 0 until children.length) {
      val node = children.item(index)
      if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE)
         continue

      val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
      when (node.nodeName) {
         "string" -> parsed += ParsedStringResource.StringValue(
            name = name,
            value = node.textContent.orEmpty(),
            translatable = node.attributes?.getNamedItem("translatable")?.nodeValue != "false",
         )

         "plurals" -> parsed += ParsedStringResource.Unsupported(name, "plurals are not supported")
         "string-array" -> parsed += ParsedStringResource.Unsupported(name, "string-array is not supported")
      }
   }

   return parsed
}

internal suspend fun pushProjectTranslations(
   client: HttpClient,
   apiKey: String,
   request: TranslationPushRequest,
): TranslationPushResponse
{
   return executePushRequest {
      val body = client.post("$BASE_URL/api/v1/translations/project") {
         header(HttpHeaders.Authorization, apiKey)
         contentType(ContentType.Application.Json)
         setBody(pushJson.encodeToString(request))
      }.requireSuccessBodyText()

      pushJson.decodeFromString(TranslationPushResponse.serializer(), body)
   }
}

private suspend fun <T> executePushRequest(action: suspend () -> T): T
{
   try {
      return action()
   }
   catch (exception: TranslationToolsPushException) {
      throw exception
   }
   catch (exception: SerializationException) {
      throw TranslationToolsPushException("Failed to parse TranslationTools push response.", exception)
   }
   catch (exception: ClientRequestException) {
      throw TranslationToolsPushException(
         "TranslationTools push request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: ServerResponseException) {
      throw TranslationToolsPushException(
         "TranslationTools push request failed with status ${exception.response.status.value}: ${exception.response.bodyAsText().trim()}",
         exception,
      )
   }
   catch (exception: Throwable) {
      throw TranslationToolsPushException("TranslationTools push request failed: ${exception.message ?: "unknown error"}", exception)
   }
}

private suspend fun io.ktor.client.statement.HttpResponse.requireSuccessBodyText(): String
{
   if (!status.isSuccess()) {
      val body = bodyAsText().trim()
      throw TranslationToolsPushException("TranslationTools request failed with status ${status.value}: $body")
   }

   return bodyAsText()
}

internal fun createDefaultPushHttpClient(): HttpClient
{
   return HttpClient(io.ktor.client.engine.cio.CIO) {
      install(ContentNegotiation) {
         json(Json { ignoreUnknownKeys = true })
      }
   }
}

internal class TranslationToolsPushException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

private val pushJson = Json { ignoreUnknownKeys = true }
