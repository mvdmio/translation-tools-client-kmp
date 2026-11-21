package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class PullTranslationsTask : DefaultTask()
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
   abstract val configuredLocales: ListProperty<String>

   @get:Input
   abstract val projectPathInput: Property<String>

   @get:Input
   abstract val baseUrl: Property<String>

   @get:Internal
   internal var httpClientFactory: () -> HttpClient = {
      createDefaultHttpClient()
   }

   @TaskAction
   fun pull()
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
         val client = httpClientFactory()
         try {
            val remote = pullTranslations(client, inputs.apiKey, configuredLocales.get(), inputs.baseUrl)
            val local = AndroidStringResourceParser().parse(inputs.androidDirectories, inputs.defaultLocale, keyOverrides.getOrElse(emptyMap()), inputs.projectPath)
            local.warnings.forEach { warning -> logger.warn(warning) }

            val byOrigin = local.entries.groupBy { it.origin }
            val discovery = discoverLocaleFiles(inputs.androidDirectories, inputs.defaultLocale)
            val filesByBase = discovery.files.groupBy { it.relativeBaseFile }
            val normalizedDefaultLocale = local.defaultLocale
            val locales = listOf(remote.project.defaultLocale) + remote.project.locales
            val effectiveLocales = locales.distinct().sorted()

            val appleDiscovery = if (inputs.appleDirectories.isNotEmpty()) discoverAppleLocaleFiles(inputs.appleDirectories, inputs.defaultLocale) else null
            appleDiscovery?.warnings?.forEach { warning -> logger.warn(warning) }
            val appleOrigins = appleDiscovery?.files.orEmpty()
               .map { buildOrigin(inputs.projectPath, it.relativeBaseFile) }
               .toSet()
            val catalogFilesByOrigin = appleDiscovery?.catalogs.orEmpty().groupBy { buildOrigin(inputs.projectPath, it.name) }

            val remoteItemsByOrigin = remote.items.groupBy { it.origin }
            remoteItemsByOrigin.forEach { (origin, entries) ->
               if (origin in appleOrigins || origin in catalogFilesByOrigin)
                  return@forEach

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

                val defaultFile = File(targetRoot, "${toAndroidValuesDirectory(normalizedDefaultLocale, normalizedDefaultLocale)}/$baseFile")
                val defaultLocaleExisting = if (defaultFile.exists()) parseStringsFile(defaultFile.readText()).imported.associateBy { it.resourceName } else emptyMap()

                effectiveLocales.forEach { locale ->
                  val valuesDirectory = toAndroidValuesDirectory(locale, normalizedDefaultLocale)
                  val targetFile = File(targetRoot, "$valuesDirectory/$baseFile")
                  val localText = if (targetFile.exists()) targetFile.readText() else null
                  val localFile = localText?.let(::parseStringsFile) ?: ParsedAndroidStringsFile(emptyList(), emptyList())
                  val changes = pulledLocaleChanges(origin, locale, localFile, defaultLocaleExisting, entries, keyOverrides.getOrElse(emptyMap()))
                  changes.warnings.forEach { warning -> logger.warn(warning) }

                  if (localText == null) {
                     writeStringsFile(targetFile, changes.changes.map { it.resource })
                  }
                  else {
                     val edited = editStringsXml(localText, origin, locale, changes.changes)
                     edited.warnings.forEach { warning -> logger.warn(warning) }
                     if (edited.text != localText)
                        targetFile.writeText(edited.text)
                  }
                }
            }

            if (appleDiscovery != null) {
               writeAppleTranslations(appleDiscovery, inputs.appleDirectories, remote, appleOrigins, effectiveLocales, inputs.projectPath, normalizeAppleLocale(inputs.defaultLocale))
               writeAppleCatalogs(catalogFilesByOrigin, remoteItemsByOrigin)
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

   private fun writeAppleCatalogs(catalogFilesByOrigin: Map<String, List<File>>, remoteItemsByOrigin: Map<String, List<PulledTranslationItem>>)
   {
      remoteItemsByOrigin.forEach { (origin, items) ->
         catalogFilesByOrigin[origin]?.forEach { file ->
            writeAppleCatalogFile(file, items) { detail -> logger.warn(detail) }.forEach { warning -> logger.warn(warning) }
         }
      }
   }

   private fun writeAppleTranslations(
      appleDiscovery: AppleResourceDiscovery,
      appleDirectories: List<File>,
      remote: PulledTranslations,
      appleOrigins: Set<String>,
      effectiveLocales: List<String>,
      projectPath: String,
      defaultLocale: String,
   )
   {
      val variantByOriginLocale = appleDiscovery.files.associateBy { buildOrigin(projectPath, it.relativeBaseFile) to it.locale }
      val filenameByOrigin = appleDiscovery.files.associate { buildOrigin(projectPath, it.relativeBaseFile) to it.relativeBaseFile }
      val kindByOrigin = appleDiscovery.files.associate { buildOrigin(projectPath, it.relativeBaseFile) to it.kind }
      val rootByFilename = appleDiscovery.files.groupBy { it.relativeBaseFile }.mapValues { it.value.first().resourceDirectory }
      val defaultRoot = appleDirectories.first()

      remote.items.filter { it.origin in appleOrigins }.groupBy { it.origin }.forEach { (origin, entries) ->
         val filename = filenameByOrigin[origin] ?: origin.substringAfterLast("/")
         val root = rootByFilename[filename] ?: defaultRoot
         val kind = kindByOrigin.getValue(origin)
         val defaultValueTypes = if (kind == AppleResourceKind.STRINGSDICT) stringsdictValueTypes(variantByOriginLocale[origin to defaultLocale]?.file) else emptyMap()

         effectiveLocales.forEach { locale ->
            val updates = entries
               .filter { it.valuesByLocale.containsKey(locale) }
               .associate { it.key to it.valuesByLocale[locale] }
            val hasContent = updates.values.any { it != null }

            val existingVariant = variantByOriginLocale[origin to locale]
            if (existingVariant == null && !hasContent)
               return@forEach

            val targetFile = existingVariant?.file ?: File(root, "${toLprojDirectory(locale)}/$filename")
            val created = when (kind) {
               AppleResourceKind.STRINGS -> writeAppleStringsFile(targetFile, updates)
               AppleResourceKind.STRINGSDICT -> {
                  val written = writeAppleStringsdictFile(targetFile, locale, updates, defaultValueTypes)
                  written.warnings.forEach { warning -> logger.warn(warning) }
                  written.created
               }
            }
            if (created)
               logger.warn("Created '${targetFile.path}' for locale '$locale'. Add '$locale' (or its region) to your Xcode project's knownRegions to bundle it.")
         }
      }

      logger.lifecycle("Pulled translations into Apple .strings resources.")
   }
}

internal suspend fun pullTranslations(
   client: HttpClient,
   apiKey: String,
   configuredLocales: List<String>,
   baseUrl: String = BASE_URL,
): PulledTranslations
{
   val metadata = fetchProjectMetadata(client, apiKey, baseUrl)
   val locales = (listOfNotNull(metadata.defaultLocale) + configuredLocales + metadata.locales).distinct().sorted()
   if (locales.isEmpty())
      throw PullTranslationsException("TranslationTools project has no locales configured.")

   val itemsByOriginKey = linkedMapOf<Pair<String, String>, MutableMap<String, String?>>()
   locales.forEach { locale ->
      fetchLocaleTranslations(client, apiKey, locale, baseUrl, ::PullTranslationsException).forEach { item ->
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

internal suspend fun fetchProjectMetadata(
   client: HttpClient,
   apiKey: String,
   baseUrl: String = BASE_URL,
): ProjectMetadataResponse
{
   return executeRemoteRequest("project metadata", ::PullTranslationsException) {
      val body = client.get("$baseUrl/api/v1/translations/project") {
         header(HttpHeaders.Authorization, apiKey)
      }.requireSuccessBodyText()

      remoteJson.decodeFromString(ProjectMetadataResponse.serializer(), body)
   }
}

internal class PullTranslationsException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Serializable
internal data class ProjectMetadataResponse(
   val locales: List<String>,
   val defaultLocale: String?,
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

internal data class PulledLocaleChanges(
   val changes: List<PulledChange>,
   val warnings: List<String>,
)

/**
 * Works out what pull writes into the [local] file of [locale]: the resources whose [remoteEntries] value differs from the
 * local one, and a null-valued resource for each local element whose remote value is null. A Key keeps its element type
 * from [defaultLocaleExisting], else from the local element; with neither, it becomes `<plurals>` when its remote value is a
 * Plural that fits one. A `<plurals>` Key whose remote value does not fit, and a Key whose local element the parser skipped,
 * are left as they are, with a warning. Elements are addressed by resource name, so a Key under [keyOverrides] finds its own.
 */
internal fun pulledLocaleChanges(
   origin: String,
   locale: String,
   local: ParsedAndroidStringsFile,
   defaultLocaleExisting: Map<String, ParsedAndroidResourceValue>,
   remoteEntries: List<PulledTranslationItem>,
   keyOverrides: Map<String, String>,
): PulledLocaleChanges
{
   val existing = local.imported.associateBy { it.resourceName }
   val skipped = local.skipped.associateBy { it.resourceName }
   val resourceNameByKey = keyOverrides.entries.associate { it.value to it.key }
   val changes = mutableListOf<PulledChange>()
   val warnings = mutableListOf<String>()

   remoteEntries.forEach { entry ->
      if (!entry.valuesByLocale.containsKey(locale))
         return@forEach

      val resourceName = resourceNameByKey[entry.key] ?: entry.key
      val remoteValue = entry.valuesByLocale[locale]
      val existingEntry = existing[resourceName]
      val managedRemotely = existingEntry?.managedRemotely ?: true
      val remoteItems = remoteValue?.let(::pluralValueToAndroidItems)
      val isPlurals = (defaultLocaleExisting[resourceName] ?: existingEntry)?.let { it.pluralItems != null } ?: (remoteItems != null)

      val skippedElement = skipped[resourceName]
      if (skippedElement != null) {
         if (remoteValue != null)
            warnings += "'${entry.key}' in '$origin' (locale $locale) has a local element pull cannot read (${skippedElement.reason}), so pull left it unchanged."
         return@forEach
      }

      val updated = when {
         remoteValue == null -> ParsedAndroidResourceValue(resourceName, null, managedRemotely)
         !isPlurals -> ParsedAndroidResourceValue(resourceName, remoteValue, managedRemotely)
         remoteItems != null -> pluralsResource(resourceName, remoteItems, managedRemotely)
         else -> {
            warnings += "'${entry.key}' in '$origin' (locale $locale) is not a Plural that fits a <plurals> resource, so pull left it unchanged."
            return@forEach
         }
      }

      val changed = when {
         existingEntry == null -> updated.value != null
         updated.value == null -> true
         else -> existingEntry.pluralItems != updated.pluralItems || existingEntry.value.orEmpty() != updated.value
      }
      if (changed)
         changes += PulledChange(entry.key, updated)
   }

   return PulledLocaleChanges(changes, warnings)
}

internal fun writeStringsFile(output: File, entries: List<ParsedAndroidResourceValue>)
{
   output.parentFile.mkdirs()
   val rendered = buildString {
      appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
      appendLine("<resources>")
      entries.filter { it.value != null }.sortedBy { it.resourceName }.forEach { entry ->
         appendLine("   ${renderAndroidElement(entry, "   ", "\n")}")
      }
      appendLine("</resources>")
   }

   if (!output.exists() || output.readText() != rendered)
      output.writeText(rendered)
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
