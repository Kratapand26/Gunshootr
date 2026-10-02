package patches.universal.manifest

import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.xml.sax.InputSource

class RotationManifestTest {
    @Test
    fun `default mode changes only the category for other apps`() {
        val document = fixture()
        val original = document.cloneNode(true) as Document

        val result = applyTabletRotationCompatibility(document)

        assertEquals("game", document.documentElement.applicationOrNull()!!.getAttributeNS(NS_ANDROID, "appCategory"))
        assertEquals("36", targetSdk(document))
        assertEquals(1, result.changes.size)
        assertTrue(result.warnings.isEmpty())
        assertOnlyExemptionAttributesChanged(original, document)
    }

    @Test
    fun `default mode retains the game exemption and original SDK for Moon Reader`() {
        for (packageName in listOf("com.flyersoft.moonreader", "com.flyersoft.moonreaderp")) {
            for (target in listOf("36", "37")) {
                val document = fixture(packageName = packageName, target = target)
                val original = document.cloneNode(true) as Document

                val result = applyTabletRotationCompatibility(document)

                assertEquals(target, targetSdk(document))
                assertEquals("game", document.documentElement.applicationOrNull()!!.getAttributeNS(NS_ANDROID, "appCategory"))
                assertEquals(1, result.changes.size)
                assertOnlyExemptionAttributesChanged(original, document)
            }
        }
    }

    @Test
    fun `disabled SDK fallback keeps the Moon Reader target SDK`() {
        val document = fixture(packageName = "com.flyersoft.moonreaderp")

        applyTabletRotationCompatibility(document, capTargetSdkTo35 = false)

        assertEquals("36", targetSdk(document))
    }

    @Test
    fun `explicit SDK fallback works for another app`() {
        val document = fixture()
        val original = document.cloneNode(true) as Document

        applyTabletRotationCompatibility(document, capTargetSdkTo35 = true)

        assertEquals("35", targetSdk(document))
        assertOnlyExemptionAttributesChanged(original, document)
    }

    @Test
    fun `default mode also preserves SDKs for renamed apps`() {
        for (packageName in listOf("com.flyersoft.moonreader.custom", "com.flyersoft.moonreaderp.clone", "com.example.moonreader")) {
            val document = fixture(packageName = packageName)

            applyTabletRotationCompatibility(document)

            assertEquals("36", targetSdk(document))
        }
    }

    @Test
    fun `older targets are never raised by the SDK fallback`() {
        for (target in listOf("1", "23", "34", "35")) {
            val document = fixture(minSdk = "1", target = target)

            applyTabletRotationCompatibility(document, capTargetSdkTo35 = true)

            assertEquals(target, targetSdk(document))
        }
    }

    @Test
    fun `SDK fallback preserves declarations that cannot be resolved safely`() {
        for ((minimum, target) in listOf(
            "36" to "36",
            "37" to "37",
            "@integer/min_sdk" to "36",
            "Baklava" to "36",
            "21" to "CinnamonBun",
            "21" to "@integer/target_sdk",
            "21" to "0",
            "21" to "-1",
            "0" to "36",
            "" to "36",
        )) {
            val document = fixture(minSdk = minimum, target = target)
            val original = document.cloneNode(true) as Document

            val result = applyTabletRotationCompatibility(document, capTargetSdkTo35 = true)

            assertEquals(target, targetSdk(document))
            assertEquals(1, result.warnings.size)
            assertOnlyExemptionAttributesChanged(original, document)
        }
    }

