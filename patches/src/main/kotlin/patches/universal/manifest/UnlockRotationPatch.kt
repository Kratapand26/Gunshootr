package patches.universal.manifest

import app.morphe.patcher.patch.resourcePatch
import java.util.logging.Logger
import org.w3c.dom.Element

@Suppress("unused")
val unlockRotationPatch = resourcePatch(
    name = "Unlock Rotation",
    description = "Remove screenOrientation locks so the app rotates freely",
    default = false,
) {
    category("Manifest")
    execute {
        val logger = Logger.getLogger(this::class.java.name)

        var removed = 0
        document("AndroidManifest.xml").use { manifest ->
            val containers = mutableListOf<Element>()
            val activities = manifest.getElementsByTagName("activity")
            for (i in 0 until activities.length) {
                (activities.item(i) as? Element)?.let { containers.add(it) }
            }
            val depsActivities = manifest.getElementsByTagName("activity-alias")
            for (i in 0 until depsActivities.length) {
                (depsActivities.item(i) as? Element)?.let { containers.add(it) }
            }
            val application = manifest.documentElement.getElementsByTagName("application")
            if (application.length > 0) {
                (application.item(0) as? Element)?.let { containers.add(it) }
            }

            for (element in containers) {
                if (element.attributes.getNamedItem("android:screenOrientation") != null) {
                    element.attributes.removeNamedItem("android:screenOrientation")
                    removed++
                }
            }
        }

        if (removed == 0) {
            logger.info("No screenOrientation locks found in the manifest")
        } else {
            logger.info("Removed $removed screenOrientation lock(s) from the manifest")
        }
    }
}
