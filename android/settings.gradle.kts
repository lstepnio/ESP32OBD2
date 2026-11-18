pluginManagement {
    repositories {
        google()
        mavenCentral()
        // Keep Kotlin marker metadata on the checksum-pinned Maven Central origin.
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "eGauge"
include(":app", ":core:designsystem")
