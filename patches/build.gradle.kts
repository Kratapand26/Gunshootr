group = "io.github.kratapand26"

patches {
    about {
        name = "Gunshootr Patches"
        description = "Universal Morphe patches for Android 16 tablet rotation fixes and device enhancements"
        source = "https://github.com/Kratapand26/Gunshootr"
        author = "Gunshootrr"
        contact = ""
        website = ""
        license = "GPLv3"
    }
}

val patchListGeneratorClasspath: Configuration by configurations.creating

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    // Ensure the Android DEX is built when building the MPP.
    // Without buildAndroid, the MPP only contains JVM .class files,
    // which the Morphe Android app cannot load (Android uses DEX format).
    build { dependsOn("buildAndroid") }

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
