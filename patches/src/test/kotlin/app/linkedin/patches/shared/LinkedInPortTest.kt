package app.linkedin.patches.shared

import app.linkedin.patches.ads.hideAdsPatch
import app.linkedin.patches.download.downloadMediaPatch
import app.linkedin.patches.feed.disableDoubleTapLikePatch
import app.linkedin.patches.feed.feedFiltersPatch
import app.linkedin.patches.feed.hidePremiumUpsellsPatch
import app.linkedin.patches.feed.hidePromotedJobsPatch
import app.linkedin.patches.feed.hideSuggestedPostsPatch
import app.linkedin.patches.links.openLinksDirectlyPatch
import app.linkedin.patches.links.sanitizeShareLinksPatch
import app.linkedin.patches.messaging.messagingPatch
import app.linkedin.patches.tracking.blockTrackingPatch
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import patches.universal.manifest.androidAttribute
import patches.universal.manifest.directChildElements

class LinkedInPortTest {
    private val features = listOf(hideAdsPatch, downloadMediaPatch, disableDoubleTapLikePatch,
        feedFiltersPatch, hidePremiumUpsellsPatch, hidePromotedJobsPatch, hideSuggestedPostsPatch,
        openLinksDirectlyPatch, sanitizeShareLinksPatch, messagingPatch, blockTrackingPatch)

    @Test
    fun `all eleven ported features retain upstream versions and defaults`() {
        assertEquals(11, features.map { it.name }.distinct().size)
        features.forEach {
            assertEquals("LinkedIn", it.category)
            assertTrue(it.default)
            val compatibility = it.compatibility!!.single()
            assertEquals("com.linkedin.android", compatibility.packageName)
            assertEquals(listOf("4.1.1255.1", "4.1.1258", null), compatibility.targets.map { target -> target.version })
            assertTrue(compatibility.targets.last().isExperimental)
            assertTrue(it.dependencies.isNotEmpty())
        }
    }

    @Test
    fun `settings registration is private idempotent and preserves the app manifest`() {
        for (namespaceAware in listOf(false, true)) {
            val manifest = parse("""<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.linkedin.android">
                <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/>
                <application android:appCategory="social"><activity android:name=".Main" android:exported="true" android:configChanges="keyboard|uiMode"/></application>
                </manifest>""", namespaceAware)
            configureLinkedInSettingsManifest(manifest)
            configureLinkedInSettingsManifest(manifest)
            val root = manifest.documentElement
            val application = root.directChildElements("application").single()
            val activities = application.directChildElements("activity")
            assertEquals(2, activities.size)
            assertEquals("true", activities.first().androidAttribute("exported")!!.value)
            assertEquals("keyboard|uiMode", activities.first().androidAttribute("configChanges")!!.value)
            assertEquals("social", application.androidAttribute("appCategory")!!.value)
            val settings = activities.last()
            assertEquals("false", settings.androidAttribute("exported")!!.value)
            assertEquals("Gunshootr Patches", settings.androidAttribute("label")!!.value)
            assertEquals("36", root.directChildElements("uses-sdk").single().androidAttribute("targetSdkVersion")!!.value)
            assertTrue(root.directChildElements("uses-permission").isEmpty())
        }
    }

    @Test
    fun `settings registration rejects unrelated apps without changing them`() {
        val manifest = parse("""<manifest package="com.example.other"><application/></manifest>""")
        assertThrows(IllegalArgumentException::class.java) { configureLinkedInSettingsManifest(manifest) }
        assertEquals(0, manifest.getElementsByTagName("activity").length)
    }

    @Test
    fun `settings registration rejects duplicate applications`() {
        val manifest = parse("""<manifest package="com.linkedin.android"><application/><application/></manifest>""")
        assertThrows(IllegalStateException::class.java) { configureLinkedInSettingsManifest(manifest) }
        assertEquals(0, manifest.getElementsByTagName("activity").length)
    }

    private fun parse(xml: String, namespaceAware: Boolean = false) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = namespaceAware }.newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray()))
}
