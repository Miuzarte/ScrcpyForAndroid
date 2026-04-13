plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    id("kotlin-parcelize")
}

val defaultAbiList = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
val configuredAbiList = (project.findProperty("abiList") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.ifEmpty { null }
    ?: defaultAbiList

android {
    namespace = "io.github.miuzarte.scrcpyforandroid"
    compileSdk = 37

    signingConfigs {
        create("release") {
            val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
            val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
            val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
            val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")

            if (!releaseStoreFile.isNullOrBlank()) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.miuzarte.scrcpyforandroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.1.4"

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=none")
            }
        }

        ndk {
            abiFilters.clear()
            //noinspection ChromeOsAbiSupport
            abiFilters += configuredAbiList
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*configuredAbiList.toTypedArray())
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        prefab = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }

    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.pip)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.material)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.navigation3.ui)
    implementation("io.github.vvb2060.ndk:boringssl:20250114")
    implementation("org.lsposed.libcxx:libcxx:27.0.12077973")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
    implementation("org.conscrypt:conscrypt-android:2.5.2")
    implementation("sh.calvin.reorderable:reorderable:3.0.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation(libs.androidx.compose.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
