package io.mvdm.translationtools.client

import android.content.Context
import okio.FileSystem

public object AndroidTranslationSnapshotStores
{
   public fun fromContext(
      context: Context,
      fileSystem: FileSystem = FileSystem.SYSTEM,
   ): TranslationSnapshotStore {
      return fromDirectory(context.filesDir.absolutePath, fileSystem)
   }

   public fun fromDirectory(
      filesDirectoryPath: String,
      fileSystem: FileSystem = FileSystem.SYSTEM,
   ): TranslationSnapshotStore {
      return FileTranslationSnapshotStore(fileSystem, "$filesDirectoryPath/translationtools/translations.json")
   }
}
