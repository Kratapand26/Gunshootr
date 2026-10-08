extension {
    name = "extensions/linkedin.mpe"
}

android {
    namespace = "app.linkedin.extension"
    // Match the SDK already installed by Gunshootr's CI; these sources use API 26 and earlier.
    compileSdk = 34
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
