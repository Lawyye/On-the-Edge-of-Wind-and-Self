pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "NutritionTracker"

include(":core")
include(":remote")

// Модуль приложения требует Google Maven и Android SDK. Там, где их нет
// (например, в изолированной среде разработки), бизнес-логику можно собрать и
// протестировать отдельно: NT_JVM_ONLY=1 ./gradlew :core:test :remote:test
if (System.getenv("NT_JVM_ONLY") != "1") {
    include(":app")
}
