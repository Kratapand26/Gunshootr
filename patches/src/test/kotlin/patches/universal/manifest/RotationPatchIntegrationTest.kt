package patches.universal.manifest

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.value.ValueType
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RotationPatchIntegrationTest {
    @get:Rule
    val temporaryFiles = TemporaryFolder()

    @Test
    fun `raw patch emits the edited manifest in the patcher output`() {
        val manifest = AndroidManifestBlock()
        val root = manifest.newElement("manifest")
        root.getOrCreateAndroidAttribute("package", 0).apply {
            setNamespace(null, null)
            valueAsString = "com.example.reader"
        }
        root.getOrCreateAndroidAttribute("versionName", 0x0101021c).valueAsString = "1.0"
        root.newElement("uses-sdk").apply {
            getOrCreateAndroidAttribute("minSdkVersion", 0x0101020c).apply { valueType = ValueType.DEC; data = 21 }
            getOrCreateAndroidAttribute("targetSdkVersion", 0x01010270).apply { valueType = ValueType.DEC; data = 36 }
        }
        root.newElement("application").newElement("activity")
            .getOrCreateAndroidAttribute("name", 0x01010003).valueAsString = ".Reader"
        manifest.refresh()
        val input = temporaryFiles.newFile("input.apk")
        ZipOutputStream(input.outputStream()).use { archive ->
            for ((name, bytes) in mapOf(
                "AndroidManifest.xml" to manifest.bytes,
                "assets/unchanged.txt" to "preserved asset".toByteArray(),
            )) {
                archive.putNextEntry(ZipEntry(name))
                archive.write(bytes)
                archive.closeEntry()
            }
        }
        val original = input.readBytes()
        val config = PatcherConfig(apkFile = input, temporaryFilesPath = temporaryFiles.newFolder("scratch"))
        Patcher(config).use { patcher ->
            patcher += setOf(fixAndroid16TabletRotationPatch)
            runBlocking { patcher().collect { assertNull(it.exception) } }
            val resources = patcher.get().resources!!

            assertNull(resources.resourcesApk) // No full resource table rebuild.
            val output = resources.otherResources!!
            val editedManifest = output.resolve("AndroidManifest.xml")
            assertTrue("The actual patcher output must include the edited manifest", editedManifest.isFile)
            val patched = editedManifest.inputStream().use(AndroidManifestBlock::load)
            assertEquals(36, patched.targetSdkVersion)
            assertEquals(0, patched.applicationElement.searchAttributeByResourceId(0x01010545).data)
            assertEquals(listOf("AndroidManifest.xml"), output.walkTopDown()
                .filter { it.isFile }.map { it.relativeTo(output).invariantSeparatorsPath }.toList())
        }
        assertArrayEquals(original, input.readBytes())
    }
}
