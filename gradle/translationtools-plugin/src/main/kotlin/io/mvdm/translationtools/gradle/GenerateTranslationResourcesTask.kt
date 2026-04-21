package io.mvdm.translationtools.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateTranslationResourcesTask : DefaultTask()
{
   @get:InputFiles
   @get:PathSensitive(PathSensitivity.RELATIVE)
   abstract val resourceFiles: ConfigurableFileCollection

   @get:Input
   abstract val defaultLocale: Property<String>

   @get:Input
   abstract val keyOverrides: MapProperty<String, String>

   @get:Input
   abstract val projectPathInput: Property<String>

   @get:Input
   abstract val packageName: Property<String>

   @get:OutputFile
   abstract val outputFile: RegularFileProperty

   @get:OutputFile
   abstract val bundledSnapshotOutputFile: RegularFileProperty

   @TaskAction
   fun generate()
   {
      val files = resourceFiles.files.filter { it.exists() }
      if (files.isEmpty())
         throw GradleException("No Android XML string resources found. Add XML files under src/androidMain/res/values*/ or configure androidResources.resourceDirectories.")

      val parser = AndroidStringResourceParser()
      val resourceDirectories = files.mapNotNull(File::getParentFile).mapNotNull(File::getParentFile).distinct()
      val parsed = parser.parse(resourceDirectories, defaultLocale.get(), keyOverrides.getOrElse(emptyMap()), projectPathInput.get())

      parsed.warnings.forEach { logger.warn(it) }

      val rendered = renderTranslationResources(parsed, packageName.get())
      val output = outputFile.asFile.get()
      output.parentFile.mkdirs()
      if (!output.exists() || output.readText() != rendered)
         output.writeText(rendered)

      val bundledSnapshot = renderBundledSnapshot(parsed, packageName.get())
      val bundledSnapshotOutput = bundledSnapshotOutputFile.asFile.get()
      bundledSnapshotOutput.parentFile.mkdirs()
      if (!bundledSnapshotOutput.exists() || bundledSnapshotOutput.readText() != bundledSnapshot)
         bundledSnapshotOutput.writeText(bundledSnapshot)
   }
}
