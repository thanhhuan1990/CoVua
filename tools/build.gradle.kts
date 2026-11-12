// Công cụ sinh nội dung (không đóng gói vào APK):
//   ./gradlew :tools:run --args="mine --games 20000 --minutes 60 --out build/mined.jsonl"
//   ./gradlew :tools:run --args="pack --in build/mined.jsonl --out ../app/src/main/assets/content --csv content/puzzles.csv"
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.huanhuynh.covua.tools.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx2g")
}

tasks.named<JavaExec>("run") { workingDir = projectDir }

dependencies {
    implementation(project(":engine"))
    testImplementation(libs.junit)
}
