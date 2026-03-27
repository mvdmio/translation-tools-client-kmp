package io.mvdm.translationtools.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class TranslationToolsPlugin : Plugin<Project>
{
   override fun apply(project: Project)
   {
      val extension = project.extensions.create("translationTools", TranslationToolsExtension::class.java)

      val generateTask = project.tasks.register("generateTranslationResources", GenerateTranslationResourcesTask::class.java) { task ->
         task.group = "translationtools"
         task.description = "Generates Kotlin translation resources from the local snapshot."

         val resolved = resolveConfig(project, extension)
         val snapshot = project.layout.projectDirectory.file(resolved.config.snapshotFile)
         val outputDir = project.layout.buildDirectory.dir("generated/source/translationtools/commonMain/kotlin")
         val packagePath = resolved.config.generated.packageName.replace('.', '/')

          task.snapshotFile.set(snapshot)
          task.packageName.set(resolved.config.generated.packageName)
          task.objectName.set(resolved.config.generated.objectName)
          task.outputFile.set(outputDir.map { it.file("$packagePath/${resolved.config.generated.objectName}.kt") })
          task.outputs.upToDateWhen {
             val output = task.outputFile.asFile.get()
             output.exists() && snapshot.asFile.exists()
          }
      }

      project.tasks.register("pullTranslations", PullTranslationsTask::class.java) { task ->
         task.group = "translationtools"
         task.description = "Pulls translations and regenerates Kotlin resources."

         val resolved = resolveConfig(project, extension)
         task.apiKey.set(resolved.config.apiKey)
         task.configuredLocales.set(resolved.config.locales)
         task.snapshotFile.set(project.layout.projectDirectory.file(resolved.config.snapshotFile))
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
