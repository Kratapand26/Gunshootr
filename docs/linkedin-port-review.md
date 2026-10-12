# LinkedIn port review

Reviewed on 12 October 2026. Source: **Michii Patches 1.1.0**, by **heyymichii**,
commit `dbe97ce221d4e83c7a4f013850c4a435029ab9b8`. The local checkout and the
`v1.1.0` tag both resolve to that exact commit.

## Scope and findings

The review covers changes from the previously reviewed Michii 1.0.0 source:
diagnostics, language selection, translation generation, dialog styling, launcher
shortcuts, and compatibility metadata. The source has 26 upstream Java classes
and eleven language text tables. Only reviewed source and text were imported;
upstream workflows, dependency catalogs, executables, wrapper binaries, and
prebuilt extensions were not imported.

No evidence of malware was found in the reviewed changes. They add no Android
permissions, native libraries, downloaded code loaders, shell execution,
credential collection, external analytics SDKs, or custom TLS trust bypasses.
Existing private preferences are retained so settings survive an update.
Reflection reads LinkedIn UI/model fields and obtains the running app context;
it does not load downloaded code.

The existing HTTPS host checks, validation of every background redirect, response
size limits, media URL and filename validation, and thumbnail bounds are
preserved. NetworkSafety, ShortLinkResolver, and UpdateChecker are unchanged from
the previous reviewed port. Normal platform certificate verification is retained.

## Diagnostics adaptations

Upstream stores raw log entries and redacts the exported report later. Gunshootr
redacts URLs, email addresses, LinkedIn identifiers, long numbers, and recognizable
credential fields before writing diagnostic entries to Android logs or private
storage. Original exceptions are not passed unredacted to the Android logger.
Repeated messages are suppressed with bounded bookkeeping. Backend failures are
contained so logging cannot interrupt a LinkedIn hook.

Message text is truncated at 4,096 characters, followed by a truncation marker.
The log is capped at 300 KiB and reports at 200 log lines. Large or damaged files
are read and repaired with bounded tail allocations. Reports include recognized
patch settings, rather than dumping every preference; personal download-folder
names and unknown imported keys are omitted.

Reports include app, Android, device, patch, and language details. They are copied
to the clipboard only when **Copy diagnostic report** is tapped. Nothing is
uploaded automatically. Reports still ask users to review their contents before
posting; pattern-based redaction cannot identify every possible personal detail.

## Preserved behavior and adaptations

The eleven feature definitions, their defaults, and separate fingerprint files
are unchanged in upstream 1.1.0. The new main-activity shortcut fingerprint and
hook match that release. Gunshootr's shortcut identifier is retained; the shortcut
now publishes at rank zero from the main screen, with a delayed refresh after
LinkedIn registers its shortcuts. Dialog button tinting follows upstream's fix.

Compatibility and ordering match Michii 1.1.0 exactly: **4.1.1255.1**, **4.1.1259**,
and the experimental target. Upstream replaced **4.1.1258** with **4.1.1259**.
Gunshootr declares no additional app versions.

English is the default for settings, prompts, and messages on every device
language. The upstream language chooser and optional translations are retained;
automatic device language must be explicitly selected. Invalid imported language
codes and non-text values from old backups fall back to English. Reviewed text compiles into Java lookup
tables, with matching format placeholders required. Indonesian server labels used
by feed matching remain unchanged because they identify LinkedIn content.

Gunshootr brands the settings screen and launcher shortcut and checks updates from
`Kratapand26/Gunshootr`. About credits the original author and shows upstream
version **1.1.0** separately from Gunshootr's bundle version. The private,
idempotent settings activity registration preserves existing activities,
permissions, and SDK declarations.

Background requests remain limited to:

- `lnkd.in` and LinkedIn warning pages when short-link resolution is enabled.
- HTTPS `licdn.com` media hosts for requested media and chooser previews.
- Gunshootr's exact latest-release metadata endpoint on `api.github.com` when About is opened.

Credits and upstream support links open only after a user taps them. The extension
has no automatic APK installation, patch download, or executable update.

## Validation and limits

**63 local tests passed:** 27 rotation tests, four LinkedIn metadata/manifest
tests, 16 existing extension safety tests, ten diagnostics tests, and six
language compatibility tests. New coverage checks redaction before storage,
credential masking, log rotation, damaged large logs, report limits, preference
filtering, English defaults, regional fallback, and compiled translations.

The exact translation generator compiled successfully in an isolated offline
Gradle build. The extension compiled against Android API 34 and was converted to
DEX; patch sources compiled against the existing Morphe Patcher 1.13.0 API. No
patcher dependency change was needed. The MPP passed source metadata and Android
DEX verification, including checks for unexpected extension namespaces.

The release workflow runs the full Gradle build and release-variant tests before
publishing, retains their reports, and audits the actual MPP. Its timestamp check
preserves the Morphe 1.34 custom-source import fix. The Java-only extension still
excludes AGP's unused Kotlin runtime from its release dependencies.

No LinkedIn APK was supplied, so actual fingerprint matching, installation,
sign-in, feed rendering, messaging, downloads, and launcher behavior have not been
tested in LinkedIn. Windows Defender reported its service and antivirus disabled,
so no antivirus scan is claimed. The assessment relies on source inspection,
compilation, tests, and the compiled-bundle audit; it cannot certify every behavior
of a patched APK or of the original app.
