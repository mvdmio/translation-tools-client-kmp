package io.mvdm.translationtools.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

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
         task.description = "Generates Kotlin translation resources from the local snapshot."

           val snapshot = resolveSnapshotFile(project)
          val outputDir = project.layout.buildDirectory.dir("generated/source/translationtools/commonMain/kotlin")

          task.snapshotFile.set(snapshot)
         task.packageName.set(resolvedConfig.map { it.config.generated.packageName })
         task.objectName.set(resolvedConfig.map { it.config.generated.objectName })
         task.outputFile.set(
            outputDir.zip(resolvedConfig) { dir, resolved ->
               dir.file("${resolved.config.generated.packageName.replace('.', '/')}/${resolved.config.generated.objectName}.kt")
            }
         )
         task.bundledSnapshotOutputFile.set(
            outputDir.zip(resolvedConfig) { dir, resolved ->
               dir.file("${resolved.config.generated.packageName.replace('.', '/')}/${resolved.config.generated.objectName}BundledSnapshot.kt")
            }
         )
          task.outputs.upToDateWhen {
             val output = task.outputFile.asFile.get()
             output.exists() && task.bundledSnapshotOutputFile.asFile.get().exists() && task.snapshotFile.asFile.get().exists()
          }
        }

      project.tasks.register("pullTranslations", PullTranslationsTask::class.java) { task ->
         task.group = "translationtools"
         task.description = "Pulls translations and regenerates Kotlin resources."

          task.apiKey.set(resolvedConfig.flatMap { resolved ->
             project.provider { resolved.config.apiKey ?: "" }
          })
           task.configuredLocales.set(resolvedConfig.map { it.config.locales })
           task.snapshotFile.set(resolveSnapshotFile(project))
           task.finalizedBy(generateTask)
        }

      project.tasks.register("migrateTranslations", MigrateTranslationsTask::class.java) { task ->
         task.group = "translationtools"
         task.description = "Imports Android string.xml resources into TranslationTools, refreshes snapshot, and regenerates Kotlin resources."

         task.apiKey.set(resolvedConfig.flatMap { resolved ->
            project.provider { resolved.config.apiKey ?: "" }
         })
         task.defaultLocale.set(resolvedConfig.flatMap { resolved ->
            project.provider { resolved.config.defaultLocale ?: "" }
         })
           task.resourceDirectories.set(resolvedConfig.map { it.config.androidResources.resourceDirectories })
           task.configuredLocales.set(resolvedConfig.map { it.config.locales })
           task.keyOverrides.set(resolvedConfig.map { it.config.androidResources.keyOverrides })
           task.snapshotFile.set(resolveSnapshotFile(project))
           task.finalizedBy(generateTask)
        }

      project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
         val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
         kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(
            project.layout.buildDirectory.dir("generated/source/translationtools/commonMain/kotlin"),
         )
         project.tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
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
