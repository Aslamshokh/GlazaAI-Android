plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aslamshoh.glazaai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aslamshoh.glazaai"
        // 26 (Android 8.0+) — поддерживает adaptive-иконки без растровых mipmap-фолбэков
        // и покрывает подавляющее большинство реальных устройств.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            // Debug-сборка подписывается автоматически сгенерированным отладочным ключом
            // Android (действует ~ 30 лет, в отличие от 7-дневной подписи бесплатного Apple ID
            // на iOS) — именно её мы собираем в CI и устанавливаем на телефон напрямую,
            // без какой-либо отдельной программы для подписи вроде Sideloadly.
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.4")

    // CameraX — съёмка кадра для Валюты/Текста/Предметов/Документов.
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ZXing (чистый Java/Kotlin, без зависимости от Google Play Services) — непрерывное
    // сканирование штрих-кодов и QR-кодов, как AVFoundation на iOS.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Сеть — тот же backend, что и у web/iOS версий (см. GlazaAI-iOS/README.md).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")


    // Перевод Текст ⇄ Перевод (RU⇄EN): модели скачиваются один раз, дальше работают офлайн.
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
