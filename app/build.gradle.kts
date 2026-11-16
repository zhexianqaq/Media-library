import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.zhexian.douyingallery"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zhexian.douyingallery"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // —— 签名 ——
    // 密钥与口令都不进仓库：优先级为 环境变量（CI/本地 export） > local.properties（本机、已 gitignore）
    // > app/douyingallery.jks 默认路径。三者都缺失时跳过签名，产出未签名 APK。
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }

    fun secret(envName: String, propKey: String): String? =
        System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }
            ?: localProps.getProperty(propKey)?.trim()?.takeIf { it.isNotEmpty() }

    val storeFileName = secret("ANDROID_KEYSTORE_PATH", "RELEASE_STORE_FILE") ?: "douyingallery.jks"
    val keystoreFile = file(storeFileName)
    val storePasswordValue = secret("ANDROID_KEYSTORE_PASSWORD", "RELEASE_STORE_PASSWORD")
    val keyAliasValue = secret("ANDROID_KEY_ALIAS", "RELEASE_KEY_ALIAS")
    val keyPasswordValue = secret("ANDROID_KEY_PASSWORD", "RELEASE_KEY_PASSWORD")

    signingConfigs {
        if (keystoreFile.exists() && storePasswordValue != null && keyAliasValue != null && keyPasswordValue != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.lifecycle(
                    "[douyin-gallery] 未找到签名密钥/口令，release 产出未签名 APK（仅用于 CI 冒烟构建）"
                )
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")

    lint {
        // 自用侧载 App，release 构建不做 lint 阻断（本地仍需手动跑 ./gradlew lint）
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST"
        )
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-gif:3.0.4")
    implementation("io.coil-kt.coil3:coil-video:3.0.4")
    implementation("me.saket.telephoto:zoomable-image-coil3:0.14.0")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.paging:paging-compose:3.3.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.biometric:biometric:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
