pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ThorControl"

// One APK, two programs: the app and the root daemon (thorctld), which service.sh starts from the
// same APK with app_process. They share only :protocol, the models and the Binder contract.
include(":app")
include(":daemon")
include(":protocol")
