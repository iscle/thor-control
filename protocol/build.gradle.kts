import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// What the app and the root daemon share: the models and the Binder contract between them (AIDL).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.thorctl.protocol"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }

    testOptions {
        unitTests {
            // The adapters are tested by calling the generated stubs directly: android.os.Binder
            // and friends come from the stub android.jar, returning defaults.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
