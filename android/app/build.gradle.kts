import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Build number comes from CI (-PbrowserxBuild=<commit count>). Local builds default to 0.
val buildNumber = (project.findProperty("browserxBuild") as String?)?.toIntOrNull() ?: 0

// Optional private signing (keystore.properties is git-ignored). When it is missing we fall
// back to the bundled BrowserX debug key so that every CI build has the SAME signature and
// in-app updates can be installed over the previous build.
val signingPropsFile = rootProject.file("keystore.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) signingPropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.browserx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.browserx.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1000 + buildNumber
        versionName = "1.1.0"
    }

    signingConfigs {
        create("browserx") {
            val customStore = signingProps.getProperty("storeFile")
            storeFile = if (customStore != null) rootProject.file(customStore) else file("browserx-signing.p12")
            storePassword = signingProps.getProperty("storePassword") ?: "browserx"
            keyAlias = signingProps.getProperty("keyAlias") ?: "browserx"
            keyPassword = signingProps.getProperty("keyPassword") ?: "browserx"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("browserx")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("browserx")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.webkit:webkit:1.12.1")
    testImplementation("junit:junit:4.13.2")
}
