plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pdfreader.core.pdfengine"
    compileSdk = libs.versions.android.api.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.api.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fPIC")
                arguments("-DANDROID_STL=c++_shared")
            }
        }

        ndk {
            debugSymbolLevel = "full"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.coroutines)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
}
