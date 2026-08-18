package io.mvdm.translationtools.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

// Separate class so TranslationToolsPlugin loads without Kotlin Gradle plugin on the classpath.
internal object KotlinMultiplatformCodegenWiring
{
   fun configure(project: Project, generateTask: TaskProvider<*>)
   {
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
