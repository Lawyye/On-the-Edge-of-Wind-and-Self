// Android Gradle Plugin кладётся в общий classpath корневого проекта, чтобы
// он и Kotlin Gradle Plugin загружались одним загрузчиком классов. В режиме
// NT_JVM_ONLY=1 он не нужен и не скачивается.
buildscript {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral()
    }
    dependencies {
        if (System.getenv("NT_JVM_ONLY") != "1") {
            classpath(libs.android.gradle.plugin)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
