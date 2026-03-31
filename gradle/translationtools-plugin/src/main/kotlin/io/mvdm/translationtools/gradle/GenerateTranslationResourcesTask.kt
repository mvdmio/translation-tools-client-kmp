package io.mvdm.translationtools.gradle

import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateTranslationResourcesTask : DefaultTask()
{
   @get:InputFile
   abstract val snapshotFile: RegularFileProperty

   @get:Input
   abstract val packageName: Property<String>

   @get:Input
   abstract val objectName: Property<String>

   @get:OutputFile
   abstract val outputFile: RegularFileProperty

   @get:OutputFile
   abstract val bundledSnapshotOutputFile: RegularFileProperty

   @TaskAction
   fun generate()
   {
      val snapshot = snapshotFile.asFile.get()
      if (!snapshot.exists())
         throw GradleException("Translation snapshot not found at ${snapshot.path}. Run ./gradlew.bat pullTranslations first.")

      val parsed = Json { ignoreUnknownKeys = false }
         .decodeFromString<TranslationSnapshotFile>(snapshot.readText())
      val rendered = renderTranslationResources(parsed, packageName.get(), objectName.get())
      val output = outputFile.asFile.get()
      output.parentFile.mkdirs()
      if (!output.exists() || output.readText() != rendered)
         output.writeText(rendered)

      val bundledSnapshot = renderBundledSnapshot(parsed, packageName.get(), objectName.get())
      val bundledSnapshotOutput = bundledSnapshotOutputFile.asFile.get()
      bundledSnapshotOutput.parentFile.mkdirs()
      if (!bundledSnapshotOutput.exists() || bundledSnapshotOutput.readText() != bundledSnapshot)
         bundledSnapshotOutput.writeText(bundledSnapshot)
   }
}
