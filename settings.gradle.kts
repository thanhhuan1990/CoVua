pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Thư viện quảng bá chéo XPromo: https://github.com/thanhhuan1990/xpromo-android
        maven("https://jitpack.io") { content { includeGroup("com.github.thanhhuan1990") } }
    }
}

// Sửa XPromo cùng lúc với app: chạy với -PxpromoLocal=true để dùng source ../xpromo-android thay bản JitPack.
if (providers.gradleProperty("xpromoLocal").orNull == "true" && file("../xpromo-android").exists()) {
    includeBuild("../xpromo-android") {
        dependencySubstitution {
            substitute(module("com.github.thanhhuan1990:xpromo-android")).using(project(":xpromo"))
        }
    }
}

rootProject.name = "CoVua"
include(":app", ":engine", ":training-core", ":tools")