    @Test
    fun `missing SDK declaration is not invented`() {
        val document = parse("""
            <manifest xmlns:android="$NS_ANDROID" package="com.flyersoft.moonreaderp">
                <application><activity android:name=".Reader" /></application>
            </manifest>
        """)

        val result = applyTabletRotationCompatibility(document, capTargetSdkTo35 = true)

        assertTrue(document.documentElement.directChildElements("uses-sdk").isEmpty())
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `omitted target SDK keeps its minimum SDK default`() {
        val document = parse("""
            <manifest xmlns:android="$NS_ANDROID" package="com.flyersoft.moonreaderp">
                <uses-sdk android:minSdkVersion="21" />
                <application />
            </manifest>
        """)

        applyTabletRotationCompatibility(document, capTargetSdkTo35 = true)

        assertFalse(document.documentElement.directChildElements("uses-sdk").single().hasAttributeNS(NS_ANDROID, "targetSdkVersion"))
    }

    @Test
    fun `omitted minimum SDK uses the Android default`() {
        val document = parse("""
            <manifest xmlns:android="$NS_ANDROID" package="com.flyersoft.moonreaderp">
                <uses-sdk android:targetSdkVersion="36" />
                <application />
            </manifest>
        """)

        applyTabletRotationCompatibility(document, capTargetSdkTo35 = true)

        assertEquals("35", targetSdk(document))
        assertFalse(document.documentElement.directChildElements("uses-sdk").single().hasAttributeNS(NS_ANDROID, "minSdkVersion"))
    }

    @Test
    fun `activities aliases screen support and existing properties are preserved`() {
        val document = fixture(packageName = "com.flyersoft.moonreaderp")
        val root = document.documentElement
        val activity = root.applicationOrNull()!!.directChildElements("activity").first()
        activity.setAttributeNS(NS_ANDROID, "android:configChanges", "@integer/player_config_changes")
        activity.setAttributeNS(NS_ANDROID, "android:screenOrientation", "@integer/player_orientation")
        val original = document.cloneNode(true) as Document

        applyTabletRotationCompatibility(document)

        assertOnlyExemptionAttributesChanged(original, document)
        val alias = root.applicationOrNull()!!.directChildElements("activity-alias").single()
        assertFalse(alias.hasAttributeNS(NS_ANDROID, "configChanges"))
        assertFalse(alias.hasAttributeNS(NS_ANDROID, "resizeableActivity"))
    }

    @Test
    fun `patch does not add screen support configuration flags or properties`() {
        val document = parse("""
            <manifest xmlns:android="$NS_ANDROID" package="com.example.app">
                <application><activity android:name=".Main" /></application>
            </manifest>
        """)

        applyTabletRotationCompatibility(document)

        assertEquals(0, document.getElementsByTagName("supports-screens").length)
        assertEquals(0, document.getElementsByTagName("property").length)
        val activity = document.documentElement.applicationOrNull()!!.directChildElements("activity").single()
        assertEquals(1, activity.attributes.length)
        assertFalse(document.documentElement.applicationOrNull()!!.hasAttributeNS(NS_ANDROID, "resizeableActivity"))
    }

    @Test
    fun `patch is idempotent for both SDK paths`() {
        for (capSdk in listOf(false, true)) {
            val document = fixture()
            applyTabletRotationCompatibility(document, capTargetSdkTo35 = capSdk)
            val afterFirstPass = xml(document)

            val secondPass = applyTabletRotationCompatibility(document, capTargetSdkTo35 = capSdk)

            assertEquals(afterFirstPass, xml(document))
            assertTrue(secondPass.changes.isEmpty())
        }
    }

    @Test
    fun `Android attributes survive serialization with a different namespace prefix`() {
        val document = parse("""
            <manifest xmlns:a="$NS_ANDROID" package="com.flyersoft.moonreaderp">
                <uses-sdk a:minSdkVersion="21" a:targetSdkVersion="36" />
                <application a:appCategory="productivity">
                    <activity a:name=".Reader" a:configChanges="orientation|screenSize" />
                </application>
            </manifest>
        """)

        applyTabletRotationCompatibility(document, capTargetSdkTo35 = true)
        val roundTripped = parse(xml(document))

        assertEquals("35", targetSdk(roundTripped))
        assertEquals("game", roundTripped.documentElement.applicationOrNull()!!.getAttributeNS(NS_ANDROID, "appCategory"))
        assertEquals("orientation|screenSize", roundTripped.getElementsByTagName("activity").item(0).attributes.getNamedItemNS(NS_ANDROID, "configChanges").nodeValue)
        assertTrue(applyTabletRotationCompatibility(roundTripped).changes.isEmpty())
    }

    @Test
    fun `malformed manifest structure fails before any mutation`() {
        for (source in listOf(
            "<other package='com.example.app'><application /></other>",
            "<manifest><application /></manifest>",
            "<manifest package='com.example.app' />",
            "<manifest package='com.example.app'><application /><application /></manifest>",
            "<manifest package='com.example.app'><uses-sdk /><uses-sdk /><application /></manifest>",
            "<manifest package='com.example.app'><wrapper><application /></wrapper></manifest>",
        )) {
            val document = parse(source)
            val before = xml(document)

            assertThrows(IllegalArgumentException::class.java) { applyTabletRotationCompatibility(document) }

            assertEquals(before, xml(document))
        }
    }

    @Test
    fun `empty document fails without a success result`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()

        assertThrows(IllegalArgumentException::class.java) { applyTabletRotationCompatibility(document) }
    }

    @Test
    fun `non namespace aware documents retain SDK and category attributes correctly`() {
        for (prefix in listOf("android", "a")) {
            val document = parse("""
                <manifest xmlns:$prefix="$NS_ANDROID" package="com.flyersoft.moonreaderp">
                    <uses-sdk $prefix:minSdkVersion="21" $prefix:targetSdkVersion="36" />
                    <application $prefix:appCategory="productivity">
                        <activity $prefix:name=".Reader" $prefix:configChanges="keyboardHidden|orientation|screenSize|uiMode" />
                    </application>
                </manifest>
            """, namespaceAware = false)

            applyTabletRotationCompatibility(document, capTargetSdkTo35 = true)
            val roundTripped = parse(xml(document))

            assertEquals("35", targetSdk(roundTripped))
            assertEquals("game", roundTripped.documentElement.applicationOrNull()!!.getAttributeNS(NS_ANDROID, "appCategory"))
            assertEquals("keyboardHidden|orientation|screenSize|uiMode", roundTripped.getElementsByTagName("activity").item(0).attributes.getNamedItemNS(NS_ANDROID, "configChanges").nodeValue)
            assertTrue(applyTabletRotationCompatibility(document, capTargetSdkTo35 = true).changes.isEmpty())
        }
    }

