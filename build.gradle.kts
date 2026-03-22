import org.gradle.kotlin.dsl.support.kotlinCompilerOptions

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

// Project-wide versions
ext {
    set("kotlin_version", "1.9.20")
    set("android_api_version", 34)
    set("android_min_api_version", 26)
    set("mupdf_version", "1.24.2")
}
