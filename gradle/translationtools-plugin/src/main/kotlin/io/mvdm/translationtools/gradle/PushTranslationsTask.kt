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
   abstract val projectPathInput: Property<String>

   @get:Input
   abstract val baseUrl: Property<String>

   @get:Internal
   internal var parser: AndroidStringResourceParser = AndroidStringResourceParser()

   @get:Internal
   internal var appleParser: AppleStringResourceParser = AppleStringResourceParser()

   @get:Internal
   internal var httpClientFactory: () -> HttpClient = { createDefaultHttpClient() }

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
          androidFormatSpecifierWarnings(state.entries).forEach { warning -> logger.warn(warning) }

          val appleProject = if (inputs.appleDirectories.isNotEmpty())
             appleParser.parse(inputs.appleDirectories, inputs.defaultLocale, inputs.projectPath).also { parsed ->
                parsed.warnings.forEach { warning -> logger.warn(warning) }
                parsed.details.forEach { detail -> logger.warn(detail) }
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

            val items = (androidItems + appleItems)
               .sortedWith(compareBy<TranslationPushItemRequest> { it.origin }.thenBy { it.locale }.thenBy { it.key })

            val defaultLocaleByOrigin = state.entries.associate { it.origin to state.defaultLocale } + appleProject?.defaultLocaleByOrigin.orEmpty()
            val nonDefaultLocales = items
               .filter { it.locale != defaultLocaleByOrigin.getValue(it.origin) }
               .map { it.locale }
               .distinct()
            val remoteItemsByLocale = nonDefaultLocales.associateWith { locale ->
               fetchLocaleTranslations(client, inputs.apiKey, locale, inputs.baseUrl, ::TranslationToolsPushException)
            }
            val guarded = keepRemoteNonDefaultValues(items, defaultLocaleByOrigin, remoteItemsByLocale)
            guarded.warnings.forEach { warning -> logger.warn(warning) }

            val response = pushProjectTranslations(
                client = client,
                apiKey = inputs.apiKey,
                request = TranslationPushRequest(
                   items = guarded.items,
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

private fun toPushItems(
   origin: String,
   key: String,
   valuesByLocale: Map<String, String?>,
): List<TranslationPushItemRequest> =
   valuesByLocale.entries.map { (locale, value) ->
      TranslationPushItemRequest(origin = origin, locale = locale, key = key, value = value)
   }

private data class TranslationSlot(val origin: String, val key: String, val locale: String)

internal data class GuardedPushItems(
   val items: List<TranslationPushItemRequest>,
   val warnings: List<String>,
)

/**
 * Applies the rule that TranslationTools owns a non-default value it holds (docs/adr/0007). A default-locale item (per
 * [defaultLocaleByOrigin]) goes up unchanged. Every other item stays in the upload, but keeps its local value only where
 * [remoteItemsByLocale] holds none for the same Origin, Key and Locale; otherwise its value is null, which TranslationTools reads
 * as "keep the stored value", with a warning when a non-empty local value differs. Push never sends `""` outside the default
 * Locale, and the read is only compared: a Key only TranslationTools has stays out.
 */
internal fun keepRemoteNonDefaultValues(
   items: List<TranslationPushItemRequest>,
   defaultLocaleByOrigin: Map<String, String>,
   remoteItemsByLocale: Map<String, List<TranslationItemResponse>>,
): GuardedPushItems
{
   val remoteValues = remoteItemsByLocale.flatMap { (locale, remoteItems) ->
      remoteItems.map { TranslationSlot(it.origin, it.key, locale) to it.value.orEmpty() }
   }.toMap()
   val warnings = mutableListOf<String>()

   val guarded = items.map { item ->
      if (item.locale == defaultLocaleByOrigin.getValue(item.origin))
         return@map item

      val local = item.value.orEmpty()
      val remote = remoteValues[TranslationSlot(item.origin, item.key, item.locale)].orEmpty()
      when {
         remote.isEmpty() -> item.copy(value = local.ifEmpty { null })
         local.isEmpty() || local == remote -> item.copy(value = null)
         else -> {
            warnings += "Your local value of '${item.key}' in '${item.origin}' (locale ${item.locale}) differs from the one on TranslationTools, " +
               "so push did not send it. Push never replaces a value outside the default Locale. " +
               "To change the value, edit it on TranslationTools. To copy the TranslationTools value into your file, run pullTranslations."
            item.copy(value = null)
         }
      }
   }

   return GuardedPushItems(guarded, warnings)
}
