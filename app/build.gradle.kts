plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.fortopes.tvlauncher"
    compileSdk = 36

    defaultConfig {
        // The Play Store ID. The code keeps its com.fortopes.tvlauncher package name, so the services'
        // component names are com.fortopes.home/com.fortopes.tvlauncher.<class>.
        applicationId = "com.fortopes.home"
        minSdk = 26
        targetSdk = 36
        // Every GitHub build gets a higher number, so a new APK always installs over the old one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.$versionCode"
    }

    signingConfigs {
        // A fixed key kept in the repo, so every build (yours or GitHub's) can update the installed app
        // without uninstalling it first. It's only for sideloading, never for the Play Store.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The Play Store upload key. It lives in GitHub secrets, never in the repo: the workflow decodes it to
        // a file and passes its path and password in, so only GitHub's builds can sign a Play bundle.
        val uploadKey = System.getenv("UPLOAD_KEYSTORE_FILE")
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey)
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = "upload"
                keyPassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8 shrinks and optimises the code. Compose runs much faster this way, which matters on slow TVs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.palette.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    // Installs the startup profile (src/main/baseline-prof.txt plus the libraries' own), so the app starts
    // compiled instead of interpreted, even right after an update.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.okhttp)
}
