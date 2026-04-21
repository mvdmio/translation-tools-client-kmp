package io.mvdm.translationtools.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

class TranslationToolsPlugin : Plugin<Project>
{
   override fun apply(project: Project)
   {
      val configFile = resolveConfigFile(project)
      val resolvedConfig = project.provider { resolveConfig(project) }

      project.tasks.register("initTranslationTools", InitTranslationToolsTask::class.java) { task ->
         task.group = "translationtools"
         task.description = "Creates a starter translationtools.yaml config file."
         task.configFile.set(configFile)
      }

      val generateTask = project.tasks.register("generateTranslationResources", GenerateTranslationResourcesTask::class.java) { task ->
          task.group = "translationtools"
          task.description = "Generates Kotlin translation resources from local Android XML resources."

          val outputDir = project.layout.buildDirectory.dir("generated/source/translationtools/commonMain/kotlin")
          val resourceDirectories = resolvedConfig.map { it.config.androidResources.resourceDirectories.map(project::file) }
          val resourceFiles = resourceDirectories.map { directories ->
             directories.flatMap { directory ->
                directory.walkTopDown()
                   .filter { file -> file.isFile && file.extension.equals("xml", ignoreCase = true) }
                   .toList()
             }
          }

          task.resourceFiles.from(resourceFiles)
          task.defaultLocale.set(resolvedConfig.map { it.config.defaultLocale ?: "en" })
          task.keyOverrides.set(resolvedConfig.map { it.config.androidResources.keyOverrides })
          task.projectPathInput.set(project.path)
          task.packageName.set(project.provider {
             resolvedConfig.get().config.generated?.packageName
                ?: inferDefaultGeneratedPackage(project)
          })
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

           task.apiKey.set(resolvedConfig.flatMap { resolved ->
              project.provider { resolved.config.apiKey ?: "" }
           })
           task.defaultLocale.set(resolvedConfig.map { it.config.defaultLocale ?: "en" })
           task.resourceDirectories.set(resolvedConfig.map { it.config.androidResources.resourceDirectories })
           task.keyOverrides.set(resolvedConfig.map { it.config.androidResources.keyOverrides })
           task.configuredLocales.set(resolvedConfig.map { it.config.locales })
             task.finalizedBy(generateTask)
          }

      project.tasks.register("pushTranslations", PushTranslationsTask::class.java) { task ->
         task.group = "translationtools"
         task.description = "Pushes local Android XML resources to TranslationTools."

         task.apiKey.set(resolvedConfig.flatMap { resolved ->
            project.provider { resolved.config.apiKey ?: "" }
         })
         task.defaultLocale.set(resolvedConfig.map { it.config.defaultLocale ?: "en" })
         task.resourceDirectories.set(resolvedConfig.map { it.config.androidResources.resourceDirectories })
         task.keyOverrides.set(resolvedConfig.map { it.config.androidResources.keyOverrides })
         task.prune.set(project.providers.gradleProperty("translationtools.prune").map(String::toBoolean).orElse(false))
      }

      project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
         val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
         kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(
            project.layout.buildDirectory.dir("generated/source/translationtools/commonMain/kotlin"),
         )
         project.tasks.withType(KotlinCompilationTask::class.java)
            .configureEach { task ->
               task.dependsOn(generateTask)
            }
         project.tasks.withType(AbstractArchiveTask::class.java)
            .matching { it.name.contains("SourcesJar", ignoreCase = true) }
            .configureEach { task ->
               task.dependsOn(generateTask)
            }
      }
   }
}

private fun inferDefaultGeneratedPackage(project: Project): String
{
   val namespace = project.findProperty("android.namespace") as String?
      ?: "${project.group}.translations"

   return "$namespace.translations"
}
