import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
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
        versionCode = 12
        versionName = "0.1.11"
        buildConfigField("String", "UPDATE_MANIFEST_PRIMARY",
            "\"https://update.litanghub.top:8443/raster-dial/update.json\"")
        buildConfigField("String", "UPDATE_MANIFEST_SECONDARY",
            "\"https://gitee.com/kdjdhe/raster-dial/raw/main/update.json\"")
        buildConfigField("String", "UPDATE_MANIFEST_FALLBACK",
            "\"https://raw.githubusercontent.com/kdjdhe/raster-dial/main/update.json\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}
