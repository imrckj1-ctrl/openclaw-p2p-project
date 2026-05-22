import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 自动递增版本号 —— 每次构建 versionCode +1，写入 version.properties
fun bumpVersionCode(): Int {
    val propsFile = rootProject.file("version.properties")
    val props = Properties()
    propsFile.inputStream().use { props.load(it) }
    val code = props.getProperty("VERSION_CODE", "1").toInt() + 1
    val major = props.getProperty("VERSION_MAJOR", "0")
    val minor = props.getProperty("VERSION_MINOR", "1")
    val patch = props.getProperty("VERSION_PATCH", "0")
    props.setProperty("VERSION_CODE", code.toString())
    props.setProperty("VERSION_MAJOR", major)
    props.setProperty("VERSION_MINOR", minor)
    props.setProperty("VERSION_PATCH", patch)
    propsFile.outputStream().use { props.store(it, "Auto-generated") }
    return code
}

val appVersionCode: Int = bumpVersionCode()
val appVersionName: String = run {
    val props = Properties()
    rootProject.file("version.properties").inputStream().use { props.load(it) }
    "${props.getProperty("VERSION_MAJOR", "0")}.${props.getProperty("VERSION_MINOR", "1")}.${props.getProperty("VERSION_PATCH", "0")}"
}

android {
    namespace = "com.imr.chat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.imr.chat"
        minSdk = 31
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 暴露版本号给 BuildConfig，UI 层直接引用
        buildConfigField("int", "VERSION_CODE", "$appVersionCode")
        buildConfigField("String", "VERSION_NAME", "\"$appVersionName\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "IMRChat-v${defaultConfig.versionName}-${variant.buildType.name}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room (SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // OkHttp (WebSocket)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.11.0")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore (settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
}
