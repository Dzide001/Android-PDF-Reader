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

rootProject.name = "pdf-reader"

// App modules
include(":app")

// Core feature modules
include(":core:pdf-engine")
include(":core:renderer")
include(":core:annotations")
include(":core:ocr")
include(":core:storage")
