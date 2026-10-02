package patches.universal.manifest

import org.w3c.dom.Attr
import org.w3c.dom.Element

internal const val NS_ANDROID = "http://schemas.android.com/apk/res/android"

/** Returns the <application> element of the given document root, or null. */
internal fun Element?.applicationOrNull(): Element? =
    this?.directChildElements("application")?.firstOrNull()

/** Restrict manifest queries to the requested scope, excluding nested components. */
internal fun Element.directChildElements(tagName: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
        .filter { it.tagName == tagName }

/** Resource documents may be parsed with or without namespace awareness. */
internal fun Element.androidAttribute(name: String): Attr? {
    getAttributeNodeNS(NS_ANDROID, name)?.let { return it }
    for (index in 0 until attributes.length) {
        val attribute = attributes.item(index) as Attr
        val qualifiedName = attribute.name
        if (':' in qualifiedName && qualifiedName.substringAfter(':') == name &&
            namespaceForPrefix(qualifiedName.substringBefore(':')) == NS_ANDROID
        ) {
            return attribute
        }
    }
    return null
}

internal fun Element.setAndroidAttribute(name: String, value: String) {
    val existing = androidAttribute(name)
    if (existing != null) {
        existing.value = value
    } else {
        setAttributeNS(NS_ANDROID, "android:$name", value)
    }
}

private fun Element.namespaceForPrefix(prefix: String): String? {
    lookupNamespaceURI(prefix)?.let { return it }
    var scope: Element? = this
    while (scope != null) {
        scope.getAttributeNode("xmlns:$prefix")?.let { return it.value }
        scope = scope.parentNode as? Element
    }
    return null
}
