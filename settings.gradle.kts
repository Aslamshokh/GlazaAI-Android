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
        // LiveKit тянет библиотеку переключения звука с JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GlazaAI"
include(":app")
// Приложение волонтёра «ИИ Глаз Помощь» (отдельный APK)
include(":volunteer")
