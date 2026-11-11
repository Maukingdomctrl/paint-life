import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing comes from keystore.properties (gitignored) or PAINTLIFE_* environment variables.
// It is never committed, and release builds never fall back to the debug key.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val storeFilePath: String? = keystoreProperties.getProperty("storeFile")
    ?: providers.environmentVariable("PAINTLIFE_STORE_FILE").orNull
val storePasswordValue: String? = keystoreProperties.getProperty("storePassword")
    ?: providers.environmentVariable("PAINTLIFE_STORE_PASSWORD").orNull
val keyAliasValue: String? = keystoreProperties.getProperty("keyAlias")
    ?: providers.environmentVariable("PAINTLIFE_KEY_ALIAS").orNull
val keyPasswordValue: String? = keystoreProperties.getProperty("keyPassword")
    ?: providers.environmentVariable("PAINTLIFE_KEY_PASSWORD").orNull
val releaseSigningConfigured =
    listOf(storeFilePath, storePasswordValue, keyAliasValue, keyPasswordValue).all { !it.isNullOrBlank() }

val releaseTaskRequested = gradle.startParameter.taskNames.any { name ->
    listOf("assembleRelease", "bundleRelease", "packageRelease", "installRelease")
        .any { name.endsWith(it, ignoreCase = true) }
}
if (releaseTaskRequested && !releaseSigningConfigured) {
    throw GradleException(
        "Release signing is not configured. Copy keystore.properties.example to keystore.properties " +
            "and fill it in, or set PAINTLIFE_STORE_FILE, PAINTLIFE_STORE_PASSWORD, PAINTLIFE_KEY_ALIAS " +
            "and PAINTLIFE_KEY_PASSWORD. Release builds are never signed with the debug key."
    )
}

val robolectricMavenRepo: String? = providers.gradleProperty("paintlife.robolectric.mavenRepoLocal").orNull

android {
    namespace = "com.mauforge.paintlife"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mauforge.paintlife"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = rootProject.file(storeFilePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // Build runs on JDK 25 (gradle/gradle-daemon-jvm.properties); bytecode targets Java 17.
    // With built-in Kotlin, the Kotlin jvmTarget follows targetCompatibility.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric reaches into java.io / jdk.internal.access (FileDescriptor), which
                // JDK 17+ blocks unless the test JVM opens and exports them.
                test.jvmArgs(
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                )
                // Robolectric mis-encodes spaces in its Maven cache path on Windows. Machines whose
                // home folder contains a space set a space-free path in ~/.gradle/gradle.properties.
                robolectricMavenRepo?.let { test.systemProperty("maven.repo.local", it) }
            }
        }
    }

    sourceSets {
        // MigrationTestHelper reads exported Room schemas as assets. Robolectric unit tests only
        // see the tested variant's merged assets (test-source-set assets are not packaged), so the
        // schema JSON (structure only, no user data) is added to debug builds; release is unaffected.
        getByName("debug").assets.directories += "$projectDir/schemas"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
