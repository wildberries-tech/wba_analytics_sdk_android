pluginManagement {

    repositories {
        maven("https://nexus.wildberries.ru/repository/android-releases/") {
            content {
                includeGroupByRegex("ru.wildanalytics.pub.*")
                includeGroupByRegex("convention.*")
            }
        }
        maven("https://nexus-proxy.wb.ru/repository/maven-proxy/")
        maven("https://nexus-proxy.wb.ru/repository/maven-proxy-gradle-plugins/")
        maven("https://nexus-proxy.wb.ru/repository/maven-proxy-google/")
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://nexus.wildberries.ru/repository/android-releases/") {
            content {
                includeGroupByRegex("ru.wildanalytics.pub.*")
                includeGroupByRegex("convention.*")
            }
        }
        maven("https://nexus-proxy.wb.ru/repository/maven-proxy/")
        maven("https://nexus-proxy.wb.ru/repository/maven-proxy-gradle-plugins/")
        maven("https://nexus-proxy.wb.ru/repository/maven-proxy-google/")
        google()
        mavenCentral()
    }
}

rootProject.name = "wildanalyticsandroid"
include("library")
include("demoapp")
