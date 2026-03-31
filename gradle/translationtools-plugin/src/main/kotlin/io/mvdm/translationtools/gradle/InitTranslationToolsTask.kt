package io.mvdm.translationtools.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class InitTranslationToolsTask : DefaultTask()
{
   @get:OutputFile
   abstract val configFile: RegularFileProperty

   @TaskAction
   fun initConfig()
   {
      val output = configFile.asFile.get()
      if (output.exists())
         throw GradleException("TranslationTools config already exists: ${output.path}")

      output.parentFile.mkdirs()
      output.writeText(renderDefaultConfig())
      logger.lifecycle("Created ${project.relativePath(output)}")
   }
}
