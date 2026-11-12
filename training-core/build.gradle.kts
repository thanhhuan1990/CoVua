// Logic huấn luyện thuần (Leitner, lịch tập, chọn bài, gợi ý cấp độ...): không phụ thuộc Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":engine"))
    testImplementation(libs.junit)
}
