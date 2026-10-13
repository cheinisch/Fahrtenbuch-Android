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
        // Google nur nach eigenen Paketen fragen; alles andere (z. B. MapLibre) kommt
        // direkt von Maven Central, ohne vergebliche Anfragen an dl.google.com.
        google {
            content {
                // Nur Gruppen, die tatsächlich bei Google liegen. Gson, ZXing usw. tragen
                // zwar „com.google“ im Namen, kommen aber von Maven Central.
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android\\..*")
                includeGroupByRegex("com\\.google\\.android\\..*")
                includeGroupByRegex("com\\.google\\.firebase.*")
                includeGroupByRegex("com\\.google\\.testing\\..*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Fahrtenbuch"
include(":app")
