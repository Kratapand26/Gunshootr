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
  - On **Android 16 (API 36)** on large screens and tablets (`sw >= 600dp`), the OS WindowManager ignores in-app `Activity.setRequestedOrientation()` calls for standard applications.
  - Sets `android:appCategory="game"` in `<application>`, triggering Android's game exemption policy which allows in-app screen rotation buttons to freely change screen orientation.
  - Ensures `<supports-screens>` declares support for large and xlarge screens on tablets and foldables.
  - Caps `targetSdkVersion` to 35 (Android 15) if the app targets API 36+, avoiding Android 16's strict tablet orientation lock.
  - Ensures activities and activity-aliases declare `configChanges` (`orientation|screenSize|smallestScreenSize...`) and `android:resizeableActivity="true"` to prevent activity restarts or letterboxing black bars upon rotation.

#### **Clearing Split Metadata**
- **Category:** Manifest
- **Type:** Universal
- **What it does:**
  - Removes split-install manifest attributes (`isSplitRequired`, `requiredSplitTypes`, `splitTypes`) and Play Store split metadata (`com.android.vending.splits`).
  - Prevents "corrupted package" / "There was a problem parsing the package" install errors when patching base APKs extracted from installed apps.

#### **Unlock Rotation**
- **Category:** Manifest
- **Type:** Universal
- **What it does:**
  - Strips hardcoded `android:screenOrientation` locks from all `<activity>` and `<activity-alias>` tags, allowing apps to rotate freely with the device orientation.

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
