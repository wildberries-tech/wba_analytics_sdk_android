plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
    id("maven-publish")
}

dependencies {
    // PUBLIC API
    api(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)
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
    namespace = "ru.wildberries.WBAnalytics2"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["moduleName"] = name
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
        freeCompilerArgs = freeCompilerArgs + "-Xexplicit-api=strict"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets["androidTest"].assets.srcDir(schemaDir)

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

ksp {
    arg("room.schemaLocation", schemaDir.toString())
    arg("room.incremental", "true")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "ru.wildberries"
            artifactId = "analytics2.public"
            version = "1.0.23"

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