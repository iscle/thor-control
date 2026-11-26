// Compile-time stand-ins for the hidden framework classes the root daemon calls (binder
// interfaces of system services and a few hidden types). The daemon compiles against them with
// compileOnly and never packages them: at runtime the framework's real classes answer. Every
// signature is copied from Android 17's sources (android-security-17.0.0_r3); one that differs
// from the device's framework fails with a LinkageError, which the daemon reports as a
// SystemServiceException.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.thorctl.hiddenapi"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
