package io.mvdm.translationtools.gradle

import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.File

data class TranslationToolsConfig(
   val apiKey: String?,
   val defaultLocale: String?,
   val locales: List<String>,
   val generated: GeneratedConfig?,
   val androidResources: AndroidResourcesConfig,
)

data class GeneratedConfig(
   val packageName: String?,
   val objectName: String,
)

data class AndroidResourcesConfig(
   val resourceDirectories: List<String>,
   val keyOverrides: Map<String, String>,
)

internal fun resolveConfig(project: Project): ResolvedTranslationToolsConfig
{
   val configFile = resolveConfigFile(project).asFile
   if (!configFile.exists())
      throw org.gradle.api.GradleException("TranslationTools config file not found: ${configFile.path}. Run ./gradlew.bat initTranslationTools first.")

   if (configFile.parentFile.canonicalFile != project.projectDir.canonicalFile)
      throw org.gradle.api.GradleException("translationtools.yaml must be placed in the project root: ${project.projectDir.path}")

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

internal fun resolveConfigFile(project: Project): RegularFile
{
   return project.layout.projectDirectory.file(DEFAULT_CONFIG_FILE)
}

private fun parseConfig(file: File): TranslationToolsConfig
{
   val settings = LoadSettings.builder().build()
   val loaded = Load(settings).loadFromInputStream(file.inputStream()) as? Map<*, *>
      ?: throw org.gradle.api.GradleException("Invalid TranslationTools config: ${file.path}")

   val apiKey = loaded["apiKey"] as? String
   val defaultLocale = loaded["defaultLocale"] as? String
   val locales = (loaded["locales"] as? List<*>)?.map { it.toString() } ?: emptyList()
   if (loaded.containsKey("snapshotFile"))
      throw org.gradle.api.GradleException("snapshotFile is no longer supported. Use the default project-root snapshot.json path.")

   val generated = loaded["generated"] as? Map<*, *>
   val packageName = generated?.get("packageName") as? String
   val objectName = generated?.get("objectName") as? String ?: "Res"
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
            generated:
              packageName: com.example.translations
              objectName: Res
           androidResources:
             resourceDirectories:
              - src/androidMain/res
          """.trimIndent() + System.lineSeparator()
}
