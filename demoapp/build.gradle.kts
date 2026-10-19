plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/config/detekt/detekt.yml", "config/detekt.yml")
    autoCorrect = true
    parallel = true
}

android {
    namespace = "ru.wildanalytics.pub.analytics.sdk.demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.wildanalytics.pub.analytics.sdk.demo"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        unitTests.isReturnDefaultValues = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(project(":library"))
    coreLibraryDesugaring(libs.android.desugaring)

    debugImplementation("com.github.chuckerteam.chucker:library:4.0.0")
    releaseImplementation("com.github.chuckerteam.chucker:library-no-op:4.0.0")

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    detektPlugins(libs.detekt.formatting)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}