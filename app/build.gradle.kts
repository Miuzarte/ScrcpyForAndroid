import java.util.Properties

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
val buildUniversalApk = configuredAbiList.size > 1
val singleAbi = configuredAbiList.singleOrNull()

android {
    namespace = "io.github.miuzarte.scrcpyforandroid"
    compileSdk = 37

    signingConfigs {
        create("release") {
            val envFile = rootProject.file(".env")
            val envProps = Properties()
            if (envFile.exists())
                envFile.inputStream().use { envProps.load(it) }

            fun getValue(key: String): String? {
                var value = (
                        envProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
                            ?: System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    ?.trim('"', '\'')
                if (value != null && value.startsWith("~"))
                    value = System.getProperty("user.home") + value.substring(1)
                return value
            }

            val releaseStoreFile = getValue("RELEASE_STORE_FILE")
            if (!releaseStoreFile.isNullOrBlank()) {
                storeFile = file(releaseStoreFile)
                storePassword = getValue("RELEASE_STORE_PASSWORD")
                keyAlias = getValue("RELEASE_KEY_ALIAS")
                keyPassword = getValue("RELEASE_KEY_PASSWORD")
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
        targetSdk = 37
        versionCode = 23
        versionName = "0.2.7"

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
            isEnable = buildUniversalApk
            reset()
            include(*configuredAbiList.toTypedArray())
            isUniversalApk = buildUniversalApk
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

    buildToolsVersion = "37.0.0"
    ndkVersion = "29.0.14206865"
}

androidComponents {
    onVariants { variant ->
        singleAbi?.let { abi ->
            variant.outputs.forEach { output ->
                output.outputFileName.set("app-$abi-${variant.name}.apk")
            }
        }
    }
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
    implementation(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.material)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.navigation3.ui)
    implementation(libs.backdrop)
    implementation(libs.boringssl)
    implementation(libs.libcxx)
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.conscrypt.android)
    implementation(libs.reorderable)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation("com.github.promeg:tinypinyin:2.0.3")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
