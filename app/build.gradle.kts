import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Firebase là tùy chọn: chỉ áp dụng plugin khi đã có app/google-services.json
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val keystoreProperties = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile")?.isNotBlank() == true

val adsProperties = Properties().apply {
    val f = rootProject.file("ads.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun adProp(key: String, fallback: String): String =
    adsProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

// Google test IDs
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testBanner = "ca-app-pub-3940256099942544/9214589741"
val testInterstitial = "ca-app-pub-3940256099942544/1033173712"
val testRewarded = "ca-app-pub-3940256099942544/5224354917"
val testNative = "ca-app-pub-3940256099942544/2247696110"
val testAppOpen = "ca-app-pub-3940256099942544/9257395921"

android {
    namespace = "com.huanhuynh.covua"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.huanhuynh.covua"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        // ABI có Stockfish (engine-native/build_stockfish.sh); thiếu file thì app dùng engine dự phòng thuần Kotlin
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    // Stockfish là file thực thi đặt tên libstockfish.so trong jniLibs: phải được giải nén ra nativeLibraryDir để chạy được
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "AD_BANNER", "\"$testBanner\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\"$testInterstitial\"")
            buildConfigField("String", "AD_REWARDED", "\"$testRewarded\"")
            buildConfigField("String", "AD_NATIVE", "\"$testNative\"")
            buildConfigField("String", "AD_APP_OPEN", "\"$testAppOpen\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            manifestPlaceholders["admobAppId"] = adProp("admob.appId", testAppId)
            buildConfigField("String", "AD_BANNER", "\"${adProp("admob.banner", testBanner)}\"")
            buildConfigField("String", "AD_INTERSTITIAL", "\"${adProp("admob.interstitial", testInterstitial)}\"")
            buildConfigField("String", "AD_REWARDED", "\"${adProp("admob.rewarded", testRewarded)}\"")
            buildConfigField("String", "AD_NATIVE", "\"${adProp("admob.native", testNative)}\"")
            buildConfigField("String", "AD_APP_OPEN", "\"${adProp("admob.appOpen", testAppOpen)}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room: schema JSON được commit vào app/schemas để kiểm tra migration.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":training-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat) // ngôn ngữ trong app (vi/en/zh/ja)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.xpromo)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx) // AdMob dùng WorkManager; giữ phiên bản đồng bộ
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.play.services.ads)
    implementation(libs.ump)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.play.review.ktx)

    testImplementation(libs.junit)
}

// ---- Phát hành lên Google Play (Gradle Play Publisher) ----
// Key service account để NGOÀI repo: ~/.config/play/huanhuynh-play.json (hoặc biến môi trường PLAY_CREDENTIALS).
// Không có key → bỏ qua, build bình thường. Lệnh: ./gradlew publishReleaseBundle  (track internal, trạng thái nháp)
// Release note: src/main/play/release-notes/vi-VN/default.txt (≤ 500 ký tự).
val playCredentials = file(System.getenv("PLAY_CREDENTIALS") ?: "${System.getProperty("user.home")}/.config/play/huanhuynh-play.json")
if (playCredentials.exists()) {
    apply(plugin = "com.github.triplet.play")
    extensions.configure<com.github.triplet.gradle.play.PlayPublisherExtension>("play") {
        serviceAccountCredentials.set(playCredentials)
        track.set("internal")
        releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.DRAFT)
        defaultToAppBundles.set(true)
    }
}
