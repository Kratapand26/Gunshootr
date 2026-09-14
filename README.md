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
- **Type:** Universal (works on any Android app)
- **What it does:**
  - On **Android 16 (API 36)** on large screens and tablets (`sw >= 600dp`), the OS WindowManager ignores in-app `Activity.setRequestedOrientation()` calls for standard applications.
  - This patch sets `android:appCategory="game"` in `<application>`, triggering Android's game exemption policy which allows in-app screen rotation buttons to freely change screen orientation.
  - Caps `targetSdkVersion` to 35 (Android 15) if the app targets API 36+, avoiding Android 16's strict tablet orientation lock.
  - Injects `PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_CONSTRAINTS` and `PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE`.
  - Ensures activities declare `configChanges` (`orientation|screenSize|smallestScreenSize...`) and `android:resizeableActivity="true"` to prevent activity restarts or letterboxing black bars upon rotation.

---

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
