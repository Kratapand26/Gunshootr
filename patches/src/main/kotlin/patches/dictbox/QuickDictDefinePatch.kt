package patches.dictbox

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element
import patches.universal.manifest.NS_ANDROID
import java.util.logging.Logger

@Suppress("unused")
val quickDictDefinePatch = resourcePatch(
    name = "Quick Dict & Define Action",
    description = "Enables Dict Box to handle system Translate/Define actions and adds a direct 'Dict' selection action.",
    default = true,
) {
    category("Dict Box")
    compatibleWith("com.grandsons.dictsharp")

    execute {
        val logger = Logger.getLogger(this::class.java.name)

        document("AndroidManifest.xml").use { manifest ->
            val root = manifest.documentElement
            val application = root.getElementsByTagName("application").item(0) as? Element
            if (application == null) {
                logger.warning("No <application> element found in manifest.")
                return@use
            }

            val targetActivities = mutableListOf<Element>()
            val candidates = mutableListOf<Element>()

            // Gather all activity and activity-alias elements
            for (tag in listOf("activity", "activity-alias")) {
                val nodes = manifest.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    (nodes.item(i) as? Element)?.let { candidates.add(it) }
                }
            }

            // 1. Locate components handling text selection (PROCESS_TEXT or SEND)
            for (cand in candidates) {
                val filters = cand.getElementsByTagName("intent-filter")
                for (j in 0 until filters.length) {
                    val filter = filters.item(j) as? Element ?: continue
                    val actions = filter.getElementsByTagName("action")
                    for (k in 0 until actions.length) {
                        val action = actions.item(k) as? Element ?: continue
                        val name = getAttr(action, "name")
                        if (name == "android.intent.action.PROCESS_TEXT" ||
                            name == "android.intent.action.SEND"
                        ) {
                            val resolvedTarget = if (cand.tagName == "activity-alias") {
                                val targetName = getAttr(cand, "targetActivity")
                                candidates.firstOrNull { it.tagName == "activity" && getAttr(it, "name") == targetName } ?: cand
                            } else {
                                cand
                            }
                            if (!targetActivities.contains(resolvedTarget)) {
                                targetActivities.add(resolvedTarget)
                            }
                        }
                    }
                }
            }

            // Fallback: If no dedicated text activity found, find the main launcher activity
            if (targetActivities.isEmpty()) {
                for (cand in candidates) {
                    val filters = cand.getElementsByTagName("intent-filter")
                    for (j in 0 until filters.length) {
                        val filter = filters.item(j) as? Element ?: continue
                        val actions = filter.getElementsByTagName("action")
                        for (k in 0 until actions.length) {
                            val action = actions.item(k) as? Element ?: continue
                            if (getAttr(action, "name") == "android.intent.action.MAIN") {
                                val resolvedTarget = if (cand.tagName == "activity-alias") {
                                    val targetName = getAttr(cand, "targetActivity")
                                    candidates.firstOrNull { it.tagName == "activity" && getAttr(it, "name") == targetName } ?: cand
                                } else {
                                    cand
                                }
                                targetActivities.add(resolvedTarget)
                                break
                            }
                        }
                    }
                }
            }

            if (targetActivities.isEmpty()) {
                logger.warning("Could not find any suitable target activity in Dict Box manifest.")
                return@use
            }

            var patchedCount = 0
            for (targetActivity in targetActivities) {
                val targetName = getAttr(targetActivity, "name")
                targetActivity.setAttributeNS(NS_ANDROID, "android:exported", "true")

                // Add TRANSLATE filter (labeled "Translate")
                if (!hasActionAndLabel(targetActivity, "android.intent.action.TRANSLATE", "Translate")) {
                    targetActivity.appendChild(
                        createFilter(manifest, "android.intent.action.TRANSLATE", label = "Translate", mimeType = "text/plain")
                    )
                }

                // Add TRANSLATE filter (labeled "Define")
                if (!hasActionAndLabel(targetActivity, "android.intent.action.TRANSLATE", "Define")) {
                    targetActivity.appendChild(
                        createFilter(manifest, "android.intent.action.TRANSLATE", label = "Define", mimeType = "text/plain")
                    )
                }

                // Add PROCESS_TEXT filter (labeled "Define")
                if (!hasActionAndLabel(targetActivity, "android.intent.action.PROCESS_TEXT", "Define")) {
                    targetActivity.appendChild(
                        createFilter(manifest, "android.intent.action.PROCESS_TEXT", label = "Define", mimeType = "text/plain")
                    )
                }

                // Add activity-alias with label "Dict"
                val aliasName = if (targetName.startsWith(".")) {
                    "${targetName}DictAlias"
                } else {
                    "${targetName}.DictAlias"
                }

                val existingAliases = manifest.getElementsByTagName("activity-alias")
                var aliasExists = false
                for (a in 0 until existingAliases.length) {
                    val existing = existingAliases.item(a) as? Element ?: continue
                    if (getAttr(existing, "name") == aliasName) {
                        aliasExists = true
                        break
                    }
                }

                if (!aliasExists) {
                    val alias = manifest.createElement("activity-alias")
                    alias.setAttributeNS(NS_ANDROID, "android:name", aliasName)
                    alias.setAttributeNS(NS_ANDROID, "android:targetActivity", targetName)
                    alias.setAttributeNS(NS_ANDROID, "android:label", "Dict")
                    alias.setAttributeNS(NS_ANDROID, "android:exported", "true")

                    alias.appendChild(
                        createFilter(manifest, "android.intent.action.PROCESS_TEXT", mimeType = "text/plain")
                    )
                    alias.appendChild(
                        createFilter(manifest, "android.intent.action.TRANSLATE", mimeType = "text/plain")
                    )

                    application.appendChild(alias)
                }

                patchedCount++
                logger.info("Configured Quick Dict & Define actions on activity: $targetName")
            }

            logger.info("Successfully patched $patchedCount activity/alias target(s) for Dict Box.")
        }
    }
}

private fun getAttr(element: Element, name: String): String {
    val nsVal = element.getAttributeNS(NS_ANDROID, name)
    if (nsVal.isNotEmpty()) return nsVal
    val androidVal = element.getAttribute("android:$name")
    if (androidVal.isNotEmpty()) return androidVal
    return element.getAttribute(name)
}

private fun hasActionAndLabel(element: Element, actionName: String, label: String): Boolean {
    val filters = element.getElementsByTagName("intent-filter")
    for (i in 0 until filters.length) {
        val filter = filters.item(i) as? Element ?: continue
        if (getAttr(filter, "label") == label) {
            val actions = filter.getElementsByTagName("action")
            for (j in 0 until actions.length) {
                val action = actions.item(j) as? Element ?: continue
                if (getAttr(action, "name") == actionName) return true
            }
        }
    }
    return false
}

private fun createFilter(
    doc: Document,
    actionName: String,
    label: String? = null,
    mimeType: String? = null,
): Element {
    val filter = doc.createElement("intent-filter")
    if (label != null) {
        filter.setAttributeNS(NS_ANDROID, "android:label", label)
    }

    val action = doc.createElement("action")
    action.setAttributeNS(NS_ANDROID, "android:name", actionName)
    filter.appendChild(action)

    val category = doc.createElement("category")
    category.setAttributeNS(NS_ANDROID, "android:name", "android.intent.category.DEFAULT")
    filter.appendChild(category)

    if (mimeType != null) {
        val data = doc.createElement("data")
        data.setAttributeNS(NS_ANDROID, "android:mimeType", mimeType)
        filter.appendChild(data)
    }

    return filter
}
