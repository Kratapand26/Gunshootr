extension {
    name = "extensions/linkedin.mpe"
}

android {
    namespace = "app.linkedin.extension"
    // Match the SDK already installed by Gunshootr's CI; these sources use API 26 and earlier.
    compileSdk = 34
}

androidComponents {
    // AGP 9.1 enables unit tests only for the tested build type by default.
    // Exercise the same release variant that supplies the bundled extension DEX.
    beforeVariants(selector().withBuildType("release")) {
        it.enableUnitTest = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
