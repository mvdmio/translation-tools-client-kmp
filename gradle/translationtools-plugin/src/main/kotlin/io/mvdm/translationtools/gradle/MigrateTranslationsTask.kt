package io.mvdm.translationtools.gradle

import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class MigrateTranslationsTask : DefaultTask()
{
   @get:Input
   abstract val apiKey: Property<String>

   @get:Input
   abstract val defaultLocale: Property<String>

   @get:Input
   abstract val resourceDirectories: ListProperty<String>

   @get:Input
   abstract val configuredLocales: ListProperty<String>

   @get:Input
   abstract val keyOverrides: MapProperty<String, String>

   @get:OutputFile
   abstract val snapshotFile: RegularFileProperty

   internal var parser: AndroidStringResourceParser = AndroidStringResourceParser()
   internal var httpClientFactory: () -> HttpClient = { createDefaultImportHttpClient() }

   @TaskAction
   fun migrate()
   {
      val resolvedApiKey = apiKey.orNull?.takeIf { it.isNotBlank() }
         ?: throw GradleException("TranslationTools API key is required. Set -Ptranslationtools.apiKey, TRANSLATIONTOOLS_API_KEY, or apiKey in translationtools.yaml.")
      val resolvedDefaultLocale = defaultLocale.orNull?.takeIf { it.isNotBlank() }
      val directories = resourceDirectories.get().map(project::file).filter(File::exists).distinct()
      if (directories.isEmpty())
         throw GradleException("No Android resource directories found. Configure translationtools.yaml androidResources.resourceDirectories.")

      runBlocking {
         val client = httpClientFactory()
         try {
            val remoteMetadata = fetchProjectMetadata(client, resolvedApiKey)
            val effectiveDefaultLocale = resolvedDefaultLocale?.takeIf { it.isNotBlank() }
               ?: remoteMetadata.defaultLocale?.takeIf { it.isNotBlank() }
               ?: throw GradleException("TranslationTools default locale is required. Set defaultLocale in translationtools.yaml or configure it remotely before migrate.")
            val state = parser.parse(directories, effectiveDefaultLocale, keyOverrides.get())

            val response = importProjectState(
               client,
               resolvedApiKey,
               ProjectTranslationStateImportRequest(
                  defaultLocale = state.defaultLocale,
                  locales = state.locales,
                  items = state.translations.entries.sortedBy { it.key }.map { (key, translations) ->
                     ProjectTranslationStateImportItemRequest(key = key, translations = translations)
                  },
               ),
            )

            logger.lifecycle("Imported ${response.receivedKeyCount} keys across ${response.receivedLocaleCount} locales.")
            logger.lifecycle("Created translations: ${response.createdTranslationCount}. Updated translations: ${response.updatedTranslationCount}.")

            state.warnings.forEach { warning -> logger.warn(warning) }

            val snapshot = pullSnapshot(client, resolvedApiKey, configuredLocales.get())
            writeSnapshotFile(snapshotFile.asFile.get(), snapshot)
            logger.lifecycle("Updated ${project.relativePath(snapshotFile.asFile.get())}")
         }
         catch (exception: TranslationToolsImportException) {
            throw GradleException(exception.message.orEmpty(), exception)
         }
         finally {
            client.close()
         }
      }
   }
}
