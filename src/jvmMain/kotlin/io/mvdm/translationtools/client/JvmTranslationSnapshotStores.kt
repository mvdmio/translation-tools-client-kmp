package io.mvdm.translationtools.client

import java.io.File
import okio.FileSystem

public object JvmTranslationSnapshotStores
{
   public fun default(
      userHome: String = System.getProperty("user.home"),
      fileSystem: FileSystem = FileSystem.SYSTEM,
   ): TranslationSnapshotStore {
      val path = File(userHome, ".mvdmio/translationtools/translations.json").path
      return FileTranslationSnapshotStore(fileSystem, path)
   }
}
