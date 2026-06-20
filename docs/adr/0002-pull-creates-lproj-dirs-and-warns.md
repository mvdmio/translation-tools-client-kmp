# `pull` creates missing `.lproj` files and warns about Xcode `knownRegions`

## Context

The Android `pull` creates new `values-<qualifier>/` directories for any locale present
remotely, and Android picks them up automatically. iOS is different: Xcode only compiles
localizations whose region is listed in the Xcode project's `knownRegions`, and the Gradle
plugin does not (and will not) edit `project.pbxproj`. So a freshly created `.lproj` file
for an unregistered locale is written to disk but silently not bundled by Xcode.

## Decision

On `pull`, for a remote locale with no existing local `.lproj` directory, the plugin
**creates** the `.lproj/<file>.strings` file anyway and **prints a warning** telling the
developer to add that region to the Xcode project's `knownRegions`. We considered
update-existing-only (skip + warn) and rejected it.

## Why

Creating the file gives the developer the content immediately and makes the missing step a
one-line manual fix (register the region in Xcode) rather than a manual file-creation
chore. The warning makes the required Xcode action explicit. The risk — a file that exists
but isn't bundled until `knownRegions` is updated — is accepted and surfaced via the
warning.
