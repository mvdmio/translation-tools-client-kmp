package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class PushTranslationsTask : DefaultTask()
{
   @get:Input
   abstract val apiKey: Property<String>

   @get:Input
   abstract val defaultLocale: Property<String>

   @get:InputFiles
   @get:PathSensitive(PathSensitivity.RELATIVE)
   abstract val resourceDirectories: ConfigurableFileCollection

   @get:InputFiles
   @get:PathSensitive(PathSensitivity.RELATIVE)
   abstract val appleResourceDirectories: ConfigurableFileCollection

   @get:Input
   abstract val keyOverrides: MapProperty<String, String>

   @get:Input
   abstract val prune: Property<Boolean>

   @get:Input
   abstract val projectPathInput: Property<String>

   @get:Input
   abstract val baseUrl: Property<String>

   @get:Internal
   internal var parser: AndroidStringResourceParser = AndroidStringResourceParser()

   @get:Internal
   internal var appleParser: AppleStringResourceParser = AppleStringResourceParser()

   @get:Internal
   internal var httpClientFactory: () -> HttpClient = { createDefaultPushHttpClient() }

   @TaskAction
   fun push()
   {
      val inputs = resolveSyncExecutionInputs(
         apiKey,
         defaultLocale,
         projectPathInput,
         baseUrl,
         resourceDirectories,
         appleResourceDirectories,
      )

      runBlocking {
         val state = parser.parse(inputs.androidDirectories, inputs.defaultLocale, keyOverrides.getOrElse(emptyMap()), inputs.projectPath)
          state.warnings.forEach { warning -> logger.warn(warning) }

          val appleProject = if (inputs.appleDirectories.isNotEmpty())
             appleParser.parse(inputs.appleDirectories, inputs.defaultLocale, inputs.projectPath).also { parsed ->
                parsed.warnings.forEach { warning -> logger.warn(warning) }
             }
          else
             null

          val client = httpClientFactory()
          try {
            val androidItems = state.entries
               .filter { it.managedRemotely }
               .sortedBy { it.origin + "|" + it.key }
               .flatMap { entry -> toPushItems(entry.origin, entry.key, entry.valuesByLocale) }

            val appleItems = appleProject?.entries.orEmpty()
               .flatMap { entry -> toPushItems(entry.origin, entry.key, entry.valuesByLocale) }

            val localItems = (androidItems + appleItems)
               .sortedWith(compareBy<TranslationPushItemRequest> { it.origin }.thenBy { it.locale }.thenBy { it.key })

            val items = if (prune.getOrElse(false)) {
               localItems
            }
            else {
               val locales = (state.locales + appleProject?.locales.orEmpty()).distinct().sorted()
               val remote = pullTranslations(client, inputs.apiKey, locales, inputs.baseUrl)
               mergeRemoteAndLocalPushItems(remote, localItems)
            }

            val response = pushProjectTranslations(
                client = client,
                apiKey = inputs.apiKey,
                request = TranslationPushRequest(
                   items = items,
                ),
                baseUrl = inputs.baseUrl,
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
   val merged = linkedMapOf<TranslationPushItemKey, TranslationPushItemRequest>()

   remote.items.forEach { remoteItem ->
      remoteItem.valuesByLocale.forEach { (locale, value) ->
         val item = TranslationPushItemRequest(
            origin = remoteItem.origin,
            locale = locale,
            key = remoteItem.key,
            value = value,
         )
         merged[item.itemKey()] = item
      }
   }

   localItems.forEach { item ->
      merged[item.itemKey()] = item
   }

   return merged.values.sortedWith(compareBy<TranslationPushItemRequest> { it.origin }.thenBy { it.locale }.thenBy { it.key })
}

private fun toPushItems(
   origin: String,
   key: String,
   valuesByLocale: Map<String, String?>,
): List<TranslationPushItemRequest> =
   valuesByLocale.entries.map { (locale, value) ->
      TranslationPushItemRequest(origin = origin, locale = locale, key = key, value = value)
   }
