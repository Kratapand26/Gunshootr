package app.linkedin.patches.shared

import org.w3c.dom.Document
import patches.universal.manifest.androidAttribute
import patches.universal.manifest.directChildElements
import patches.universal.manifest.setAndroidAttribute

internal fun configureLinkedInSettingsManifest(document: Document) {
    val root = document.documentElement
    require(root?.tagName == "manifest" && root.getAttribute("package") == "com.linkedin.android") {
        "LinkedIn settings require a com.linkedin.android manifest."
    }
    val application = root.directChildElements("application").singleOrNull()
        ?: error("Expected exactly one LinkedIn application element.")
    val className = "app.linkedin.extension.SettingsActivity"
    val existing = application.directChildElements("activity")
        .filter { it.androidAttribute("name")?.value == className }
    require(existing.size <= 1) { "Duplicate LinkedIn patch settings activities." }
    val activity = existing.singleOrNull() ?: document.createElement("activity")
    activity.setAndroidAttribute("name", className)
    activity.setAndroidAttribute("exported", "false")
    activity.setAndroidAttribute("label", "Gunshootr Patches")
    activity.setAndroidAttribute("theme", "@android:style/Theme.DeviceDefault.DayNight")
    if (existing.isEmpty()) application.appendChild(activity)
}
