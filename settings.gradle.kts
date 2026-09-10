@file:Suppress("UnstableApiUsage")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "Velqi-Luna"
include(":app")
include(":desktop")
include(":innertube")
include(":kugou")
include(":lrclib")
include(":selene")
include(":material-color-utilities")
include(":discord-rpc")
