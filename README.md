# Gunshootr Patches

A curated collection of universal patches for the [Morphe](https://github.com/MorpheApp) patcher, designed to restore in-app features and bypass OS restrictions on Android devices and tablets.

---

## 📲 Add as Source in Morphe Manager

Add Gunshootr Patches directly inside Morphe Manager:

| Method | Link |
| :--- | :--- |
| **Deep Link** | [morphe.software/add-source?github=Kratapand26/Gunshootr](https://morphe.software/add-source?github=Kratapand26/Gunshootr) |
| **Manual Source** | `https://github.com/Kratapand26/Gunshootr` |

> [!TIP]
> Tap the deep link on an Android device with Morphe Manager installed to add the source in one tap.

---

## 🧩 Available Patches

### 🖥️ Manifest & Display

#### **Fix Android 16 Tablet Rotation**
- **Category:** Manifest
- **Type:** Universal
- **What it does:**
  - On **Android 16 (API 36)**, apps targeting API 36+ can have orientation requests ignored on displays with a smallest width of at least 600dp. [Android documents an exception for games](https://developer.android.com/about/versions/16/behavior-changes-16#exceptions).
  - Retains the existing `android:appCategory="game"` exemption so apps with in-app rotation controls can request orientation again.
  - Preserves the app's `screenOrientation`, `configChanges`, resizability, screen support, and activity aliases. Adding configuration flags does not teach an app how to update its layouts; Android's normal activity recreation must remain available to apps that depend on it.
  - Uses Morphe's raw resource mode to edit the binary manifest when selected alone, preserving the resource table, resource names, layouts, and strings byte for byte. If another selected patch requires full resource decoding, it edits that patcher's shared XML manifest instead; the combined run can still rebuild resources.
  - Preserves `targetSdkVersion` by default. Inspection of the supplied original and working patched **Moon+ Reader Pro 10.7** APKs confirms both target API 36; the working copy adds the game category without an SDK downgrade. Its DEX files are unchanged.
  - **Cap target SDK to Android 15 (API 35)** is an optional fallback, disabled by default. Lowering the target SDK can affect Android behavior beyond rotation. SDK declarations that cannot be resolved safely, or require a minimum SDK above 35, are preserved with a diagnostic.
  - Does not inject experimental compatibility properties. A successful patch reports the actual manifest changes; it does not guarantee that an app's layouts or rendering work in every orientation or windowing mode.

For Moon+ Reader Pro 10.7 on an Android 16 tablet, keep the SDK fallback **disabled** and patch an **original, unpatched APK**. Applying this version to an already patched APK cannot recover the original lifecycle or screen settings, because those declarations may have been overwritten by the older patch. Check the in-app rotation button, reading position, portrait/landscape layouts, and background/resume on your device. Manifest inspection and regression tests do not replace this device check.

#### **Clearing Split Metadata**
- **Category:** Manifest
- **Type:** Universal
- **What it does:**
  - Removes split-install manifest attributes (`isSplitRequired`, `requiredSplitTypes`, `splitTypes`) and Play Store split metadata (`com.android.vending.splits`).
  - Prevents "corrupted package" / "There was a problem parsing the package" install errors when patching base APKs extracted from installed apps.

### 🛡️ Spoof & Integrity

#### **Spoof Signature Match**
- **Category:** Spoof
- **Type:** Universal (Bytecode)
- **What it does:**
  - Intercepts `PackageManager.checkSignatures()` calls and forces them to return `SIGNATURE_MATCH` (`0x0`).
  - Bypasses built-in app integrity and anti-tamper verification checks (such as those in Moon+ Reader Pro and other closed-source apps) that display "App corrupted" or "License invalid" when re-signed by Morphe.

## 🛠️ Building From Source

```bash
# Clone the repository
git clone https://github.com/Kratapand26/Gunshootr.git
cd Gunshootr

# Build the patch bundle (.mpp)
./gradlew build

# Generate patches-list.json
./gradlew generatePatchesList
```

The output bundle will be generated under `patches/build/libs/patches-<version>.mpp`.

The build runs rotation manifest regression tests. To run those checks separately, use `./gradlew :patches:test`.
