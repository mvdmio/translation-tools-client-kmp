package io.mvdm.translationtools.gradle

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.File

data class TranslationToolsConfig(
   val apiKey: String?,
   val locales: List<String>,
   val snapshotFile: String,
   val generated: GeneratedConfig,
)

data class GeneratedConfig(
   val packageName: String,
   val objectName: String,
)

internal fun resolveConfig(project: Project, extension: TranslationToolsExtension): ResolvedTranslationToolsConfig
{
   val configuredPath = project.findProperty("translationtools.config") as String?
   val configFileProvider: Provider<RegularFile> = if (configuredPath != null) {
      project.provider {
         project.layout.projectDirectory.file(configuredPath)
      }
   }
   else {
      extension.configFile.orElse(project.layout.projectDirectory.file("translationtools.yaml"))
   }
   val configFile = configFileProvider.get().asFile
   if (!configFile.exists())
      throw org.gradle.api.GradleException("TranslationTools config file not found: ${configFile.path}")

   val parsed = parseConfig(configFile)
   val apiKey = (project.findProperty("translationtools.apiKey") as String?)
      ?: System.getenv("TRANSLATIONTOOLS_API_KEY")
      ?: parsed.apiKey

   return ResolvedTranslationToolsConfig(
      configFile = configFile,
      config = parsed.copy(apiKey = apiKey),
   )
}

internal data class ResolvedTranslationToolsConfig(
   val configFile: File,
   val config: TranslationToolsConfig,
)

private fun parseConfig(file: File): TranslationToolsConfig
{
   val settings = LoadSettings.builder().build()
   val loaded = Load(settings).loadFromInputStream(file.inputStream()) as? Map<*, *>
      ?: throw org.gradle.api.GradleException("Invalid TranslationTools config: ${file.path}")

   val apiKey = loaded["apiKey"] as? String
   val locales = (loaded["locales"] as? List<*>)?.map { it.toString() } ?: emptyList()
   val snapshotFile = loaded["snapshotFile"] as? String ?: "translationtools/snapshot.json"
   val generated = loaded["generated"] as? Map<*, *>
      ?: throw org.gradle.api.GradleException("Missing generated config in ${file.path}")
   val packageName = generated["packageName"] as? String
      ?: throw org.gradle.api.GradleException("Missing generated.packageName in ${file.path}")
   val objectName = generated["objectName"] as? String ?: "Res"

   return TranslationToolsConfig(
      apiKey = apiKey,
      locales = locales,
      snapshotFile = snapshotFile,
      generated = GeneratedConfig(packageName = packageName, objectName = objectName),
   )
}
