// Корневой build.gradle.kts — версии плагинов объявлены здесь (apply false), сами плагины
// применяются в app/build.gradle.kts. Сборка выполняется в облаке (GitHub Actions), так как
// локально у вас нет Android SDK — см. README.md.
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Начиная с Kotlin 2.0 компилятор Compose вынесен в отдельный Gradle-плагин
    // (раньше настраивался через composeOptions.kotlinCompilerExtensionVersion).
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
