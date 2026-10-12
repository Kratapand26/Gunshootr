import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HostTestBuilder

extension {
    name = "extensions/linkedin.mpe"
}

android {
    namespace = "app.linkedin.extension"
    // Match the SDK already installed by Gunshootr's CI; these sources use API 26 and earlier.
    compileSdk = 34
}

extensions.configure<ApplicationAndroidComponentsExtension> {
    // AGP 9.1 enables unit tests only for the tested build type by default.
    // Exercise the same release variant that supplies the bundled extension DEX.
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests.getValue(HostTestBuilder.UNIT_TEST_TYPE).enable = true
    }
}

configurations.configureEach {
    // This extension contains Java only. Keep AGP's automatic Kotlin runtime
    // out of the DEX merged into LinkedIn, which supplies its own runtime.
    if (name == "releaseRuntimeClasspath") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Extensions cannot carry Android resources. Compile reviewed translation text
// into a Java lookup table; the running app never downloads translation code.
val translationsDir = layout.projectDirectory.dir("src/main/translations")
val generatedTranslationsDir = layout.buildDirectory.dir("generated/translations")

val generateTranslations by tasks.registering {
    inputs.dir(translationsDir)
    outputs.dir(generatedTranslationsDir)

    doLast {
        fun quote(text: String) = buildString {
            append('"')
            text.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> if (c.code < 0x20 || c.code > 0x7E) append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }

        fun readJson(file: File): Map<String, String> {
            val entries = groovy.json.JsonSlurper().parse(file, "UTF-8") as Map<*, *>
            check(entries.all { (key, value) -> key is String && value is String }) {
                "${file.name} must contain string keys and values"
            }
            @Suppress("UNCHECKED_CAST")
            return entries as Map<String, String>
        }

        val dir = translationsDir.asFile
        val languages = readJson(dir.resolve("languages.json"))
        check("en" in languages) { "English must be available" }
        val source = readJson(dir.resolve("en.json")).keys
        val out = StringBuilder()
        out.append("package app.linkedin.extension;\n\n")
        out.append("import java.util.HashMap;\nimport java.util.LinkedHashMap;\nimport java.util.Map;\n\n")
        out.append("/** Generated from reviewed translation text. Do not edit. */\n")
        out.append("final class Translations {\n")
        out.append("    static final Map<String, String> LANGUAGES = new LinkedHashMap<>();\n\n    static {\n")
        languages.forEach { (code, name) -> out.append("        LANGUAGES.put(${quote(code)}, ${quote(name)});\n") }
        out.append("    }\n\n    private Translations() {}\n\n")
        out.append("    static Map<String, String> forLanguage(String code) {\n        switch (code) {\n")
        val translated = languages.keys.filter { it != "en" }
        translated.forEachIndexed { index, code ->
            out.append("            case ${quote(code)}: return language$index();\n")
        }
        out.append("            default: return null;\n        }\n    }\n")

        val placeholder = Regex("%\\d+\\$[sd]")
        translated.forEachIndexed { index, code ->
            val entries = readJson(dir.resolve("$code.json"))
            check(entries.keys.all { it in source }) { "$code.json has unknown translation keys" }
            entries.forEach { (english, text) ->
                if (text.isNotEmpty()) {
                    check(placeholder.findAll(english).map { it.value }.sorted().toList()
                            == placeholder.findAll(text).map { it.value }.sorted().toList()) {
                        "$code.json changes the placeholders of $english"
                    }
                }
            }
            out.append("\n    private static Map<String, String> language$index() {\n")
            out.append("        Map<String, String> m = new HashMap<>();\n")
            entries.forEach { (english, text) ->
                if (text.isNotEmpty()) out.append("        m.put(${quote(english)}, ${quote(text)});\n")
            }
            out.append("        return m;\n    }\n")
        }
        out.append("}\n")
        val target = generatedTranslationsDir.get().asFile.resolve("app/linkedin/extension/Translations.java")
        target.parentFile.mkdirs()
        target.writeText(out.toString())
    }
}

android.sourceSets.getByName("main").java.srcDir(generatedTranslationsDir.get().asFile)
tasks.named("preBuild") { dependsOn(generateTranslations) }
