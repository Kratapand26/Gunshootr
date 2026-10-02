package patches.universal.manifest

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.value.ValueType
import org.w3c.dom.Document

private const val APP_CATEGORY_ID = 0x01010545
private const val MIN_SDK_ID = 0x0101020c
private const val TARGET_SDK_ID = 0x01010270

internal data class RotationManifestResult(
    val packageName: String,
    val changes: List<String>,
    val warnings: List<String>,
)

internal fun applyTabletRotationCompatibility(
    manifest: Document,
    capTargetSdkTo35: Boolean = false,
): RotationManifestResult {
    val root = manifest.documentElement
    require(root != null && root.tagName == "manifest") { "Missing <manifest> root. No rotation changes applied." }
    val packageName = root.getAttribute("package")
    require(packageName.isNotBlank()) { "Missing manifest package name. No rotation changes applied." }

    // Validate the structure before changing anything; don't report success for a partial patch.
    val applications = root.directChildElements("application")
    require(applications.size == 1) { "Expected exactly one <application>. No rotation changes applied." }
    val sdkElements = root.directChildElements("uses-sdk")
    require(sdkElements.size <= 1) { "Multiple <uses-sdk> declarations. No rotation changes applied." }
    val application = applications.single()
    val usesSdk = sdkElements.singleOrNull()
    val decision = rotationManifestDecision(
        packageName, application.androidAttribute("appCategory")?.value,
        usesSdk != null,
        usesSdk?.androidAttribute("minSdkVersion")?.value,
        usesSdk?.androidAttribute("targetSdkVersion")?.value,
        capTargetSdkTo35,
    )
    if (decision.capSdk) usesSdk!!.setAndroidAttribute("targetSdkVersion", "35")
    if (decision.setCategory) application.setAndroidAttribute("appCategory", "game")
    return decision.result
}

internal fun applyTabletRotationCompatibility(
    manifest: AndroidManifestBlock,
    capTargetSdkTo35: Boolean = false,
): RotationManifestResult {
    val root = manifest.documentElement
    require(root != null && root.name == "manifest") { "Missing <manifest> root. No rotation changes applied." }
    val packageName = manifest.packageName
    require(!packageName.isNullOrBlank()) { "Missing manifest package name. No rotation changes applied." }
    val applications = root.getElements("application").asSequence().toList()
    require(applications.size == 1) { "Expected exactly one <application>. No rotation changes applied." }
    val sdkElements = root.getElements("uses-sdk").asSequence().toList()
    require(sdkElements.size <= 1) { "Multiple <uses-sdk> declarations. No rotation changes applied." }
    val application = applications.single()
    val usesSdk = sdkElements.singleOrNull()
    val category = application.searchAttributeByResourceId(APP_CATEGORY_ID)
    val oldCategory = if (category?.isInteger() == true && category.data == 0) "game" else category?.sdkText()
    val decision = rotationManifestDecision(
        packageName, oldCategory, usesSdk != null,
        usesSdk?.searchAttributeByResourceId(MIN_SDK_ID)?.sdkText(),
        usesSdk?.searchAttributeByResourceId(TARGET_SDK_ID)?.sdkText(),
        capTargetSdkTo35,
    )
    if (decision.capSdk) {
        usesSdk!!.getOrCreateAndroidAttribute("targetSdkVersion", TARGET_SDK_ID).setInteger(35)
    }
    if (decision.setCategory) {
        application.getOrCreateAndroidAttribute("appCategory", APP_CATEGORY_ID).setInteger(0)
    }
    return decision.result
}

private fun ResXmlAttribute.isInteger(): Boolean = valueType == ValueType.DEC || valueType == ValueType.HEX

private fun ResXmlAttribute.sdkText(): String = when {
    isInteger() -> data.toString()
    valueType == ValueType.STRING -> valueAsString
    else -> "${valueType}:0x${data.toUInt().toString(16)}"
}

private fun ResXmlAttribute.setInteger(value: Int) {
    valueType = ValueType.DEC
    data = value
}

private data class RotationManifestDecision(
    val result: RotationManifestResult,
    val capSdk: Boolean,
    val setCategory: Boolean,
)

private fun rotationManifestDecision(
    packageName: String,
    category: String?,
    hasSdkDeclaration: Boolean,
    minimumSdk: String?,
    targetSdk: String?,
    capTargetSdkTo35: Boolean,
): RotationManifestDecision {
    val changes = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    var capSdk = false

    if (capTargetSdkTo35) {
        if (!hasSdkDeclaration) {
            warnings += "No <uses-sdk> declaration; kept the original SDK defaults."
        } else {
            val minSdkText = minimumSdk ?: "1"
            val targetSdkText = targetSdk ?: minSdkText
            val minSdk = minSdkText.trim().toIntOrNull()?.takeIf { it > 0 }
            val targetSdk = targetSdkText.trim().toIntOrNull()?.takeIf { it > 0 }

            when {
                minSdk == null || targetSdk == null ->
                    warnings += "Unresolved SDK declaration (min=$minSdkText, target=$targetSdkText); SDK cap skipped."
                minSdk > 35 ->
                    warnings += "minSdkVersion=$minSdk requires API 36 or newer; SDK cap skipped."
                targetSdk >= 36 -> {
                    capSdk = true
                    changes += "targetSdkVersion: $targetSdkText -> 35 (selected compatibility fallback)"
                }
            }
        }
    }

    // Keep the Android 16 game exemption used by the existing working patch.
    // Do not add configChanges: activities must keep their own resource/lifecycle handling.
    // Do not force resizability, modify screen support, or write activity attributes to aliases.
    val oldCategory = category.orEmpty()
    if (oldCategory != "game") {
        changes += "appCategory: ${oldCategory.ifEmpty { "unset" }} -> game"
    }

    return RotationManifestDecision(
        RotationManifestResult(packageName, changes, warnings), capSdk, oldCategory != "game",
    )
}
