import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.metro)
}

android {
    namespace = "com.thorctl"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.thorctl"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 14
        versionName = "2.2.1"
    }

    // Release builds are signed with keystore/thorctl.jks; it and its passwords
    // (keystore/keystore.properties: storePassword, keyAlias, keyPassword) are kept out of git.
    // Without them builds fall back to the debug key, and such an APK can't update an install
    // that was signed with the release key.
    val thorKeystore = rootProject.file("keystore/thorctl.jks")
    val thorKeyProps = Properties().apply {
        rootProject.file("keystore/keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (thorKeystore.exists() && thorKeyProps.getProperty("storePassword") != null) {
            create("thor") {
                storeFile = thorKeystore
                storePassword = thorKeyProps.getProperty("storePassword")
                keyAlias = thorKeyProps.getProperty("keyAlias")
                keyPassword = thorKeyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("thor") ?: signingConfigs.getByName("debug")
        }
        debug {
            signingConfig = signingConfigs.findByName("thor") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Framework calls the tests don't care about (Log, SystemClock) return defaults.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

// LayeringTest reads the main sources to check the package layering.
val mainSources = file("src/main/java")
tasks.withType<Test>().configureEach {
    inputs.dir(mainSources)
    systemProperty("sources.path", mainSources.absolutePath)
}

dependencies {
    implementation(project(":protocol"))
    // The daemon ships in this APK (service.sh runs it from here) but the app never calls it:
    // runtime only, so app code can't even compile against it.
    runtimeOnly(project(":daemon"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.metrox.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
