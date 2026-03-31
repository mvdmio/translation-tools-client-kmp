package io.mvdm.translationtools.gradle

import org.gradle.api.Project
import org.gradle.api.file.RegularFile

internal const val DEFAULT_CONFIG_FILE = "translationtools.yaml"
internal const val DEFAULT_SNAPSHOT_FILE = "snapshot.json"

internal fun resolveSnapshotFile(project: Project): RegularFile = project.layout.projectDirectory.file(DEFAULT_SNAPSHOT_FILE)
