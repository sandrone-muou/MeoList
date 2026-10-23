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
    }
}

rootProject.name = "MeoList"

include(
    ":app",
    ":domain",
    ":data",
    ":core",
    ":notification",
    ":feature:tasks",
    ":feature:habits",
    ":feature:countdown",
    ":feature:tracker",
    ":feature:stats",
    ":feature:calendar",
    ":feature:farm",
    ":feature:settings",
)
