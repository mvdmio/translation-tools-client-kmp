package io.mvdm.translationtools.client

import okio.FileSystem

public object TranslationSnapshotStores
{
   public fun file(
      filePath: String,
      fileSystem: FileSystem,
   ): TranslationSnapshotStore {
      return FileTranslationSnapshotStore(fileSystem, filePath)
   }
}
