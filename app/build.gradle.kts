plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Changing applicationId forces everyone to reinstall, so it stays as it is.
val appId = "io.github.karismatic_megafauna.papertimetable"
val bundledPack = "lametro.pack"

android {
    namespace = "io.github.karismatic_megafauna.papertimetable"
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "PACK_BASE_URL", "\"${providers.gradleProperty("packBaseUrl").getOrElse("")}\"")
        buildConfigField("String", "BUNDLED_PACK", "\"$bundledPack\"")
    }
    // Release builds are signed with the project's key when this machine has it
    // (set in ~/.gradle/gradle.properties, never in the repository). Without it
    // the release build still works but comes out unsigned. See README, "Releases".
    val storeFile = providers.gradleProperty("paperTimetableStoreFile").orNull
    val storePassword = providers.gradleProperty("paperTimetableStorePassword").orNull
    if (storeFile != null && storePassword != null) {
        signingConfigs.create("release") {
            this.storeFile = file(storeFile)
            this.storePassword = storePassword
            keyAlias = providers.gradleProperty("paperTimetableKeyAlias").getOrElse("paper-timetable")
            keyPassword = storePassword
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.findByName("release") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
}

// The schedule pack is built by the converter and bundled as an asset. It is
// not kept in git (it changes weekly), so the build copies it in.
val copyPack by tasks.registering(Copy::class) {
    val source = rootProject.layout.projectDirectory.file("converter/dist/$bundledPack")
    doFirst {
        if (!source.asFile.exists()) {
            throw GradleException("converter/dist/$bundledPack is missing — run the converter first (see README).")
        }
    }
    from(source)
    into(layout.projectDirectory.dir("src/main/assets"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") && !it.name.contains("UnitTest") }
    .configureEach { dependsOn(copyPack) }
tasks.matching { it.name.contains("lint", ignoreCase = true) }.configureEach { dependsOn(copyPack) }
