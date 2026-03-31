package io.mvdm.translationtools.gradle

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.File

data class TranslationToolsConfig(
   val apiKey: String?,
   val defaultLocale: String?,
   val locales: List<String>,
   val snapshotFile: String,
   val generated: GeneratedConfig,
   val androidResources: AndroidResourcesConfig,
)

data class GeneratedConfig(
   val packageName: String,
   val objectName: String,
)

data class AndroidResourcesConfig(
   val resourceDirectories: List<String>,
   val keyOverrides: Map<String, String>,
)

internal fun resolveConfig(project: Project, extension: TranslationToolsExtension): ResolvedTranslationToolsConfig
{
   val configFile = resolveConfigFile(project, extension).get().asFile
   if (!configFile.exists())
      throw org.gradle.api.GradleException("TranslationTools config file not found: ${configFile.path}. Run ./gradlew.bat initTranslationTools first.")

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

internal fun resolveConfigFile(project: Project, extension: TranslationToolsExtension): Provider<RegularFile>
{
   val configuredPath = project.findProperty("translationtools.config") as String?
   return if (configuredPath != null) {
      project.provider {
         project.layout.projectDirectory.file(configuredPath)
      }
   }
   else {
      extension.configFile.orElse(project.layout.projectDirectory.file("translationtools.yaml"))
   }
}

private fun parseConfig(file: File): TranslationToolsConfig
{
   val settings = LoadSettings.builder().build()
   val loaded = Load(settings).loadFromInputStream(file.inputStream()) as? Map<*, *>
      ?: throw org.gradle.api.GradleException("Invalid TranslationTools config: ${file.path}")

   val apiKey = loaded["apiKey"] as? String
   val defaultLocale = loaded["defaultLocale"] as? String
   val locales = (loaded["locales"] as? List<*>)?.map { it.toString() } ?: emptyList()
   val snapshotFile = loaded["snapshotFile"] as? String ?: "translationtools/snapshot.json"
   val generated = loaded["generated"] as? Map<*, *>
      ?: throw org.gradle.api.GradleException("Missing generated config in ${file.path}")
   val packageName = generated["packageName"] as? String
      ?: throw org.gradle.api.GradleException("Missing generated.packageName in ${file.path}")
   val objectName = generated["objectName"] as? String ?: "Res"
   val androidResources = loaded["androidResources"] as? Map<*, *>
   val resourceDirectories = (androidResources?.get("resourceDirectories") as? List<*>)
      ?.map { it.toString() }
      ?: listOf("src/androidMain/res")
   val keyOverrides = (androidResources?.get("keyOverrides") as? Map<*, *>)
      ?.entries
      ?.associate { (key, value) -> key.toString() to value.toString() }
      ?: emptyMap()

   return TranslationToolsConfig(
      apiKey = apiKey,
      defaultLocale = defaultLocale,
      locales = locales,
      snapshotFile = snapshotFile,
      generated = GeneratedConfig(packageName = packageName, objectName = objectName),
      androidResources = AndroidResourcesConfig(
         resourceDirectories = resourceDirectories,
         keyOverrides = keyOverrides,
      ),
   )
}

internal fun renderDefaultConfig(): String
{
   return """
          apiKey: your-project-api-key
          defaultLocale: en
          locales:
            - en
          snapshotFile: translationtools/snapshot.json
          generated:
            packageName: com.example.translations
            objectName: Res
          androidResources:
            resourceDirectories:
              - src/androidMain/res
          """.trimIndent() + System.lineSeparator()
}
