package io.mvdm.translationtools.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

class TranslationToolsPlugin : Plugin<Project>
{
   override fun apply(project: Project)
   {
      val configFile = resolveConfigFile(project)
      val parsedConfig = readTranslationToolsConfig(project)
      val projectDirectory = project.layout.projectDirectory
      val apiKey = resolvedApiKey(project, parsedConfig, configFile.asFile.path)
      // TestKit seam for local HTTP. Not a documented consumer setting.
      val baseUrl = project.providers.gradleProperty("translationtools.baseUrl").orElse(BASE_URL)
      val generatedCodegenEnabled = parsedConfig?.generated?.enabled ?: true

      project.tasks.register("initTranslationTools", InitTranslationToolsTask::class.java) { task ->
         task.group = "translationtools"
         task.description = "Creates a starter translationtools.yaml config file."
         task.configFile.set(configFile)
      }

      val generateTask = project.tasks.register("generateTranslationResources", GenerateTranslationResourcesTask::class.java) { task ->
          task.group = "translationtools"
          task.description = "Generates Kotlin translation resources from local Android XML resources."

          val outputDir = project.layout.buildDirectory.dir("generated/source/translationtools/commonMain/kotlin")
          val defaultPackageName = inferDefaultGeneratedPackage(project)

          if (parsedConfig != null)
          {
             task.resourceFiles.from(
                parsedConfig.androidResources.resourceDirectories.map { path ->
                   projectDirectory.dir(path).asFileTree.matching { spec -> spec.include("**/*.xml") }
                },
             )
             task.defaultLocale.set(parsedConfig.defaultLocale ?: "en")
             task.keyOverrides.set(parsedConfig.androidResources.keyOverrides)
             task.packageName.set(parsedConfig.generated?.packageName ?: defaultPackageName)
          }
          else
          {
             task.defaultLocale.set(missingConfigProvider(project, configFile.asFile.path))
             task.packageName.set(missingConfigProvider(project, configFile.asFile.path))
          }
          task.projectPathInput.set(project.path)
          task.outputFile.set(
             outputDir.zip(task.packageName) { dir, packageName ->
                dir.file("${packageName.replace('.', '/')}/$GENERATED_OBJECT_NAME.kt")
             }
          )
          task.bundledSnapshotOutputFile.set(
             outputDir.zip(task.packageName) { dir, packageName ->
                dir.file("${packageName.replace('.', '/')}/${GENERATED_OBJECT_NAME}BundledSnapshot.kt")
             }
          )
         }

      project.tasks.register("pullTranslations", PullTranslationsTask::class.java) { task ->
          task.group = "translationtools"
          task.description = "Pulls translations into local Android XML resources and regenerates Kotlin resources."

           task.apiKey.set(apiKey)
           task.defaultLocale.set(parsedConfig?.defaultLocale ?: "en")
           if (parsedConfig != null)
              applyResourceDirectoryInputs(projectDirectory, parsedConfig, task.resourceDirectories, task.appleResourceDirectories)
           task.keyOverrides.set(parsedConfig?.androidResources?.keyOverrides ?: emptyMap())
           task.configuredLocales.set(parsedConfig?.locales ?: emptyList())
           task.projectPathInput.set(project.path)
           task.baseUrl.set(baseUrl)
           if (generatedCodegenEnabled)
              task.finalizedBy(generateTask)
          }

      project.tasks.register("pushTranslations", PushTranslationsTask::class.java) { task ->
         task.group = "translationtools"
         task.description = "Pushes local Android XML resources to TranslationTools."

         task.apiKey.set(apiKey)
         task.defaultLocale.set(parsedConfig?.defaultLocale ?: "en")
         if (parsedConfig != null)
            applyResourceDirectoryInputs(projectDirectory, parsedConfig, task.resourceDirectories, task.appleResourceDirectories)
         task.keyOverrides.set(parsedConfig?.androidResources?.keyOverrides ?: emptyMap())
         task.prune.set(
            project.providers.gradleProperty("translationtools.prune")
               .map(String::toBoolean)
               .orElse(parsedConfig?.androidResources?.prune ?: false)
         )
         task.projectPathInput.set(project.path)
         task.baseUrl.set(baseUrl)
      }

      project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
         if (!generatedCodegenEnabled)
            return@withId
         KotlinMultiplatformCodegenWiring.configure(project, generateTask)
      }
   }
}

internal fun applyResourceDirectoryInputs(
   projectDirectory: Directory,
   config: TranslationToolsConfig,
   resourceDirectories: ConfigurableFileCollection,
   appleResourceDirectories: ConfigurableFileCollection,
)
{
   resourceDirectories.from(config.androidResources.resourceDirectories.map(projectDirectory::dir))
   appleResourceDirectories.from(
      (config.appleResources?.resourceDirectories ?: emptyList()).map(projectDirectory::dir),
   )
}

private fun resolvedApiKey(project: Project, parsedConfig: TranslationToolsConfig?, configPath: String): Provider<String>
{
   val fromPropertyOrEnv = project.providers.gradleProperty("translationtools.apiKey")
      .orElse(project.providers.environmentVariable("TRANSLATIONTOOLS_API_KEY"))
   val yamlApiKey = parsedConfig?.apiKey
   return when
   {
      yamlApiKey != null -> fromPropertyOrEnv.orElse(yamlApiKey)
      parsedConfig != null -> fromPropertyOrEnv.orElse("")
      else -> fromPropertyOrEnv.orElse(missingConfigProvider(project, configPath))
   }
}

private fun missingConfigProvider(project: Project, configPath: String): Provider<String> =
   project.provider {
      throw GradleException("TranslationTools config file not found: $configPath. Run ./gradlew.bat initTranslationTools first.")
   }

private fun inferDefaultGeneratedPackage(project: Project): String
{
   val namespace = project.findProperty("android.namespace") as String?
      ?: "${project.group}.translations"

   return "$namespace.translations"
}
