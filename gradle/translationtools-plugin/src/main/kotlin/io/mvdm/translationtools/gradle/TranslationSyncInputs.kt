package io.mvdm.translationtools.gradle

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import java.io.File

internal data class ResolvedSyncExecutionInputs(
   val apiKey: String,
   val defaultLocale: String,
   val projectPath: String,
   val baseUrl: String,
   val androidDirectories: List<File>,
   val appleDirectories: List<File>,
)

internal fun resolveSyncExecutionInputs(
   apiKey: Property<String>,
   defaultLocale: Property<String>,
   projectPathInput: Property<String>,
   baseUrl: Property<String>,
   resourceDirectories: ConfigurableFileCollection,
   appleResourceDirectories: ConfigurableFileCollection,
): ResolvedSyncExecutionInputs
{
   val resolvedApiKey = apiKey.orNull?.takeIf { it.isNotBlank() }
      ?: throw GradleException("TranslationTools API key is required. Set -Ptranslationtools.apiKey, TRANSLATIONTOOLS_API_KEY, or apiKey in translationtools.yaml.")
   val androidDirectories = resourceDirectories.files.filter(File::exists).distinct()
   if (androidDirectories.isEmpty())
      throw GradleException("No Android resource directories found. Configure translationtools.yaml androidResources.resourceDirectories.")

   return ResolvedSyncExecutionInputs(
      apiKey = resolvedApiKey,
      defaultLocale = defaultLocale.orNull?.takeIf { it.isNotBlank() } ?: "en",
      projectPath = projectPathInput.get(),
      baseUrl = baseUrl.get(),
      androidDirectories = androidDirectories,
      appleDirectories = appleResourceDirectories.files.filter(File::exists).distinct(),
   )
}
