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
        // TDLib prebuilt para Android (com.github.tdlibx:td) se publica vía JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ScannJox3"
include(":app")
