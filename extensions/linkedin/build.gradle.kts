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
