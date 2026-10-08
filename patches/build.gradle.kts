group = "io.github.kratapand26"

patches {
    about {
        name = "Gunshootr Patches"
        description = "Morphe patches for tablet compatibility, Dict Box enhancements, and LinkedIn features"
        source = "https://github.com/Kratapand26/Gunshootr"
        author = "Gunshootrr"
        contact = ""
        website = ""
        license = "GPLv3"
    }
}

val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    compileOnly(libs.arsclib)
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
    testImplementation(libs.arsclib)
    testImplementation(libs.junit)
    testImplementation(libs.morphe.patcher)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks {
    // Ensure the Android DEX is built when building the MPP.
    // Without buildAndroid, the MPP only contains JVM .class files,
    // which the Morphe Android app cannot load (Android uses DEX format).
    build { dependsOn("buildAndroid") }

    test {
        useJUnit()
        dependsOn(":extensions:linkedin:testReleaseUnitTest")
    }

    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    publish {
        dependsOn("generatePatchesList")
    }
}
