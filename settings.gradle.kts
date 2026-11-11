pluginManagement {
    repositories {
        google()
        // Espelho do Maven Central mantido pelo Google (mais tolerante a limites de taxa).
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "Calories"

include(":app")
include(":data")
include(":domain")
include(":library-format")
include(":tools:food-importer")
