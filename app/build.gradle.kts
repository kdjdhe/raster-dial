plugins {
    id("com.android.application")
}

android {
    namespace = "com.raster.dial"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.raster.dial"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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
