package patches.universal.manifest

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element
import java.util.logging.Logger

@Suppress("unused")
val fixAndroid16TabletRotationPatch = resourcePatch(
    name = "Fix Android 16 Tablet Rotation",
    description = "Exempts the app from Android 16 tablet orientation restrictions so in-app rotate buttons work properly.",
    default = false,
) {
    category("Manifest")

    val exemptAsGame by booleanOption(
        title = "Exempt as Game Category",
        default = true,
        key = "exemptAsGame",
        description = "Sets android:appCategory=\"game\" in <application> to bypass Android 16 tablet orientation blocks.",
    )

    val capTargetSdkTo35 by booleanOption(
        title = "Cap Target SDK to 35 (Android 15)",
        default = true,
        key = "capTargetSdkTo35",
        description = "Caps targetSdkVersion to 35 so Android 16 does not enforce API 36+ large-screen orientation restrictions.",
    )

    val addRotationConfigChanges by booleanOption(
        title = "Ensure Rotation Config Changes",
        default = true,
        key = "addRotationConfigChanges",
        description = "Adds orientation and screenSize to configChanges on all activities to prevent crashes or restarts during rotation.",
    )

    val enableResizable by booleanOption(
        title = "Force Resizable Activity",
        default = true,
        key = "enableResizable",
        description = "Sets android:resizeableActivity=\"true\" so rotated layouts fill tablet displays properly without letterboxing.",
    )

    execute {
        val logger = Logger.getLogger(this::class.java.name)
        var patchedActivities = 0
        var categoryApplied = false
        var sdkCapped = false

        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement ?: return@use
            val application = root.applicationOrNull()

            if (application == null) {
                logger.warning("No <application> tag found in AndroidManifest.xml.")
                return@use
            }

            // 1. Set appCategory="game" to exempt from Android 16 tablet orientation policy
            if (exemptAsGame == true) {
                application.setAttributeNS(NS_ANDROID, "android:appCategory", "game")
                categoryApplied = true
            }

            // 2. Set resizeableActivity on application
            if (enableResizable == true) {
                application.setAttributeNS(NS_ANDROID, "android:resizeableActivity", "true")
            }

            // 3. Ensure supports-screens allows large and xlarge screens on tablets
            val supportsList = manifest.getElementsByTagName("supports-screens")
            val supportsScreens = if (supportsList.length > 0) {
                supportsList.item(0) as? Element
            } else {
                val created = manifest.createElement("supports-screens")
                root.insertBefore(created, application)
                created
            }
            if (supportsScreens != null) {
                supportsScreens.setAttributeNS(NS_ANDROID, "android:smallScreens", "true")
                supportsScreens.setAttributeNS(NS_ANDROID, "android:normalScreens", "true")
                supportsScreens.setAttributeNS(NS_ANDROID, "android:largeScreens", "true")
                supportsScreens.setAttributeNS(NS_ANDROID, "android:xlargeScreens", "true")
                supportsScreens.setAttributeNS(NS_ANDROID, "android:requiresSmallestWidthDp", "0")
                supportsScreens.setAttributeNS(NS_ANDROID, "android:anyDensity", "true")
            }

            // 4. Cap targetSdkVersion to 35 if it is >= 36
            if (capTargetSdkTo35 == true) {
                val usesSdk = root.getElementsByTagName("uses-sdk")?.item(0) as? Element
                if (usesSdk != null) {
                    val currentTarget = usesSdk.getAttributeNS(NS_ANDROID, "targetSdkVersion").toIntOrNull() ?: 0
                    if (currentTarget >= 36) {
                        usesSdk.setAttributeNS(NS_ANDROID, "android:targetSdkVersion", "35")
                        sdkCapped = true
                    }
                }
            }

            // 5. Update activities and activity-aliases with configChanges & resizability
            val neededConfigs = setOf(
                "orientation",
                "screenSize",
                "smallestScreenSize",
                "screenLayout",
                "density",
                "layoutDirection",
            )
            val activityContainers = mutableListOf<Element>()
            val activities = manifest.getElementsByTagName("activity")
            for (i in 0 until activities.length) {
                (activities.item(i) as? Element)?.let { activityContainers.add(it) }
            }
            val aliases = manifest.getElementsByTagName("activity-alias")
            for (i in 0 until aliases.length) {
                (aliases.item(i) as? Element)?.let { activityContainers.add(it) }
            }

            for (act in activityContainers) {
                if (enableResizable == true) {
                    act.setAttributeNS(NS_ANDROID, "android:resizeableActivity", "true")
                }

                if (addRotationConfigChanges == true) {
                    val existing = act.getAttributeNS(NS_ANDROID, "configChanges")
                    val parts = if (existing.isNullOrEmpty()) {
                        mutableSetOf()
                    } else {
                        existing.split("|").map { it.trim() }.toMutableSet()
                    }
                    parts.addAll(neededConfigs)
                    act.setAttributeNS(NS_ANDROID, "android:configChanges", parts.joinToString("|"))
                }
                patchedActivities++
            }
        }

        logger.info("Android 16 Tablet Rotation Patch applied: categoryGame=$categoryApplied, sdkCappedTo35=$sdkCapped, activitiesPatched=$patchedActivities")
    }
}
