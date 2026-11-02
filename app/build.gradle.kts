plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wordstory.app"
    compileSdk {
        version = release(36)
    }
    // 本地只装了 35.0.0 / 36.x，dl.google.com 不可达，固定用已装的版本（同 hero 项目）
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.wordstory.app"
        minSdk = 26   // java.time
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            // 自己用，直接用 debug 签名，装手机不用再配 keystore
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // 测验队列挂在 Compose 快照状态上，跑单测时别被没桩的 android.jar 拦住
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
