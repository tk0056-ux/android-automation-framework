plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.dandantang.autoai"
    compileSdk = 35
    ndkVersion = "25.1.8937393"

    defaultConfig {
        applicationId = "com.dandantang.autoai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
            abiFilters += "armeabi-v7a"
        }

        // C++ 编译参数配置
        externalNativeBuild {
            cmake {
                abiFilters("arm64-v8a", "armeabi-v7a")
                // 关键：明确告诉编译器我们需要异常处理和运行时类型检查
                cppFlags("-std=c++11", "-frtti", "-fexceptions")
                // 关键：强制指定 STL 类型
                arguments("-DANDROID_STL=c++_static")
            }
        }
    }

    // 指定 CMakeLists 的路径
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // 网络与 OCR 库
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

}