package io.mvdm.translationtools.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class TranslationToolsExtension @Inject constructor(
   objects: ObjectFactory,
)
{
   val configFile: RegularFileProperty = objects.fileProperty()
}
