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
        versionCode = 2
        versionName = "0.1.1"
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
