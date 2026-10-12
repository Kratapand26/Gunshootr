package patches.dictbox

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object DictBoxConstants {
    val COMPATIBILITY_DICTBOX = Compatibility(
        name = "Dict Box",
        packageName = "com.grandsons.dictsharp",
        apkFileType = ApkFileType.APK,
        targets = listOf(
            AppTarget(
                version = null,
                isExperimental = false,
            ),
        ),
    )
}
