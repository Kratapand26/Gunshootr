package patches.universal.manifest

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement
import com.reandroid.arsc.value.ValueType
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryRotationManifestTest {
    @Test
    fun `binary mode changes only the category after serialization`() {
        val manifest = fixture()
        val original = snapshot(manifest.documentElement)

        val result = applyTabletRotationCompatibility(manifest)
        val patched = roundTrip(manifest)

        assertEquals(1, result.changes.size)
        assertTrue(result.warnings.isEmpty())
        assertEquals(36, patched.targetSdkVersion)
        assertEquals(0, patched.applicationElement.searchAttributeByResourceId(CATEGORY).data)
        patched.applicationElement.removeAttributesWithId(CATEGORY)
        assertEquals(original, snapshot(patched.documentElement))
    }

    @Test
    fun `binary SDK fallback retains every other declaration`() {
        val manifest = fixture()
        val original = snapshot(manifest.documentElement)

        applyTabletRotationCompatibility(manifest, capTargetSdkTo35 = true)
        val patched = roundTrip(manifest)

        assertEquals(35, patched.targetSdkVersion)
        patched.applicationElement.removeAttributesWithId(CATEGORY)
        patched.documentElement.getElements("uses-sdk").next().searchAttributeByResourceId(TARGET).integer(36)
        assertEquals(original, snapshot(patched.documentElement))
    }

    @Test
    fun `binary mode is idempotent in both SDK paths`() {
        for (capSdk in listOf(false, true)) {
            val manifest = fixture()
            applyTabletRotationCompatibility(manifest, capTargetSdkTo35 = capSdk)
            val patched = roundTrip(manifest)
            val before = snapshot(patched.documentElement)

            val second = applyTabletRotationCompatibility(patched, capTargetSdkTo35 = capSdk)

            assertTrue(second.changes.isEmpty())
            assertEquals(before, snapshot(patched.documentElement))
        }
    }

    @Test
    fun `binary SDK fallback skips preview references and high minimum SDKs`() {
        for (unsafeMinimum in listOf(false, true)) {
            for (type in listOf(ValueType.STRING, ValueType.REFERENCE, ValueType.DEC)) {
                val manifest = fixture()
                val sdk = manifest.documentElement.getElements("uses-sdk").next()
                val attribute = sdk.searchAttributeByResourceId(if (unsafeMinimum) MINIMUM else TARGET)
                when (type) {
                    ValueType.STRING -> attribute.valueAsString = "CinnamonBun"
                    ValueType.REFERENCE -> { attribute.valueType = type; attribute.data = 0x7f100001 }
                    else -> attribute.integer(if (unsafeMinimum) 36 else 0)
                }
                val original = snapshot(manifest.documentElement)

                val result = applyTabletRotationCompatibility(manifest, capTargetSdkTo35 = true)

                assertEquals(1, result.warnings.size)
                manifest.applicationElement.removeAttributesWithId(CATEGORY)
                assertEquals(original, snapshot(manifest.documentElement))
            }
        }
    }

    @Test
    fun `binary SDK defaults are preserved`() {
        val missingSdk = fixture()
        missingSdk.documentElement.removeElementsIf { it.name == "uses-sdk" }

        val result = applyTabletRotationCompatibility(missingSdk, capTargetSdkTo35 = true)

        assertFalse(missingSdk.documentElement.getElements("uses-sdk").hasNext())
        assertEquals(1, result.warnings.size)

        val missingTarget = fixture()
        missingTarget.documentElement.getElements("uses-sdk").next().removeAttributesWithId(TARGET)
        applyTabletRotationCompatibility(missingTarget, capTargetSdkTo35 = true)
        assertFalse(missingTarget.documentElement.getElements("uses-sdk").next().hasAttribute(TARGET))

        val missingMinimum = fixture()
        missingMinimum.documentElement.getElements("uses-sdk").next().removeAttributesWithId(MINIMUM)
        applyTabletRotationCompatibility(missingMinimum, capTargetSdkTo35 = true)
        assertEquals(35, roundTrip(missingMinimum).targetSdkVersion)
    }

    @Test
    fun `binary malformed structures fail before mutation`() {
        for (damage in listOf<(AndroidManifestBlock) -> Unit>(
            { it.documentElement.name = "other" },
            { it.documentElement.removeAttributesWithName("package") },
            { it.documentElement.newElement("application") },
            { it.documentElement.newElement("uses-sdk") },
            { it.documentElement.removeElementsIf { element -> element.name == "application" } },
        )) {
            val manifest = fixture()
            damage(manifest)
            val before = snapshot(manifest.documentElement)

            assertThrows(IllegalArgumentException::class.java) { applyTabletRotationCompatibility(manifest) }

            assertEquals(before, snapshot(manifest.documentElement))
        }
        assertThrows(IllegalArgumentException::class.java) {
            applyTabletRotationCompatibility(AndroidManifestBlock())
        }
    }

    @Test
    fun `existing binary game category with hexadecimal encoding is retained`() {
        val manifest = fixture()
        val category = manifest.applicationElement.getOrCreateAndroidAttribute("appCategory", CATEGORY)
        category.valueType = ValueType.HEX
        category.data = 0
        val before = snapshot(manifest.documentElement)

        val result = applyTabletRotationCompatibility(manifest)

        assertTrue(result.changes.isEmpty())
        assertEquals(before, snapshot(manifest.documentElement))
    }

    private fun fixture(): AndroidManifestBlock {
        val manifest = AndroidManifestBlock()
        val root = manifest.newElement("manifest")
        root.getOrCreateAndroidAttribute("package", 0).apply {
            setNamespace(null, null)
            valueAsString = "com.example.reader"
        }
        root.newElement("uses-sdk").apply {
            getOrCreateAndroidAttribute("minSdkVersion", MINIMUM).integer(21)
            getOrCreateAndroidAttribute("targetSdkVersion", TARGET).integer(36)
        }
        root.newElement("supports-screens").getOrCreateAndroidAttribute("largeScreens", 0x01010286).valueAsBoolean = false
        root.newElement("application").apply {
            getOrCreateAndroidAttribute("resizeableActivity", 0x010104f6).valueAsBoolean = false
            newElement("activity").apply {
                getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = ".Reader"
                getOrCreateAndroidAttribute("configChanges", 0x0101001f).integer(0x00000fb0)
                getOrCreateAndroidAttribute("screenOrientation", 0x0101001e).integer(1)
            }
            newElement("activity").apply {
                getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = ".Login"
                getOrCreateAndroidAttribute("configChanges", 0x0101001f).apply {
                    valueType = ValueType.REFERENCE
                    data = 0x7f100001
                }
            }
            newElement("activity-alias").getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = ".Launcher"
            newElement("property").getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = "existing.property"
        }
        return roundTrip(manifest)
    }

    private fun ResXmlAttribute.integer(value: Int) {
        valueType = ValueType.DEC
        data = value
    }

    private fun roundTrip(manifest: AndroidManifestBlock): AndroidManifestBlock {
        manifest.refresh()
        return ByteArrayInputStream(manifest.bytes).use(AndroidManifestBlock::load)
    }

    private fun snapshot(element: ResXmlElement): String = buildString {
        append(element.name)
        append(element.attributes.asSequence().map {
            val value = if (it.valueType == ValueType.STRING) it.valueAsString else it.data.toString()
            "${it.uri}:${it.name}:${it.nameId}:${it.valueType}:$value"
        }.sorted().toList())
        append(element.elements.asSequence().map(::snapshot).toList())
    }

    private companion object {
        const val CATEGORY = 0x01010545
        const val MINIMUM = 0x0101020c
        const val TARGET = 0x01010270
    }
}
