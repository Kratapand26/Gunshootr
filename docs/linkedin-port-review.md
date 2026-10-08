# LinkedIn port review

Reviewed on 8 October 2026. Source: **Michii Patches 1.0.0**, by **heyymichii**,
commit `516e40f95aed928e225e21a34599d7936a6d6c59`.

## Scope and findings

The review covered all LinkedIn Kotlin patch definitions and all 24 upstream Java
extension classes, including network requests, settings backup, clipboard and share
hooks, reflective access, feed scanning, settings navigation, and media downloads.
Only these source files and their minimal extension configuration were imported.
Upstream workflow files, dependency catalogs, executables, wrapper binaries, and
prebuilt extensions were not imported.

No evidence of malware was found in the reviewed source. There are no added Android
permissions, native libraries, downloaded code loaders, shell execution, credential
collection, external analytics SDKs, or custom TLS trust bypasses in the imported
extension. Settings use the patch's own private preferences. Clipboard access is
used for cleaning copied links and explicitly requested settings backup/import.
Reflection reads LinkedIn UI/model fields and obtains the running app context;
it does not load downloaded code.

The upstream code allowed automatic background redirects to arbitrary hosts and
read update responses without a size limit. The port validates every background
redirect, requires HTTPS and expected service hosts, bounds response sizes, and
limits decoded thumbnail dimensions. Media download URLs and filenames are also
validated. Normal platform certificate verification is retained.

## Preserved behavior and adaptations

All eleven features, bytecode fingerprints, compatibility versions **4.1.1255.1**
and **4.1.1258**, and the experimental target are preserved. The original feature
defaults are retained. Indonesian server labels used by the feed matching logic
are intentionally preserved alongside English labels; translating those internal
match strings would change filter behavior. All visible interface text is English.

Gunshootr names its settings screen and launcher shortcut and checks updates from
`Kratapand26/Gunshootr`. Upstream version **1.0.0** remains visible in About, and
the original author is credited. Gunshootr uses its own bundle version for source
updates while retaining the upstream LinkedIn app version declarations. Settings
activity registration is private and idempotent and does not change the app's SDK
declarations, existing activities, or permissions.

Background requests are limited to these services:

- `lnkd.in` and LinkedIn warning pages when short-link resolution is enabled.
- HTTPS `licdn.com` media hosts for user-requested media and chooser previews.
- The exact Gunshootr latest-release metadata endpoint on `api.github.com` when About is opened.

There is no automatic APK installation, patch download, or executable update.

## Validation and limits

The Android extension compiled against API 34 and was converted to Android DEX.
All patch sources compiled against Gunshootr's existing Morphe Patcher 1.13.0 API;
no patcher or LinkedIn compatibility version was changed. The MPP includes both
the patch DEX and the rebuilt LinkedIn extension DEX.

**47 tests passed:** 27 existing rotation tests, four LinkedIn metadata/manifest
tests, and 16 extension safety tests. Coverage includes spoofed hosts, TLS
downgrades, redirect destination validation, oversized responses, executable link schemes,
clipboard link preservation, download folder traversal, malformed payloads,
original language matching, and original compatibility metadata.

The full Gradle build and release tests passed on GitHub Actions. Release-variant
unit tests are explicitly enabled through the typed AGP 9.1 variant API. Local
compilation, Android DEX generation, and JUnit tests used the previously verified
official Morphe runtime and installed development tools; the pinned plugin remains
unavailable through the local package-registry credentials.

The final release audit found that AGP automatically included an unused Kotlin
runtime in the Java-only extension. Release runtime dependencies exclude that
library so it is not injected into LinkedIn. Both build and release workflows
verify the actual MPP with the Android SDK's DEX parser and reject extension
classes outside `app.linkedin.extension` before publishing or uploading the bundle.

No LinkedIn APK was supplied, so real fingerprint matching, installation, sign-in,
feed rendering, messaging, and media downloads have not been tested in the app.
The built-in antivirus scanner was unavailable during validation; the malware
assessment is based on source inspection and the compiled-bundle audit.
This source review and these tests cannot certify every behavior of a patched
LinkedIn APK or of the original app.
