package patches.universal.manifest

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.rawResourcePatch
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.util.logging.Logger

@Suppress("unused")
val fixAndroid16TabletRotationPatch = rawResourcePatch(
    name = "Fix Android 16 Tablet Rotation",
    description = "Restores in-app rotation on Android 16 tablets using the game exemption while preserving the app's activity configuration and screen support.",
    default = false,
) {
    category("Manifest")

    val capTargetSdkTo35 by booleanOption(
        key = "capTargetSdkTo35",
        title = "Cap target SDK to Android 15 (API 35)",
        default = false,
        description = "Optional fallback for apps that still block rotation. Moon+ Reader Pro 10.7's working patched APK retains API 36, so leave this disabled for that app. Lowering the target SDK can change other Android behavior.",
    )

    execute {
        val logger = Logger.getLogger(this::class.java.name)
        val binaryManifestFile = File(get("AndroidManifest.xml").parentFile, "AndroidManifest.xml.bin")
        val result = if (binaryManifestFile.isFile) {
            // Raw mode keeps resources.arsc and every resource file untouched.
            val manifest = binaryManifestFile.inputStream().use(AndroidManifestBlock::load)
            applyTabletRotationCompatibility(manifest, capTargetSdkTo35 == true).also {
                if (it.changes.isNotEmpty()) {
                    manifest.refresh()
                    val patchedManifest = manifest.bytes
                    binaryManifestFile.writeBytes(patchedManifest)
                    // Patcher 1.13 routes the decoded .bin outside its returned output folder.
                    // Also register an APK root override so the rebuilt APK includes this edit.
                    get(".").resolve("AndroidManifest.xml").writeBytes(patchedManifest)
                }
            }
        } else {
            // A selected full resource patch can promote this run out of raw mode.
            document("AndroidManifest.xml").use { manifest ->
                applyTabletRotationCompatibility(manifest, capTargetSdkTo35 == true)
            }
        }

        result.warnings.forEach { logger.warning(it) }
        if (result.changes.isEmpty()) {
            logger.info("Android 16 Tablet Rotation: ${result.packageName}: no additional manifest changes; game category already set.")
        } else {
            logger.info("Android 16 Tablet Rotation: ${result.packageName}: ${result.changes.joinToString("; ")}. Activity configuration and screen support preserved.")
        }
    }
}
