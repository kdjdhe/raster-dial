plugins {
    id("com.android.application")
}

android {
    namespace = "com.raster.dial"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.raster.dial"
        minSdk = 28
        targetSdk = 34
        versionCode = 3
        versionName = "0.1.2"
        buildConfigField("String", "UPDATE_MANIFEST_PRIMARY",
            "\"https://updates.example.com/raster-dial/update.json\"")
        buildConfigField("String", "UPDATE_MANIFEST_FALLBACK",
            "\"https://raw.githubusercontent.com/kdjdhe/raster-dial/main/update.json\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
