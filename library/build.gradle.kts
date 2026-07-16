plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
    id("maven-publish")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("detekt-baseline.xml")
    parallel = true
}

dependencies {
    detektPlugins(libs.detekt.formatting)

    // PUBLIC API
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.coroutines.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    coreLibraryDesugaring(libs.android.desugaring)

    implementation(libs.androidx.startup)
    implementation(libs.lifecycle.process)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.runner.junit5)
}

val schemaDir = File(projectDir, "schemas")

android {
    namespace = "ru.wildanalytics.pub.WildAnalytics"
    compileSdk = 35

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["moduleName"] = name
        consumerProguardFiles("$rootDir/consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    // Publishing only release variant of library
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xexplicit-api=strict")
    }
}

// В AGP 9 индексный доступ android.sourceSets["androidTest"] больше не работает,
// поэтому каталог Room-схем регистрируется как assets через Sources API на androidComponents.
androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory(schemaDir.absolutePath)
    }
}

ksp {
    arg("room.schemaLocation", schemaDir.toString())
    arg("room.incremental", "true")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "ru.wildanalytics"
            artifactId = "pub"
            version = System.getenv("wild.analytics.version") ?: "1.0.36"

            afterEvaluate {
                from(components["release"])
            }
        }
    }

    repositories {
        maven {
            name = "public"
            url = uri(layout.projectDirectory.dir("releases"))
        }
    }
}
