plugins {
    id("com.android.application")
}

android {
    namespace = "com.xiaomi.thermaleditor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiaomi.thermaleditor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}

