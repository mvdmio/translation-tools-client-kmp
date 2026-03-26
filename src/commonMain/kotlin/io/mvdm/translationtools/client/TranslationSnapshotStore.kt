package io.mvdm.translationtools.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath

public interface TranslationSnapshotStore
{
   public suspend fun load(): StoredTranslations?

   public suspend fun save(translations: StoredTranslations)

   public suspend fun clear()
}

public object NoOpTranslationSnapshotStore : TranslationSnapshotStore
{
   override suspend fun load(): StoredTranslations?
   {
      return null
   }

   override suspend fun save(translations: StoredTranslations)
   {
   }

   override suspend fun clear()
   {
   }
}

public class FileTranslationSnapshotStore(
   private val fileSystem: FileSystem,
   filePath: String,
   private val json: Json = defaultSnapshotStoreJson,
) : TranslationSnapshotStore
{
   private val snapshotPath: Path = filePath.toPath(normalize = true)

   override suspend fun load(): StoredTranslations?
   {
      if (!fileSystem.exists(snapshotPath))
         return null

      return try {
         fileSystem.read(snapshotPath) {
            json.decodeFromString<StoredTranslations>(readUtf8())
         }
      }
      catch (_: Exception) {
         clear()
         null
      }
   }

   override suspend fun save(translations: StoredTranslations)
   {
      snapshotPath.parent?.let { parent ->
         fileSystem.createDirectories(parent)
      }

      fileSystem.write(snapshotPath) {
         writeUtf8(json.encodeToString(translations))
      }
   }

   override suspend fun clear()
   {
      try {
         fileSystem.delete(snapshotPath, mustExist = false)
      }
      catch (_: IOException) {
      }
   }
}

private val defaultSnapshotStoreJson = Json {
   ignoreUnknownKeys = true
   encodeDefaults = true
}
