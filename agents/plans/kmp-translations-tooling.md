# Kotlin Multiplatform TranslationTools tooling plan

## Goal

- Add Gradle/KMP-native tooling after the KMP client runtime is finished.
- Help Android/KMP teams migrate from existing `string.xml` resources.
- Add generated typed accessors similar in spirit to the .NET client.
- Keep this tooling separate from the existing .NET `translations` tool.

## Scope

- Gradle/KMP ecosystem only.
- No .NET CLI changes.
- Depends on completed KMP runtime client plan.

## Recommendation

- Build a dedicated Gradle plugin for config + tasks.
- Add source generation through Gradle tasks first.
- Consider KSP later only if annotation-driven generation becomes necessary.

## Main use-cases

### 1. Migrate existing Android resources

- Read `values/strings.xml`.
- Read localized variants like `values-nl/strings.xml`.
- Map Android string resource names to TranslationTools keys.
- Import strings into TranslationTools.

### 2. Pull TranslationTools data into project metadata

- Fetch project metadata and locale content from TranslationTools.
- Store a local manifest/metadata file for generation.
- Keep generated code based on TranslationTools state, not runtime Android resources.

### 3. Generate typed accessors

- Generate Kotlin source into `Res.string.*` typed resources.
- Generated resources are consumed through the KMP runtime client APIs.
- Safe after client `initialize()`.

## Proposed plugin shape

- artifact: `translationtools-gradle-plugin`
- current task names:
  - `importAndroidResources`
  - `pullTranslations`
  - `generateTranslationResources`
- config file: `translationtools.yaml`
- config sections:
  - API key
  - locales
  - snapshot file
  - generated package/object name
  - Android resource directories
  - Android resource key overrides

## Key decisions to settle

### 1. Source of truth after migration

- Recommended: TranslationTools becomes source of truth.
- `string.xml` used for migration/bootstrap only.

### 2. Key naming strategy

- Need deterministic mapping from Android names like `action_save`.
- Fixed default:
  - `action_save` -> `action.save`
- Support explicit overrides for collisions/legacy naming.

### 3. Generation input

- Recommended: generate from pulled TranslationTools manifest/project data.
- Avoid tying generation directly to `string.xml` after migration.

### 4. Generated API shape

- Fixed generated shape:

```kotlin
object Res {
    object string {
        val action_save = TranslationStringResource(
            key = "action.save",
            fallback = "Save",
        )
    }
}
```

- Keep generation aligned with existing `TranslationStringResource` runtime APIs.
- Fallback order matches KMP runtime policy.

## Import behavior

- Parse default locale + localized resource files.
- Preserve string value text.
- Current API constraint: project push only accepts default-locale values.
- Decide handling for:
  - formatted strings
  - escaped characters
  - plurals
  - string arrays
  - translatable=`false`
- Recommended v1:
- support normal strings first
- skip plurals and arrays until explicitly designed
- skip `translatable="false"` entries
- ignore non-locale qualifiers like `values-night`
- parse localized files now; upload them once the API supports locale writes

## Generation behavior

- Generate Kotlin into build/generated sources.
- Emit stable file names.
- Regenerate deterministically.
- Generate keys/constants plus simple typed accessors.
- Optionally generate default values from default locale.

## Non-goals for first tooling version

- No integration into .NET CLI.
- No runtime parsing of `string.xml`.
- No automatic bidirectional sync.
- No Android resource XML regeneration from TranslationTools.
- No plurals/arrays support unless explicitly added.

## Tests

### Plugin/task tests

- config binding
- task wiring
- generated source registered in Gradle source sets

### Import tests

- parse `values/strings.xml`
- parse localized directories
- key mapping rules
- import payload correctness

### Generation tests

- deterministic generated source
- keys/constants output
- accessor output
- default value fallback wiring

## Docs

- plugin README
- setup instructions for Android/KMP projects
- migration guide from `string.xml`
- generated accessors usage guide
- limitations section for plurals/arrays

## Phases

## Phase 0 - design

### Work

- key mapping fixed to underscore-to-dot by default
- snapshot file remains the generation input after import
- generated API shape fixed to `Res.string.*`

### Acceptance

- import and generation behavior documented and aligned with shipped plugin tasks

## Phase 1 - Gradle plugin skeleton

### Work

- create plugin project
- add extension/config model
- add placeholder tasks

### Acceptance

- plugin applies cleanly in a sample KMP project

## Phase 2 - Android resource import

### Work

- parse `string.xml` files
- map resource names to TranslationTools keys
- add import execution

### Acceptance

- existing Android resource sets can be imported into TranslationTools

## Phase 3 - manifest pull + Kotlin generation

### Work

- pull TranslationTools metadata/content
- write local manifest/cache input
- generate typed Kotlin source

### Acceptance

- KMP project can use generated typed accessors backed by the KMP runtime client

## Phase 4 - docs + publishing

### Work

- write README and migration docs
- add CI and publishing
- add sample project

### Acceptance

- plugin installable and documented end-to-end

## Done when

- Android/KMP teams can migrate existing `string.xml` content into TranslationTools.
- KMP projects can generate typed localization accessors.
- tooling stays fully separate from the .NET `translations` tool.
