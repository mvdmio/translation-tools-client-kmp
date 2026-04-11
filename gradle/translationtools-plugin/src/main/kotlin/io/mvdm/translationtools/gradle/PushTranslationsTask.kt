package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class PushTranslationsTask : DefaultTask()
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
   abstract val prune: Property<Boolean>

   internal var parser: AndroidStringResourceParser = AndroidStringResourceParser()
   internal var httpClientFactory: () -> HttpClient = { createDefaultPushHttpClient() }

   @TaskAction
   fun push()
   {
      val resolvedApiKey = apiKey.orNull?.takeIf { it.isNotBlank() }
         ?: throw GradleException("TranslationTools API key is required. Set -Ptranslationtools.apiKey, TRANSLATIONTOOLS_API_KEY, or apiKey in translationtools.yaml.")
      val resolvedDefaultLocale = defaultLocale.orNull?.takeIf { it.isNotBlank() }
         ?: "en"
      val directories = resourceDirectories.get().map(project::file).filter(File::exists).distinct()
      if (directories.isEmpty())
         throw GradleException("No Android resource directories found. Configure translationtools.yaml androidResources.resourceDirectories.")

      runBlocking {
         val state = parser.parse(directories, resolvedDefaultLocale, keyOverrides.getOrElse(emptyMap()), project.path)
          state.warnings.forEach { warning -> logger.warn(warning) }

          val client = httpClientFactory()
          try {
            val localItems = state.entries
               .filter { it.managedRemotely }
               .sortedBy { it.origin + "|" + it.key }
               .flatMap { entry ->
                  entry.valuesByLocale.entries.map { (locale, value) ->
                     TranslationPushItemRequest(
                        origin = entry.origin,
                        locale = locale,
                        key = entry.key,
                        value = value,
                     )
                  }
               }

            val items = if (prune.getOrElse(false)) {
               localItems
            }
            else {
               val remote = pullTranslations(client, resolvedApiKey, state.locales)
               mergeRemoteAndLocalPushItems(remote, localItems)
            }

            val response = pushProjectTranslations(
                client = client,
                apiKey = resolvedApiKey,
                request = TranslationPushRequest(
                   items = items,
                ),
             )

            logger.lifecycle("Push complete. Synced ${response.receivedKeyCount} translation values.")
            logger.lifecycle("Created: ${response.createdKeyCount}. Updated values: ${response.updatedKeyCount}. Removed: ${response.removedKeyCount}.")
          }
          catch (exception: TranslationToolsPushException) {
            throw GradleException(exception.message.orEmpty(), exception)
          }
         finally {
            client.close()
         }
      }
   }
}

internal fun mergeRemoteAndLocalPushItems(
   remote: PulledTranslations,
   localItems: List<TranslationPushItemRequest>,
): List<TranslationPushItemRequest>
{
   val merged = linkedMapOf<Triple<String, String, String>, TranslationPushItemRequest>()

   remote.items.forEach { remoteItem ->
      remoteItem.valuesByLocale.forEach { (locale, value) ->
         merged[Triple(remoteItem.origin, locale, remoteItem.key)] = TranslationPushItemRequest(
            origin = remoteItem.origin,
            locale = locale,
            key = remoteItem.key,
            value = value,
         )
      }
   }

   localItems.forEach { item ->
      merged[Triple(item.origin, item.locale, item.key)] = item
   }

   return merged.values.sortedWith(compareBy<TranslationPushItemRequest> { it.origin }.thenBy { it.locale }.thenBy { it.key })
}