    @Test
    fun `Moon Reader 10_7 original reader configuration flags remain intact`() {
        val document = parse("""
            <manifest xmlns:android="$NS_ANDROID" package="com.flyersoft.moonreaderp"
                android:versionCode="1007000" android:versionName="10.7">
                <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="36" />
                <application android:resizeableActivity="true">
                    <activity android:name="com.flyersoft.moonreaderp.ActivityMain" android:configChanges="0x00000fb0" android:resizeableActivity="true" />
                    <activity android:name="com.flyersoft.moonreaderp.ActivityTxt" android:configChanges="0x00000fb0" android:resizeableActivity="true" />
                    <activity android:name="com.dropbox.core.android.AuthActivity" android:configChanges="0x00000090" android:resizeableActivity="true" />
                    <activity android:name="com.google.android.gms.common.api.GoogleApiActivity" />
                </application>
            </manifest>
        """)
        val original = document.cloneNode(true) as Document

        val result = applyTabletRotationCompatibility(document)

        assertEquals("36", targetSdk(document))
        assertEquals(1, result.changes.size)
        assertOnlyExemptionAttributesChanged(original, document)
    }

    @Test
    fun `new category is serialized correctly in both parser modes`() {
        for (namespaceAware in listOf(false, true)) {
            val document = parse("""
                <manifest xmlns:android="$NS_ANDROID" package="com.flyersoft.moonreaderp">
                    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="36" />
                    <application><activity android:name=".Reader" /></application>
                </manifest>
            """, namespaceAware = namespaceAware)

            applyTabletRotationCompatibility(document)
            val roundTripped = parse(xml(document))

            assertEquals("game", roundTripped.documentElement.applicationOrNull()!!.getAttributeNS(NS_ANDROID, "appCategory"))
            assertEquals("36", targetSdk(roundTripped))
            assertEquals(1, roundTripped.documentElement.applicationOrNull()!!.attributes.length)
            assertTrue(applyTabletRotationCompatibility(document).changes.isEmpty())
        }
    }

    private fun fixture(
        packageName: String = "com.example.app",
        minSdk: String = "21",
        target: String = "36",
    ): Document = parse("""
        <manifest xmlns:android="$NS_ANDROID" package="$packageName">
            <uses-sdk android:minSdkVersion="$minSdk" android:targetSdkVersion="$target" />
            <uses-permission android:name="android.permission.INTERNET" />
            <supports-screens android:largeScreens="false" android:anyDensity="false" android:requiresSmallestWidthDp="720" />
            <application android:appCategory="productivity" android:resizeableActivity="false" android:label="Reader">
                <activity android:name=".Reader" android:screenOrientation="portrait" android:configChanges="orientation|screenSize" android:resizeableActivity="false">
                    <property android:name="existing.activity.property" android:value="false" />
                </activity>
                <activity android:name=".Login" android:exported="false" />
                <activity android:name=".Camera" android:screenOrientation="landscape" android:configChanges="0x40000" />
                <activity-alias android:name=".Launcher" android:targetActivity=".Reader" android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity-alias>
                <property android:name="existing.app.property" android:resource="@bool/example" />
            </application>
        </manifest>
    """)

    /** Every declaration except the two intended exemption attributes must remain identical. */
    private fun assertOnlyExemptionAttributesChanged(original: Document, patched: Document) {
        val restored = patched.cloneNode(true) as Document
        val originalApplication = original.documentElement.applicationOrNull()!!
        val restoredApplication = restored.documentElement.applicationOrNull()!!
        if (originalApplication.hasAttributeNS(NS_ANDROID, "appCategory")) {
            restoredApplication.setAttributeNS(
                NS_ANDROID, "android:appCategory", originalApplication.getAttributeNS(NS_ANDROID, "appCategory"),
            )
        } else {
            restoredApplication.removeAttributeNS(NS_ANDROID, "appCategory")
        }
        val originalSdk = original.documentElement.directChildElements("uses-sdk").single()
        restored.documentElement.directChildElements("uses-sdk").single().setAttributeNS(
            NS_ANDROID, "android:targetSdkVersion", originalSdk.getAttributeNS(NS_ANDROID, "targetSdkVersion"),
        )
        assertEquals(xml(original), xml(restored))
    }

    private fun targetSdk(document: Document): String =
        document.documentElement.directChildElements("uses-sdk").single().getAttributeNS(NS_ANDROID, "targetSdkVersion")

    private fun parse(source: String, namespaceAware: Boolean = true): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = namespaceAware
    }.newDocumentBuilder().parse(InputSource(StringReader(source.trimIndent())))

    private fun xml(document: Document): String = StringWriter().also {
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(it))
    }.toString()
}
