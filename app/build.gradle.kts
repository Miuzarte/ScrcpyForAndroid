import java.net.URI
import java.security.MessageDigest
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
        versionCode = 30
        versionName = "0.3.4"

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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
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

val scrcpyServerAssetDir = "${project.projectDir}/src/main/assets/bin"
val scrcpyServerAssetFile = "$scrcpyServerAssetDir/scrcpy-server-v4.0"
val scrcpyServerDownloadUrl = "https://github.com/Genymobile/scrcpy/releases/download/v4.0/scrcpy-server-v4.0"
val scrcpyServerSha256 = "84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a"

val downloadScrcpyServer by tasks.registering {
    description = "Download scrcpy-server binary from GitHub releases if absent or SHA256 mismatch"
    group = "build setup"

    inputs.property("downloadUrl", scrcpyServerDownloadUrl)
    inputs.property("expectedSha256", scrcpyServerSha256)
    outputs.file(scrcpyServerAssetFile)

    doLast {
        val file = outputs.files.singleFile
        val url = inputs.properties["downloadUrl"] as String
        val expectedSha = inputs.properties["expectedSha256"] as String
        val dir = file.parentFile

        if (!dir.exists()) dir.mkdirs()

        fun computeSha256(f: File): String {
            return f.inputStream().use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } >= 0) {
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }

        val needsDownload = !file.exists() || computeSha256(file) != expectedSha

        if (needsDownload) {
            logger.lifecycle("Downloading scrcpy-server-v4.0 from GitHub releases...")
            try {
                URI(url).toURL().openStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                throw GradleException(
                    "Failed to download scrcpy-server-v4.0 from GitHub releases.\n" +
                    "  URL: $url\n" +
                    "  You may download it manually and place it at: ${file.absolutePath}\n" +
                    "  If you are behind a proxy, check your Gradle proxy settings\n" +
                    "  (gradle.properties: systemProp.https.proxyHost / systemProp.https.proxyPort).",
                    e
                )
            }

            val actualSha = computeSha256(file)
            require(actualSha == expectedSha) {
                "SHA256 mismatch for scrcpy-server-v4.0!\n" +
                "  Expected: $expectedSha\n" +
                "  Got:      $actualSha\n" +
                "  Delete ${file.absolutePath} to retry download."
            }
            logger.lifecycle("scrcpy-server-v4.0 downloaded and verified.")
        } else {
            logger.lifecycle("scrcpy-server-v4.0 exists with correct SHA256, skip download.")
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadScrcpyServer)
}
